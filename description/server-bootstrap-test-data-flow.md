# ServerBootstrapTest 数据全链路说明

本文基于 `ServerBootstrapTest` 和 `BootstrapTest` 描述一次客户端发送业务字符串、服务端接收、服务端回复 `ACK` 的完整数据链路。

重点说明：
- 入站数据如何存储
- 入站数据如何在 pipeline 中传递
- 引用计数如何控制释放
- 当前代码中内存池回流/释放的实际情况
- 服务端出站 `ACK` 经过哪些步骤写回客户端

---

## 1. 测试中的 Pipeline 配置

### 1.1 服务端测试代码

`ServerBootstrapTest` 中服务端 child channel 的业务配置如下：

```java
ch.pipeline()
        .addLast(new LengthFieldBasedFrameDecoder())
        .addLast(new LengthFieldBasedFrameEncoder())
        .addLast(new StringDecoder())
        .addLast(new StringEncoder())
        .addLast(new SimpleChannelInboundHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                String s = (String) msg;
                if(s != null && !s.isEmpty()){
                    System.out.println("这里是Server端接受到的消息：" + s);
                }
                ctx.pipeline().writeAndFlush("ACK");
                ctx.fireChannelRead(msg);
            }
        });
```

`ServerChannelInitializer` 还会额外插入框架级 handler：

```java
preInit:  addLast(new IdleStateHandler(...))
postInit: addAfter(FrameCodec.class, new ServerHeartbeatHandler())
```

因为 `addAfter(FrameCodec.class, ...)` 从尾部向前查找带 `@FrameCodec` 的 handler，最终会找到 `LengthFieldBasedFrameEncoder`，所以服务端最终 pipeline 顺序是：

```text
HeadContext
  -> IdleStateHandler
  -> LengthFieldBasedFrameDecoder
  -> LengthFieldBasedFrameEncoder
  -> ServerHeartbeatHandler
  -> StringDecoder
  -> StringEncoder
  -> ServerBootstrapTest 业务 Handler
  -> TailContext
```

入站事件从 `HeadContext` 向 `TailContext` 方向传播。

出站事件从 `TailContext` 向 `HeadContext` 方向传播。

---

## 2. 客户端发送的数据格式

`BootstrapTest` 中客户端定时发送：

```java
ctx.pipeline().writeAndFlush("你好，我是客户端，我是业务数据");
```

客户端出站也有同类 pipeline：

```text
String
  -> StringEncoder
  -> ClientHeartbeatHandler
  -> LengthFieldBasedFrameEncoder
  -> ChannelOutboundBuffer
  -> SocketChannel.write(...)
```

因此服务端收到的 TCP 字节流逻辑格式是：

```text
[ length field ][ heartbeat/business type ][ utf-8 payload ]
```

默认长度字段为 4 字节，大端序。

业务数据帧中：

```text
heartbeat/business type = 0x00
payload = UTF-8 编码后的业务字符串
```

---

## 3. 服务端接收：从 Socket 到 ByteBufChain

服务端 worker 线程在 `NioEventLoop.selectAndDisPatch()` 中监听到 `OP_READ`：

```java
ByteBufChain msg = new ByteBufChain(true, allocator);
SocketChannel socketChannel = (SocketChannel) selectionKey.attachment();
int write = msg.write(socketChannel);
socketChannel.pipeline().fireChannelRead(msg);
```

这里的数据存储方式是：

```text
SocketChannel
  -> DirectByteBuffer
  -> ByteBuf
  -> ByteBufChain
```

### 3.1 ByteBufChain 的结构

`ByteBufChain` 内部维护：

```java
private LinkedList<ByteBuf> bufferChain;
```

每个 `ByteBuf` 包装一个 `ByteBuffer`：

```java
private final ByteBuffer byteBuffer;
private int writeIndex;
private int readIndex;
```

读取 socket 时：

```java
buf.writeFromChannel(socketChannel);
```

底层会把 socket 数据直接写入 direct `ByteBuffer`：

```text
kernel socket buffer
  -> java.nio.DirectByteBuffer
  -> ByteBuf.writeIndex 推进
```

这里不创建业务 payload 的 `byte[]`。

---

## 4. 入站解码：LengthFieldBasedFrameDecoder

`LengthFieldBasedFrameDecoder.channelRead()` 接收到的是 `ByteBufChain`。

它做两件事：

1. 读取长度字段
2. 根据长度字段切出完整 frame

### 4.1 长度字段读取

长度字段是协议元数据，当前实现允许复制少量字节到 `byte[] lengthFieldBytes`：

