# Mini-Netty 类描述文档

本文档描述当前 mini-netty 项目的核心类职责、模块边界和主要数据流。项目是一个基于 Java NIO 的 mini-netty demo，包含启动器、Channel/EventLoop、Pipeline/Handler、池化 ByteBuf、编解码、心跳和定时任务等核心组件。

---

## 总体架构

当前主链路采用固定大小池化 direct chunk + `ByteBufChain` 链式聚合模型：

```text
String / 业务对象
  -> Encoder / Coder
  -> ByteBufChain(4KB pooled ByteBuf chunks)
  -> ChannelOutboundBuffer
  -> SocketChannel.write(ByteBuffer[])
```

关键约束：

- 常规出站主路径使用 `ByteBufChain`。
- `StringEncoder` 通过 `CharsetEncoder` 直接编码到池化 chunk，不创建中间 `byte[]`。
- `ChannelOutboundBuffer` 对 `ByteBufChain` 使用 gathering write，不合并多 chunk。
- 裸 `ByteBuffer` 不作为业务出站入口；调用方应通过 coder/encoder 输出 `ByteBufChain` 或框架内 `ReferenceCounted`。
- `CompositeByteBuf` 保留为兼容/遗留视图层，不再作为 frame/header 的主路径组合类型。
- `SimpleByteArray` 已删除。

---

## Bootstrap 模块

### `Bootstrap`

客户端启动辅助类，负责创建客户端通道并建立远程连接。

主要职责：

- 配置 worker `EventLoopGroup`。
- 指定客户端 `SocketChannel` 类型。
- 配置业务 `ChannelInitializer`。
- 通过 `connect()` 创建通道、初始化 pipeline、注册到 EventLoop 并发起非阻塞连接。

### `ServerBootstrap`

服务端启动辅助类，负责创建服务端通道并绑定端口。

主要职责：

- 配置 boss 和 worker 两组 `EventLoopGroup`。
- 指定 `ServerSocketChannel` 类型。
- 配置 boss handler 和 child handler。
- 通过 `bind()` 创建服务端通道、注册 OP_ACCEPT 并监听端口。
- 内部 `ServerBootstrapAcceptor` 负责接收新连接、注入 child handler 并注册到 worker。

---

## Buffer 模块

### `ReferenceCounted`

引用计数契约。

主要职责：

- `refCnt()` 查询当前引用计数。
- `retain()` 增加引用。
- `release()` 释放引用，引用归零时触发资源释放或回池。

### `ByteBuf`

对 `ByteBuffer` 的封装，是底层 chunk 和 retained slice 的承载类型。

主要职责：

- 维护 `readIndex` / `writeIndex`。
- 支持 `readByte()`、`read(...)`、`skipBytes(...)`、`writeByte(...)`、`writeInt(...)`、`writeBytes(...)`。
- 支持 `nioBuffer()` 暴露当前可读区间视图。
- 支持 `writableNioBuffer()` 暴露当前可写区间视图。
- 支持 `advanceWriterIndex(int)` 配合 encoder 直接写入 NIO view。
- 支持 `retainedSlice(int)` 创建共享 root 引用计数的零拷贝切片。
- root `ByteBuf` 引用归零后，如果来自 `PooledByteBufAllocator`，则回到 allocator；非池化 root 才释放底层 direct memory。

### `ByteBufChain`

固定 chunk 链式缓冲容器，是当前入站/出站主数据结构。

主要职责：

- 使用 `LinkedList<ByteBuf>` 管理多个池化 chunk。
- 维护 cached `readableBytes`，避免每次遍历链表。
- 支持跨 chunk `readByte()`、`read(...)`、`skipBytes(...)`。
- 支持跨 chunk `writeByte(...)`、`writeInt(...)`、`writeBytes(...)`。
- 支持 `nioBuffers(int maxCount)` 返回多段可读 `ByteBuffer` 视图，用于 gathering write。
- 支持 `writableNioBuffer()` / `advanceWriterIndex(int)`，供 `CharsetEncoder` 等直接写入池化 direct chunk。
- 支持 `readRetainedFrame(int)` 返回新的 `ByteBufChain` retained slice 视图，不复制 payload。
- 支持 `append(ByteBuf)` 和 `appendChain(ByteBufChain)` 转移组件所有权。
- `release()` 归零时级联释放链内所有 `ByteBuf`。

