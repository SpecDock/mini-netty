# TODO: 统一固定分片内存池与 ByteBufChain 出站零多余拷贝重构

## 目标

将当前内存模型重构为统一固定大小分片模型：

- 所有堆外内存块都由 `PooledByteBufAllocator` 分配固定大小 chunk。
- 业务层尽量统一使用 `ByteBufChain` 作为入站/出站数据容器。
- `ByteBuf` 逐步退化为内部 chunk/segment，不再作为主要业务层 buffer 暴露。
- 非默认容量 `ByteBuf` 不再单独申请堆外内存，避免频繁 `allocateDirect()` 和 direct memory cleaner 开销。
- 出站使用 `SocketChannel.write(ByteBuffer[])` gathering write，避免把多段数据合并成连续数组。
- 每个 chunk 使用引用计数，使用完成后 `release()`，引用计数归零后回流 allocator，由 allocator 判断回池或释放。

## 设计判断

统一固定分片是合理方向：

- 堆外内存申请和释放通常比跨 chunk 计算更昂贵。
- 固定分片能让所有 chunk 都可回池复用。
- 大消息天然拆分成多个 chunk，不需要申请大块 direct buffer。
- 出站可通过 gathering write 零合并拷贝写出。
- 入站和出站复用同一套内存生命周期模型。

## 核心模型

```text
PooledByteBufAllocator
  allocateChunk() -> ByteBuf

ByteBufChain
  Deque<ByteBuf> chunks
  cachedReadableBytes
  read/write indexes across chunks
  release() -> release all chunks

Outbound
  ByteBufChain.nioBuffers(maxCount)
  SocketChannel.write(ByteBuffer[])
```

## ByteBuf 定位调整

`ByteBuf` 不建议完全删除，因为底层仍然需要对象承载：

- 固定大小 `ByteBuffer`。
- 引用计数。
- 所属 allocator。
- 回池/释放生命周期。
- retained slice 的 root 管理。

但业务主链路应逐步避免直接使用裸 `ByteBuf`，而是统一使用 `ByteBufChain`。

## ByteBufChain 需要增强

### 1. 维护 cached readable bytes

当前 `ByteBufChain.length()` 每次遍历链表计算：

```java
for (ByteBuf buf : bufferChain) {
    sum += buf.readableBytes();
}
```

后续应改为维护字段，例如：

```java
private int readableBytes;
```

写入时增加，读取/skip 时减少，避免频繁 O(n) 扫链。

### 2. 支持出站 gathering write

新增：

```java
ByteBuffer[] nioBuffers(int maxCount);
```

要求：

- 返回每个可读 chunk 的 `ByteBuffer` 视图。
- 不合并字节。
- 限制单次返回数量，避免 ByteBuffer[] 过大。
- 写出后通过 `skipBytes(writtenBytes)` 推进读指针。

### 3. 支持直接写入尾部 chunk

新增通用能力，不加入协议/编码语义：

```java
ByteBuffer writableNioBuffer();
void advanceWriterIndex(int bytes);
```

用于 `StringEncoder` 直接通过 `CharsetEncoder` 编码到 direct chunk，避免 `String -> byte[] -> direct ByteBuf` 的中间拷贝。

### 4. 支持跨 chunk 写入基础类型

需要保证以下方法跨 chunk 正确：

- `writeByte(int value)`
- `writeInt(int value)`
- `writeBytes(byte[] src, int offset, int length)`
- `writeFromChannel(SocketChannel channel)`

### 5. 支持 retained frame / slice

保留或增强：

```java
ReferenceCounted readRetainedFrame(int length);
```

长期方向：返回 `ByteBufChain`，而不是 `CompositeByteBuf`。

## CompositeByteBuf 后续处理

当前 `CompositeByteBuf` 是多段 `ReferenceCounted` 的组合视图，适合：

- header + payload 组合。
- 多个 retained slice 的临时零拷贝组合。
- 出站临时 gathering write。

长期目标是用增强后的 `ByteBufChain` 替代它：

- `ByteBufChain` 支持追加 retained slice/chunk。
- `ByteBufChain` 支持 prepend 或直接写 header。
- `ByteBufChain.nioBuffers()` 覆盖 `CompositeByteBuf.nioBuffers()` 场景。

迁移策略：

1. 短期保留 `CompositeByteBuf`，避免一次性重构过大。
2. 先让 `ChannelOutboundBuffer` 支持 `ByteBufChain` 出站 gathering write。
3. 再逐步把 frame/header 组合逻辑迁移到 `ByteBufChain`。
4. 当 `CompositeByteBuf` 无调用方后再删除。