```java
readBytesFromChains(lengthFieldBytes, 0, lengthFieldLength);
lengthField = bytesToInt(lengthFieldBytes);
```

这一步只复制 1 到 4 字节，不复制业务 payload。

### 4.2 Payload 零拷贝取帧

业务 frame 使用：

```java
ReferenceCounted frame = readFrameFromChains(lengthField);
ctx.fireChannelRead(frame);
```

`readFrameFromChains()` 内部调用：

```java
frame.addComponent(chain.readRetainedFrame(read));
```

`ByteBufChain.readRetainedFrame()` 使用 `ByteBuf.retainedSlice()`：

```java
composite.addComponent(buf.retainedSlice(sliceLength));
buf.skipBytes(sliceLength);
```

此时 frame 的存储结构可能是：

```text
CompositeByteBuf
  -> ByteBuf slice 1
  -> ByteBuf slice 2
  -> ...
```

如果一个 frame 跨多个 socket read chunk，不会把多个 chunk 合并成 `byte[]`，而是组合成 `CompositeByteBuf`。

---

## 5. 引用计数如何保护入站 Frame

`ByteBuf.retainedSlice(length)` 的关键逻辑是：

```java
retain();
return new ByteBuf(root, duplicate.slice(), 0, length);
```

含义：

```text
原始 ByteBuf root refCnt + 1
slice 与 root 共享同一个 refCnt
slice 只是 ByteBuffer 视图，不复制字节
```

所以即使 `ByteBufChain` 消费完原始 chunk 并调用：

```java
bufferChain.removeFirst().release();
```

只要下游 frame slice 还没有释放，底层 direct memory 就不会被真正释放。

引用计数变化示例：

```text
1. ByteBuf 从 allocator 创建: refCnt = 1
2. Decoder retainedSlice:   refCnt = 2
3. ByteBufChain 消费完成:   release -> refCnt = 1
4. 下游 StringDecoder 消费: release -> refCnt = 0
5. refCnt 归零:             释放底层 direct memory
```

---

## 6. ServerHeartbeatHandler：剥离业务协议头

解码器输出的 frame 继续向后传播到 `ServerHeartbeatHandler`。

业务帧的第一个字节是协议类型：

```text
0x00 = 业务数据
0x01 = Ping
0x02 = Pong
```

服务端入站逻辑：

```java
byte frameType = readByte(frame);
```

这一步不是复制，而是推进 `readerIndex`：

```text
Before:
[ 0x00 ][ payload bytes... ]
   ^ readerIndex

After readByte:
[ 0x00 ][ payload bytes... ]
          ^ readerIndex
```

如果是业务帧：

```java
ctx.fireChannelRead(frame);
```

同一个 `ReferenceCounted` frame 继续传给后面的 `StringDecoder`。

如果是 Ping 控制帧：

```java
ctx.writeAndFlush(singleByteBuf(2), new DefaultChannelPromise());
frame.release();
```

Ping 被当前 handler 消费，不再向后传播，所以必须释放引用。

---

## 7. StringDecoder：允许的类型转换边界

`StringDecoder` 是明确允许从二进制转换为 Java 字符串的地方。

它接收 `ByteBuf` 或 `CompositeByteBuf`：

```java
ReferenceCounted buffer = (ReferenceCounted) msg;
```

然后读取剩余可读字节：

```java
byte[] bytes = new byte[length];
buffer.read(bytes, 0, length);
ctx.fireChannelRead(new String(bytes, StandardCharsets.UTF_8));
```

这里会发生一次必要复制：

```text
Direct ByteBuf / CompositeByteBuf
  -> byte[]
  -> String
```

转换完成后，原始 `ByteBuf/CompositeByteBuf` 不再向后传播，所以 `finally` 中释放：

```java
buffer.release();
```

释放会级联：

```text
CompositeByteBuf.release()
  -> component.release()
  -> ByteBuf root refCnt - 1
  -> refCnt == 0 时释放 direct memory
```

---

## 8. 业务 Handler 接收 String

`ServerBootstrapTest` 的业务 handler 接收到的是 `String`：

```java
String s = (String) msg;
System.out.println("这里是Server端接受到的消息：" + s);
```

此时二进制 frame 已经被 `StringDecoder` 消费并释放。

业务 handler 随后写出响应：

```java
ctx.pipeline().writeAndFlush("ACK");
ctx.fireChannelRead(msg);
```

`ctx.fireChannelRead(msg)` 会把 `String` 继续传到 `TailContext`。

因为 `String` 不是 `ReferenceCounted`，`TailContext` 不需要释放它，等待 JVM GC 即可。