### `CompositeByteBuf`

多段 `ReferenceCounted` 的零拷贝组合视图，当前作为兼容/遗留层保留。

主要职责：

- 使用组件列表保存 `ByteBuf`、`ByteBufChain` 或嵌套 `CompositeByteBuf`。
- 不复制底层字节，只按顺序读取各 component。
- 支持 `readByte()`、`read(...)`、`skipBytes(...)`。
- 支持 `nioBuffers()` 收集多段 NIO view，用于 gathering write。
- 自身 `release()` 归零时逐个释放 component。

### `PooledByteBufAllocator`

固定大小 chunk 的池化分配器。

主要职责：

- 默认 chunk 大小为 `4 * 1024`。
- 维护 direct / heap 两类 `ByteBuf` 池。
- `allocate(boolean)` 分配默认大小 chunk。
- `recycle(ByteBuf)` 接收引用归零的池化 root，默认大小 chunk 回池，非默认容量或池满时释放。
- 回池对象复用前保持不可访问状态，重新分配时重置读写索引、引用计数和生命周期代次。
- `close()` 释放池内剩余缓冲区。

---

## Channel 模块

### `Channel`

通道核心接口。

主要职责：

- 定义 `bind`、`connect`、`close`。
- 定义 Selector 注册/注销操作。
- 暴露 `SelectionKey`、`EventLoop`、`ChannelPipeline`、`ChannelOutboundBuffer`。
- 查询通道状态：`isOpen()`、`isActive()`、`isRegistered()`。

### `ServerChannel`

服务端通道接口，扩展 `Channel` 并增加 `accept()`。

### `SocketChannel`

客户端/连接通道接口。

主要职责：

- 获取远端和本地地址。
- 支持 `write(ByteBuffer)` 单段写。
- 支持 `write(ByteBuffer[])` gathering write。
- 支持 `finishConnect()` 完成非阻塞连接。

### `ChannelHandler`

handler 根接口，定义入站和出站事件方法。

主要职责：

- 入站：`channelRegistered`、`channelActive`、`channelInactive`、`channelRead`、`userEventTriggered`。
- 出站：`write`、`flush`。

### `ChannelInboundHandler` / `ChannelOutboundHandler`

分别表示入站和出站处理器接口，用于区分事件传播方向。

### `ChannelHandlerContext`

pipeline 节点上下文。

主要职责：

- 获取 `channel()`、`executor()`、`pipeline()`、`handler()`。
- 向后传播入站事件。
- 向前传播出站请求。

### `AbstractChannelHandlerContext`

上下文抽象基类，维护双向链表节点并实现入站/出站事件传播。

### `DefaultChannelHandlerContext`

默认上下文实现，持有关联 handler。

### `ChannelPipeline`

handler 链接口。

主要职责：

- 支持 `addFirst`、`addLast`、`addAfter`、`addBefore`、`remove`。
- 入站事件从 Head 向 Tail 传播。
- 出站事件从 Tail 向 Head 传播。
- 支持通过类型或注解定位上下文。

### `DefaultChannelPipeline`

pipeline 默认实现。

主要职责：

- 维护 HeadContext / TailContext 哨兵节点。
- HeadContext 将最终出站写入转交 `ChannelOutboundBuffer`。
- TailContext 对未被业务消费的 `ReferenceCounted` 入站消息兜底 `release()`。

### `ChannelInitializer`

通道初始化器抽象类。

主要职责：

- 模板方法：`preInit` -> `initChannel` -> `postInit`。
- `initChannel` 由业务实现，用于装配 pipeline。
- 初始化完成后从 pipeline 中移除自身。

### `ServerChannelInitializer` / `ClientChannelInitializer`

框架提供的服务端/客户端初始化器。

主要职责：

- 注入 `IdleStateHandler`。
- 在 `FrameCodec` 标记的编解码器附近注入心跳 handler。

### `SimpleChannelInboundHandler`

简单入站 handler 基类，默认透传事件，业务只需重写关心的方法。

### `ChannelOutboundBuffer`

出站缓冲队列。

主要职责：