## PooledByteBufAllocator 重构方向

### 1. 固定 chunk 分配

保留固定大小：

```java
bufferSize = 4 * 1024;
```

原则：

- 默认只分配固定大小 chunk。
- 非默认容量不再作为常规路径。
- 大数据由多个 chunk 组成。

### 2. 每个 EventLoop 一个 allocator

`NioEventLoop` 当前已有：

```java
private final PooledByteBufAllocator allocator;
```

需要在 `EventLoop` 接口暴露：

```java
PooledByteBufAllocator allocator();
```

优点：

- 每个 EventLoop 绑定一个 allocator。
- 减少跨线程竞争。
- handler 可通过 `ctx.executor().allocator()` 获取内存池。

### 3. 回收策略

chunk release 归零后：

```text
ByteBuf.release()
  -> refCnt == 0
  -> PooledByteBufAllocator.recycle(root)
  -> 默认固定容量且池未满：mark recycled + offer pool
  -> 池满或不可池化：deallocate
```

## 出站零多余拷贝目标

### ByteBufChain 主路径

```text
ByteBufChain
  -> nioBuffers(maxCount)
  -> SocketChannel.write(ByteBuffer[])
  -> skipBytes(written)
```

不允许：

- 把 chain 合并为 `byte[]`。
- 把 chain 合并为单个 direct ByteBuf。
- 为 header/string 单独创建非默认容量 direct ByteBuf。

### StringEncoder

目标链路：

```text
String
  -> CharsetEncoder
  -> ByteBufChain writable direct chunk
  -> SocketChannel.write(ByteBuffer[])
```

避免：

```text
String -> byte[] -> direct ByteBuf
```

实现原则：

- `StringEncoder` 负责字符编码。
- `ByteBufChain` 只暴露通用 writable NIO view。
- 不给 `ByteBuf` 添加 `writeUtf8()` 这类协议/编码补丁方法。

### ByteBuffer 兼容路径

目标：

- 外部传入 direct `ByteBuffer` 时，不复制到 `byte[]`。
- 外部传入 heap `ByteBuffer` 时，不在框架层强制复制到 direct ByteBuf；底层 JDK/OS 是否复制不由框架控制。
- 不默认释放用户传入的外部 direct memory。

建议增加 non-owning wrapper：

```text
ByteBufferReference implements ReferenceCounted
```

语义：

- 只管理引用生命周期。
- 推进 position/read index。
- release 不清理底层 ByteBuffer。

## ChannelOutboundBuffer 重构方向

支持出站消息类型：

- `ByteBufChain`
- `ByteBufferReference`
- 短期兼容 `ByteBuf`
- 短期兼容 `CompositeByteBuf`

写出策略：

```text
ByteBufChain         -> SocketChannel.write(ByteBuffer[])
CompositeByteBuf    -> SocketChannel.write(ByteBuffer[])
ByteBuf             -> SocketChannel.write(ByteBuffer)
ByteBufferReference -> SocketChannel.write(ByteBuffer)
```

长期目标：

- 主路径只保留 `ByteBufChain`。
- `CompositeByteBuf` 和裸 `ByteBuf` 逐步退到内部或兼容层。

## SimpleByteArray 废弃与入站解码统一

`SimpleByteArray` 应废弃。

原因：

- 它只是 `byte[] + begin + end` 的简单切片包装。
- 它没有引用计数。
- 它不参与内存池。
- 它不能表达 direct memory。
- 它与 chunk 引用计数和 release 回池模型冲突。
- 它会让 `StringDecoder`、心跳处理器保留特殊分支，导致数据模型分裂。

需要处理的文件：