---

## 9. 出站 ACK：从 String 到 Direct ByteBuf

服务端调用：

```java
ctx.pipeline().writeAndFlush("ACK");
```

出站传播方向是从 tail 向 head：

```text
业务 Handler
  -> StringEncoder
  -> StringDecoder
  -> ServerHeartbeatHandler
  -> LengthFieldBasedFrameEncoder
  -> LengthFieldBasedFrameDecoder
  -> IdleStateHandler
  -> HeadContext
  -> ChannelOutboundBuffer
```

### 9.1 StringEncoder

`StringEncoder` 把 `ACK` 转换为 UTF-8 字节，再写入 direct `ByteBuf`：

```java
byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
buf.writeBytes(bytes);
ctx.write(buf, promise);
```

这是允许的类型转换边界。

转换后出站链路重新回到 direct `ByteBuf`。

---

## 10. ServerHeartbeatHandler：添加业务协议头

服务端出站经过 `ServerHeartbeatHandler.write()`：

```java
return ctx.write(withHeader(0, msg), promise);
```

`withHeader()` 构造：

```java
new CompositeByteBuf()
    .addComponent(singleByteBuf(0))
    .addComponent(toReferenceCounted(payload));
```

此时出站数据结构是：

```text
CompositeByteBuf
  -> ByteBuf(header = 0x00)
  -> ByteBuf(payload = "ACK" UTF-8 bytes)
```

这里不会创建 `[header + payload]` 的新数组。

---

## 11. LengthFieldBasedFrameEncoder：添加长度字段

接着进入 `LengthFieldBasedFrameEncoder.write()`。

它计算 payload 长度：

```java
int length = readableBytes(payload);
```

然后创建长度字段 header：

```java
ByteBuf header = new ByteBuf(ByteBuffer.allocateDirect(lengthFieldLength));
writeLength(header, length);
```

最后组合成完整 frame：

```java
frame = new CompositeByteBuf()
        .addComponent(header)
        .addComponent(payload);
ctx.write(frame, promise);
```

最终出站数据结构是：

```text
CompositeByteBuf(frame)
  -> ByteBuf(length header)
  -> CompositeByteBuf(heartbeat frame)
       -> ByteBuf(type header = 0x00)
       -> ByteBuf(payload = "ACK")
```

逻辑字节流是：

```text
[ length ][ 0x00 ][ A ][ C ][ K ]
```

但内存上仍然是多个 direct `ByteBuf` 组合，不合并复制 payload。

---

## 12. HeadContext：写入 ChannelOutboundBuffer

出站事件最终到达 `HeadContext.write()`：

```java
channel().channelOutboundBuffer().writeToBuffer(msg, promise);
```

`ChannelOutboundBuffer.writeToBuffer()`：

```java
byteBufSenders.addLast(new ByteBufSender(toReferenceCounted(msg), promise));
```

从这一刻开始：

```text
ChannelOutboundBuffer 接管该 ReferenceCounted 消息的最终释放责任
```

如果写成功：

```java
msg.release();
promise.setSuccess();
```

如果写失败或 channel 关闭：

```java
msg.release();
promise.setFailure(cause);
```

---

## 13. Flush：从 CompositeByteBuf 到 SocketChannel

`HeadContext.flush()` 调用：

```java
channel().channelOutboundBuffer().flush();
```

`ChannelOutboundBuffer.doWriteToChannel()` 取队头 `ByteBufSender`。

如果消息是 `ByteBuf`：

```java
write = socketChannel.write(byteBuf.nioBuffer());
byteBuf.skipBytes(write);
```

如果消息是 `CompositeByteBuf`：

```java
write = (int) socketChannel.write(composite.nioBuffers());
composite.skipBytes(write);
```

`CompositeByteBuf.nioBuffers()` 会收集所有 component 当前可读区间：

```text
CompositeByteBuf
  -> ByteBuffer(length header)
  -> ByteBuffer(type header)
  -> ByteBuffer(payload)
```

然后调用 NIO gathering write：

```java
java.nio.channels.SocketChannel.write(ByteBuffer[] srcs)
```

这一步避免了把多个 buffer 合并成一个大数组。

---

## 14. TCP 背压与 OP_WRITE

如果一次写没有写完：

```java
if(write == 0){
    return;
}
```

`ChannelOutboundBuffer.flush()` 会在队列未清空时注册 `OP_WRITE`：

```java
socketChannel.register(selector, SelectionKey.OP_WRITE);
```

下次 selector 监听到 `OP_WRITE`：