- 维护待写出的 `ReferenceCounted` 消息队列。
- 入队后接管消息最终 `release()` 责任。
- 支持 `ByteBufChain` 使用 `SocketChannel.write(ByteBuffer[])` gathering write。
- 短期兼容 `ByteBuf` 单段写和 `CompositeByteBuf` gathering write。
- 拒绝裸 `ByteBuffer` 出站，避免绕过 coder/encoder 和池化 chunk 模型。
- 保留 `byte[]` 兼容路径，但转换为固定 chunk `ByteBufChain`。
- 写完成功后释放消息并设置 promise success；失败或关闭时释放消息并设置 promise failure。
- 当 Socket 发送窗口满时注册 OP_WRITE，队列清空后注销 OP_WRITE。

### `FrameCodec`

编解码器标记注解，用于 initializer 或 pipeline 按位置注入 handler。

### `EventLoop`

事件循环接口，继承 `EventLoopGroup`。

主要职责：

- 查询是否在 EventLoop 线程。
- 暴露定时任务队列。
- 暴露绑定的 `PooledByteBufAllocator allocator()`，供编解码器和 handler 使用同一池化分配器。

### `EventLoopGroup`

事件循环组接口。

主要职责：

- 提交普通任务和定时任务。
- `next()` 获取下一个 EventLoop。
- 注册 channel 到 Selector。

### `DefaultChannelPromise`

异步结果默认实现。

主要职责：

- 记录 success/failure/done 状态。
- 支持 listener 回调。
- 支持 `sync()` 阻塞等待。
- 关联 `Channel`。

---

## NIO Channel 实现

### `NioServerSocketChannel`

NIO 服务端通道实现。

主要职责：

- 封装 `java.nio.channels.ServerSocketChannel`。
- 配置非阻塞模式。
- 实现绑定、注册、accept 和关闭。
- accept 后返回 `NioSocketChannel`。

### `NioSocketChannel`

NIO 连接通道实现。

主要职责：

- 封装 `java.nio.channels.SocketChannel`。
- 配置非阻塞模式。
- 支持非阻塞 connect / finishConnect。
- 维护 `ChannelOutboundBuffer`。
- 实现 `write(ByteBuffer)` 和 `write(ByteBuffer[])`。
- 关闭时触发 inactive、取消 SelectionKey 并关闭底层 channel。

### `NioEventLoop`

单线程 NIO 事件循环。

主要职责：

- 持有 `Selector`。
- 持有当前 EventLoop 绑定的 `PooledByteBufAllocator`。
- 执行普通任务和定时任务。
- 分发 OP_ACCEPT、OP_READ、OP_WRITE、OP_CONNECT。
- OP_READ 时读取到 `ByteBufChain` 并触发 `fireChannelRead`。
- OP_WRITE 时调用 `ChannelOutboundBuffer.flush()`。

### `NioEventLoopGroup`

NIO 事件循环组，维护多个 `NioEventLoop` 并通过轮询分配 channel。

---

## Handler 模块

### `LengthFieldBasedFrameDecoder`

基于长度字段的帧解码器，带 `@FrameCodec` 标记。

主要职责：

- 解析粘包/拆包。
- 从一个或多个 `ByteBufChain` 中读取长度字段。
- 完整 payload 通过 `ByteBufChain.readRetainedFrame()` 切出，不复制 payload。
- 输出 `ByteBufChain` frame 给下一个 handler。
- 释放已完全消费的入站链。

### `LengthFieldBasedFrameEncoder`

基于长度字段的帧编码器，带 `@FrameCodec` 标记。

主要职责：

- 使用 `ctx.executor().allocator()` 创建 `ByteBufChain`。
- 将长度字段直接写入 chain。
- 将 payload 追加/转移到 frame chain。
- 主路径输出 `ByteBufChain`，不再创建独立 header `ByteBuf` 或 `CompositeByteBuf`。
- 异常路径释放未成功传递的 `ReferenceCounted`。

### `StringDecoder`

字符串解码器。

主要职责：

- 支持直接消费 `ByteBufChain`、`ByteBuf`、`CompositeByteBuf`。
- 将当前可读内容转换为 UTF-8 `String`。
- 转换完成后释放原始 `ReferenceCounted`。
- 不再支持 `SimpleByteArray`。

### `StringEncoder`

字符串编码器。

主要职责：