- `src/main/java/io/github/specdock/mininetty/buffer/SimpleByteArray.java`
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/StringDecoder.java`
- `src/main/java/io/github/specdock/mininetty/channel/handler/timeout/ClientHeartbeatHandler.java`
- `src/main/java/io/github/specdock/mininetty/channel/handler/timeout/ServerHeartbeatHandler.java`

当前风险：

- `StringDecoder` 对 `SimpleByteArray` 有特殊分支。
- `StringDecoder` 对 `ReferenceCounted` 的非 `ByteBuf` 输入默认强转为 `CompositeByteBuf`。
- 如果 `ByteBufChain` 直接进入 `StringDecoder`，会有 `ClassCastException` 风险。
- `ClientHeartbeatHandler` 和 `ServerHeartbeatHandler` 也有 `SimpleByteArray` 分支，且 helper 当前只区分 `ByteBuf` 和 `CompositeByteBuf`，不支持 `ByteBufChain`。

短期改造：

1. `StringDecoder` 移除 `SimpleByteArray` import 和分支。
2. `StringDecoder` 显式支持 `ByteBufChain`、`ByteBuf`、`CompositeByteBuf`。
3. `ClientHeartbeatHandler` 移除 `SimpleByteArray` import 和分支。
4. `ServerHeartbeatHandler` 移除 `SimpleByteArray` import 和分支。
5. 两个心跳 handler 的 `readableBytes`、`readByte` helper 显式支持 `ByteBufChain`。
6. 删除 `SimpleByteArray.java`。

短期 `StringDecoder` 可接受实现：

```java
ReferenceCounted buffer = (ReferenceCounted) msg;
try {
    int length = readableBytes(buffer);
    byte[] bytes = new byte[length];

    if (buffer instanceof ByteBufChain) {
        ((ByteBufChain) buffer).read(bytes, 0, length);
    } else if (buffer instanceof ByteBuf) {
        ((ByteBuf) buffer).read(bytes, 0, length);
    } else if (buffer instanceof CompositeByteBuf) {
        ((CompositeByteBuf) buffer).read(bytes, 0, length);
    } else {
        throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass().getName());
    }

    ctx.fireChannelRead(new String(bytes, StandardCharsets.UTF_8));
} finally {
    buffer.release();
}
```

长期优化：

- `StringDecoder` 不再先复制到 `byte[]`。
- `ByteBufChain` 暴露 `nioBuffers()`。
- `StringDecoder` 使用 `CharsetDecoder` 从多个 `ByteBuffer` 视图直接解码为 `String`。
- Java `String` 自身内部存储创建是必要类型转换，不算多余框架拷贝。

目标链路：

```text
ByteBufChain chunks
  -> nioBuffers()
  -> CharsetDecoder
  -> String
```

不再保留：

```text
SimpleByteArray
ByteBufChain -> CompositeByteBuf 强转
ByteBufChain -> byte[] -> SimpleByteArray -> String
```

## 实施顺序

1. `EventLoop` 增加 `allocator()`，`NioEventLoop` 返回已有 allocator。
2. `ByteBufChain` 增加 cached readable bytes。
3. `ByteBufChain` 增加 `nioBuffers(maxCount)`。
4. `ChannelOutboundBuffer` 支持 `ByteBufChain` gathering write。
5. `ByteBufChain` 增加 writable NIO view 和 writer index 推进能力。
6. `StringEncoder` 改为通过 `ctx.executor().allocator()` 创建 `ByteBufChain` 并直接编码到 chunk。
7. 增加 `ByteBufferReference`，移除 `ByteBuffer -> byte[] -> direct ByteBuf` 的兼容路径双重拷贝。
8. 将 frame/header 组合逻辑逐步从 `CompositeByteBuf` 迁移到 `ByteBufChain`。
9. 清理裸 `ByteBuf` 作为业务出站消息的使用点。
10. 移除 `StringDecoder`、`ClientHeartbeatHandler`、`ServerHeartbeatHandler` 中的 `SimpleByteArray` 分支。
11. 让 `StringDecoder` 和心跳 handler 显式支持 `ByteBufChain`。
12. 删除 `SimpleByteArray.java`。
13. 无调用方后考虑删除或弱化 `CompositeByteBuf`。

## 验收标准

- 出站 `ByteBufChain` 不发生合并拷贝。
- `StringEncoder` 不再创建中间 `byte[]`。
- `ByteBuffer` 出站不再走 `ByteBuffer -> byte[] -> direct ByteBuf`。
- `SimpleByteArray` 被删除或不再被主代码引用。
- `StringDecoder` 可直接处理 `ByteBufChain`。
- 心跳处理器可直接处理 `ByteBufChain`。
- 所有池化 chunk release 归零后回流 allocator。
- 非默认容量 direct ByteBuf 不再作为常规路径出现。
- `mvn test` 通过。
- 新增测试覆盖：
  - `ByteBufChain.nioBuffers()` gathering write 视图正确。
  - 跨 chunk `skipBytes/readByte/read/write` 正确。
  - `StringEncoder` 直接编码到 chain。
  - 外部 direct ByteBuffer 出站 release 后不被框架清理底层内存。
  - chunk release 后可回池复用。

## 注意事项

- 不要为了追求统一一次性删除 `CompositeByteBuf`，先迁移主路径。
- 不要让 `ByteBufChain.readableBytes()` 每次遍历链表。
- 不要让外部 direct `ByteBuffer` 被框架错误释放。
- 不要给 `ByteBuf` 增加协议相关方法，例如 `writeUtf8()`。
- 不要把多 chunk 合并成连续数组再写出。