```java
socketChannel.channelOutboundBuffer().flush();
```

继续从上次 `readerIndex` 位置发送。

写完后：

```java
byteBufSenders.pollFirst().success();
socketChannel.unregister(SelectionKey.OP_WRITE);
```

---

## 15. 出站引用计数释放链路

以服务端回复 `ACK` 为例，出站对象的释放路径是：

```text
ChannelOutboundBuffer.ByteBufSender.success()
  -> frame.release()
     -> lengthHeader.release()
     -> heartbeatComposite.release()
        -> typeHeader.release()
        -> ackPayload.release()
  -> promise.setSuccess()
```

如果某个 `ByteBuf` 的 `refCnt` 归零：

```java
releaseNative(root.byteBuffer);
```

也就是说：

```text
最后一个引用释放
  -> 释放 direct memory
```

---

## 16. 关于“回流到内存池”的当前代码现状

当前项目里存在 `PooledByteBufAllocator`：

```java
private final Queue<ByteBuf> directBufferPool;
private final Queue<ByteBuf> heapBufferPool;
```

它提供 `recycle(ByteBuf buf)`，理论上可以把 `refCnt == 1` 且未释放的 `ByteBuf` reset 后放回池中：

```java
buf.reset();
pool.offer(buf);
```

但当前全链路零拷贝改造后的主路径中，`ByteBufChain` 消费完 chunk 时调用的是：

```java
buf.release();
```

`ByteBuf.release()` 在引用计数归零后会直接释放 direct memory：

```java
releaseNative(root.byteBuffer);
```

因此当前实际行为是：

```text
引用计数保护生命周期
  -> refCnt 归零
  -> 释放 direct memory
```

不是严格意义上的：

```text
refCnt 归零
  -> 回收到 PooledByteBufAllocator 池中
```

如果后续要实现真正的“引用计数归零后回流到内存池”，建议让 root `ByteBuf` 持有 allocator 回调：

```text
ByteBuf.release()
  -> refCnt == 0
  -> allocator.recycleRoot(root)
  -> 池未满: reset 后入池
  -> 池已满: releaseNative
```

当前文档按现有代码描述：

```text
入站/出站通过引用计数安全释放 direct memory；allocator 池化能力保留，但主链路暂未把 release 归零对象自动回收到池。
```

---

## 17. 服务端完整入站链路总结

```text
1. worker EventLoop 监听 OP_READ
2. 创建 ByteBufChain(true, allocator)
3. SocketChannel.read 写入 direct ByteBuf
4. pipeline.fireChannelRead(ByteBufChain)
5. LengthFieldBasedFrameDecoder 读取长度字段
6. LengthFieldBasedFrameDecoder 使用 readRetainedFrame 零拷贝切出 payload frame
7. ServerHeartbeatHandler readByte 剥离 0x00 业务协议头
8. StringDecoder 把 ByteBuf/CompositeByteBuf 转成 String
9. StringDecoder release 原始 ReferenceCounted frame
10. 业务 Handler 接收 String 并打印
```

---

## 18. 服务端完整出站链路总结

```text
1. 业务 Handler 调用 pipeline.writeAndFlush("ACK")
2. StringEncoder 把 String 转成 direct ByteBuf
3. ServerHeartbeatHandler 添加 0x00 业务协议头，形成 CompositeByteBuf
4. LengthFieldBasedFrameEncoder 添加 length header，形成更外层 CompositeByteBuf
5. HeadContext 把 CompositeByteBuf 放入 ChannelOutboundBuffer
6. ChannelOutboundBuffer.flush 执行写出
7. CompositeByteBuf.nioBuffers 生成 ByteBuffer[]
8. NioSocketChannel.write(ByteBuffer[]) 执行 gathering write
9. 写完后 ByteBufSender.success release 整个 CompositeByteBuf
10. Promise 标记成功
11. 如果写不完，注册 OP_WRITE，下次继续发送
```

---

## 19. 一句话总结

`ServerBootstrapTest` 的接收链路中，网络字节先进入 direct `ByteBufChain`，解码器通过 `retainedSlice` 和 `CompositeByteBuf` 把完整 frame 传给后续 handler，心跳 handler 通过推进读指针剥离协议头，只有 `StringDecoder` 在类型转换边界复制为 `String`。服务端回复 `ACK` 时，出站链路用 direct `ByteBuf` 和 `CompositeByteBuf` 组合协议头、长度头与 payload，最终通过 `SocketChannel.write(ByteBuffer[])` 聚集写出，并由 `ChannelOutboundBuffer` 在写完或失败时统一释放引用计数资源。