- 使用 `CharsetEncoder` 将 `String` 直接编码到 `ByteBufChain.writableNioBuffer()`。
- 通过 `advanceWriterIndex(int)` 推进写索引。
- 使用当前 `EventLoop` 的 allocator 分配固定大小 chunk。
- 不调用 `String.getBytes()`，不创建中间 `byte[]`，不创建非默认容量 direct `ByteBuf`。

### `IdleStateHandler`

空闲检测 handler。

主要职责：

- 记录最后读时间。
- 使用 `HashedWheelTimer` 定时检测读空闲。
- 超时后触发 `READER_IDLE_STATE_EVENT`。
- channel inactive 时取消定时任务。

### `ClientHeartbeatHandler`

客户端心跳 handler。

主要职责：

- 协议头：`0x00` 业务数据、`0x01` Ping、`0x02` Pong。
- 读空闲时发送 Ping，Ping 使用 `ByteBufChain`。
- 收到 Pong 后释放消息。
- 收到业务帧时剥离头字节后透传。
- 出站业务消息前写入业务头，输出 `ByteBufChain`。
- 支持直接处理 `ByteBufChain`。

### `ServerHeartbeatHandler`

服务端心跳 handler。

主要职责：

- 收到 Ping 时回复 Pong，Pong 使用 `ByteBufChain`。
- 收到业务帧时剥离头字节后透传。
- 读空闲事件触发超时关闭。
- 出站业务消息前写入业务头，输出 `ByteBufChain`。
- 支持直接处理 `ByteBufChain`。

---

## Timer 模块

### `HashedWheelTimer`

时间轮定时器。

主要职责：

- 使用环形 bucket 管理定时任务。
- 后台 worker 周期推进 tick。
- 支持 `newTimeout(...)` 创建延迟任务。
- 支持取消任务。

---

## Util 模块

### `HeartbeatConstant`

心跳常量定义。

### `InterestOpsUtil`

SelectionKey 操作位格式化工具。

---

## Util Concurrent 模块

### `Future`

异步操作只读结果接口。

### `Promise`

可写异步承诺接口，继承 `Future`。

### `GenericFutureListener`

异步完成监听器。

### `ScheduleTask`

定时任务实体，实现 `Runnable` 和 `Comparable`。

---

## 测试覆盖

当前测试覆盖重点：

- `ByteBufChain` 默认 4KB chunk、跨 chunk 读写、skip、gathering view、retained frame。
- `PooledByteBufAllocator` 引用归零回池、非默认容量释放、旧生命周期句柄不可访问。
- `ChannelOutboundBuffer` 拒绝裸 `ByteBuffer` 出站。
- `StringEncoder` 直接编码到 `ByteBufChain`。
- `StringDecoder` 直接消费 `ByteBufChain`。
- 客户端/服务端心跳 handler 直接处理 `ByteBufChain`。

---

## 模块关系总结

```text
bootstrap/
  Bootstrap
  ServerBootstrap

buffer/
  ReferenceCounted
  ByteBuf
  ByteBufChain
  CompositeByteBuf
  PooledByteBufAllocator

channel/
  Channel
  ServerChannel
  ChannelHandler
  ChannelInboundHandler / ChannelOutboundHandler
  ChannelHandlerContext
  AbstractChannelHandlerContext
  DefaultChannelHandlerContext
  ChannelPipeline
  DefaultChannelPipeline
  ChannelInitializer
  ServerChannelInitializer / ClientChannelInitializer
  SimpleChannelInboundHandler
  ChannelOutboundBuffer
  FrameCodec
  EventLoop / EventLoopGroup
  DefaultChannelPromise
  socket/
    ServerSocketChannel / SocketChannel
    nio/
      NioServerSocketChannel
      NioSocketChannel
      NioEventLoop
      NioEventLoopGroup

handler/
  codec/
    LengthFieldBasedFrameDecoder
    LengthFieldBasedFrameEncoder
    StringDecoder
    StringEncoder
  timeout/
    IdleStateHandler
    ClientHeartbeatHandler
    ServerHeartbeatHandler

timer/
  HashedWheelTimer

util/
  HeartbeatConstant
  InterestOpsUtil
  concurrent/
    Future / Promise
    GenericFutureListener
    ScheduleTask
```
