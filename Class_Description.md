# Mini-Netty 类描述文档

本文档详细描述 mini-netty 项目中每个类的职责和功能。

---

## Bootstrap 模块

### `Bootstrap`
**位置**: `io.github.specdock.mininetty.bootstrap.Bootstrap`

客户端启动辅助类，负责创建客户端通道并建立远程连接。

**主要职责**:
- 配置 `EventLoopGroup`（worker 线程组）
- 指定 `SocketChannel` 类型（NIO 通道）
- 配置业务 handler 到 channel 的 pipeline
- 通过 `connect()` 方法连接远程服务器
- 连接流程：通道实例化 → Pipeline 初始化 → Promise 创建 → EventLoop 注册 → 触发物理连接

---

### `ServerBootstrap`
**位置**: `io.github.specdock.mininetty.bootstrap.ServerBootstrap`

服务端启动辅助类，负责创建服务端通道并绑定端口。

**主要职责**:
- 配置 boss 和 workers 两个 `EventLoopGroup`
- 指定 `ServerSocketChannel` 类型
- 配置 handler（boss 组）和 childHandler（workers 组新连接）
- 通过 `bind()` 方法绑定端口
- 内部包含 `ServerBootstrapAcceptor` 内部类，负责将新连接的 childHandler 注入 pipeline 并注册到 workers

**ServerBootstrapAcceptor**: 内部类，实现了 `ChannelInboundHandler`。当 boss 监听到新连接（OP_ACCEPT）时，`channelRead` 方法会被触发，它将 `childHandler` 添加到新连接的 pipeline 中，并将新连接注册到 workers 线程组。

---

## Buffer 模块

### `ByteBuf`
**位置**: `io.github.specdock.mininetty.buffer.ByteBuf`

对 `java.nio.ByteBuffer` 的封装，提供读写索引管理。

**主要职责**:
- 封装 `ByteBuffer`，维护 `writeIndex` 和 `readIndex`
- 支持从 `SocketChannel` 读取数据到缓冲区
- 支持从缓冲区读取数据到字节数组
- 支持将缓冲区数据写入 `SocketChannel`
- 提供 `release()` 方法释放直接内存（通过 sun.misc.Unsafe 或 sun.nio.ch.DirectBuffer.cleaner）
- 使用 `AtomicBoolean isReleased` 防止 Double Free
- 支持直接内存和堆内存缓冲区的判断

---

### `ByteBufChain`
**位置**: `io.github.specdock.mininetty.buffer.ByteBufChain`

链表结构的缓冲区管理器，用于处理多个 `ByteBuf`。

**主要职责**:
- 使用 `LinkedList<ByteBuf>` 存储多个 ByteBuf 组成缓冲链
- 支持从 SocketChannel 持续读取数据直到无可读字节
- 支持从链表中读取指定长度的字节数据
- 自动回收已读空的 ByteBuf 到池中
- 自动创建新的 ByteBuf 当当前缓冲区写满时
- `recycle()` 方法释放所有缓冲区到池中

---

### `PooledByteBufAllocator`
**位置**: `io.github.specdock.mininetty.buffer.PooledByteBufAllocator`

池化内存分配器，减少内存分配和释放的开销。

**主要职责**:
- 维护直接内存缓冲区池（`directBufferPool`）和堆内存缓冲区池（`heapBufferPool`）
- 默认缓冲区大小 1024 字节，最大池大小 1024
- `allocate(isDirect)` 方法从池中获取缓冲区，池空时创建新的
- `recycle(ByteBuf)` 方法将缓冲区重置后归还池中
- 池满时调用 `ByteBuf.release()` 释放直接内存
- `close()` 方法关闭分配器并释放所有池中缓冲区

---

### `SimpleByteArray`
**位置**: `io.github.specdock.mininetty.buffer.SimpleByteArray`

简单的字节数组包装类。

**主要职责**:
- 封装字节数组 `bytes` 和索引范围 `[begin, end)`
- 提供边界检查（begin >= 0, end <= bytes.length, begin <= end）
- 无参构造抛出异常

---

## Channel 模块

### `Channel`
**位置**: `io.github.specdock.mininetty.channel.Channel`

通道接口，定义通道的核心操作。

**主要职责**:
- 定义通道的绑定（`bind`）、连接（`connect`）、关闭（`close`）操作
- 定义 Selector 注册操作（`register`、`unregister`）
- 定义获取 `SelectionKey`、`EventLoop`、`ChannelPipeline`、`ChannelOutboundBuffer` 的方法
- 定义通道状态查询方法：`isOpen()`、`isActive()`、`isRegistered()`

---

### `ServerChannel`
**位置**: `io.github.specdock.mininetty.channel.ServerChannel`

服务端通道接口，继承自 `Channel`。

**主要职责**:
- 扩展 `Channel` 接口，增加 `accept()` 方法用于接受客户端连接

---

### `SocketChannel`
**位置**: `io.github.specdock.mininetty.channel.socket.SocketChannel`

客户端通道接口，继承自 `Channel`。

**主要职责**:
- 定义获取远程地址（`getRemoveAddress`）和本地地址（`getLocalAddress`）
- 定义 `write(ByteBuffer)` 方法向通道写入数据
- 定义 `finishConnect()` 方法完成非阻塞连接

---

### `ChannelHandler`
**位置**: `io.github.specdock.mininetty.channel.ChannelHandler`

通道处理器接口，定义 handler 的生命周期和事件处理方法。

**主要职责**:
- 定义入站事件处理：`channelRegistered`、`channelActive`、`channelInactive`、`channelRead`、`userEventTriggered`
- 定义出站事件处理：`write`、`flush`
- 出站方法返回 `Future` 用于异步操作追踪

---

### `ChannelInboundHandler`
**位置**: `io.github.specdock.mininetty.channel.ChannelInboundHandler`

入站处理器接口，继承自 `ChannelHandler`。

**主要职责**:
- 用于处理入站数据和控制事件
- 如 `channelRead` 用于处理读取的数据

---

### `ChannelOutboundHandler`
**位置**: `io.github.specdock.mininetty.channel.ChannelOutboundHandler`

出站处理器接口，继承自 `ChannelHandler`。

**主要职责**:
- 用于处理出站数据和控制事件
- 如 `write` 用于处理写入的数据，`flush` 用于刷新发送缓冲区

---

### `ChannelHandlerContext`
**位置**: `io.github.specdock.mininetty.channel.ChannelHandlerContext`

通道处理器上下文接口，提供组件获取和事件传播能力。

**主要职责**:
- 组件获取：`channel()`、`executor()`、`pipeline()`、`handler()`
- 入站事件传播（向后传播）：`fireChannelRegistered`、`fireChannelActive`、`fireChannelInactive`、`fireChannelRead`、`fireChannelReadComplete`、`fireUserEventTriggered`
- 出站事件请求（向前传播）：`bind`、`connect`、`write`、`flush`、`writeAndFlush`

---

### `AbstractChannelHandlerContext`
**位置**: `io.github.specdock.mininetty.channel.AbstractChannelHandlerContext`

`ChannelHandlerContext` 的抽象基类，实现双向链表节点功能。

**主要职责**:
- 实现链表节点：`prev`、`next`
- 实现事件传播：入站事件传播给下一个 handler，出站事件传播给上一个 handler
- 提供组件获取方法的默认实现

---

### `DefaultChannelHandlerContext`
**位置**: `io.github.specdock.mininetty.channel.DefaultChannelHandlerContext`

`ChannelHandlerContext` 的默认实现类。

**主要职责**:
- 继承 `AbstractChannelHandlerContext`
- 实现 `handler()` 方法返回关联的 handler

---

### `ChannelPipeline`
**位置**: `io.github.specdock.mininetty.channel.ChannelPipeline`

通道管道接口，管理多个 `ChannelHandler` 的链表。

**主要职责**:
- Handler 管理：`addFirst`、`addLast`、`addAfter`、`addBefore`、`remove`
- 入站事件触发（从 Head 向 Tail 传播）：`fireChannelRegistered`、`fireChannelActive`、`fireChannelInactive`、`fireChannelRead`、`fireChannelReadComplete`、`fireUserEventTriggered`
- 出站事件请求（从 Tail 向 Head 传播）：`bind`、`connect`、`write`、`flush`、`writeAndFlush`、`close`、`deregister`
- 组件检索：`channel()`、`context()`、`filterContext()`、`first()`、`last()`

---

### `DefaultChannelPipeline`
**位置**: `io.github.specdock.mininetty.channel.DefaultChannelPipeline`

`ChannelPipeline` 的默认实现类，使用双向链表存储 handler。

**主要职责**:
- 维护 HeadContext 和 TailContext 作为链表的头尾哨兵
- 实现所有 `addFirst`、`addLast`、`addAfter`、`addBefore`、`remove` 方法
- 实现所有事件触发和传播方法
- 内部类 `HeadContext`：同时实现 `ChannelOutboundHandler` 和 `ChannelInboundHandler`，负责将出站写入操作写入 `ChannelOutboundBuffer`
- 内部类 `TailContext`：同时实现 `ChannelOutboundHandler` 和 `ChannelInboundHandler`，作为链尾哨兵，消费所有传播到此处的事件

---

### `ChannelInitializer`
**位置**: `io.github.specdock.mininetty.channel.ChannelInitializer`

通道初始化器抽象类，用于配置 channel 的 pipeline。

**主要职责**:
- 实现模板方法模式：`channelRegistered` 作为模板方法调用 `preInit`、`initChannel`、`postInit`
- `preInit`：框架级前置钩子（如插入空闲检测），默认空实现
- `initChannel`：抽象方法，由子类实现业务 handler 装配逻辑
- `postInit`：框架级后置钩子（如插入心跳拦截），默认空实现
- 初始化完成后自动从 pipeline 中移除自身
- 将其他方法透传给下一个 handler

---

### `ServerChannelInitializer`
**位置**: `io.github.specdock.mininetty.channel.ServerChannelInitializer`

服务端通道初始化器，继承自 `ChannelInitializer`。

**主要职责**:
- `preInit`：添加 `IdleStateHandler`（读空闲检测，64秒超时）
- `postInit`：在 `FrameCodec` 注解的 handler 之后添加 `ServerHeartbeatHandler`

---

### `ClientChannelInitializer`
**位置**: `io.github.specdock.mininetty.channel.ClientChannelInitializer`

客户端通道初始化器，继承自 `ChannelInitializer`。

**主要职责**:
- `preInit`：添加 `IdleStateHandler`（读空闲检测，16秒间隔）
- `postInit`：在 `FrameCodec` 注解的 handler 之后添加 `ClientHeartbeatHandler`

---

### `SimpleChannelInboundHandler`
**位置**: `io.github.specdock.mininetty.channel.SimpleChannelInboundHandler`

简单通道入站处理器抽象类，提供方法透传的默认实现。

**主要职责**:
- 所有入站方法默认透传给下一个 handler
- 供业务继承，只需重写关心的方法

---

### `ChannelOutboundBuffer`
**位置**: `io.github.specdock.mininetty.channel.ChannelOutboundBuffer`

出站缓冲区，用于缓存待发送的数据。

**主要职责**:
- 维护 `LinkedList<ByteBufSender>` 队列
- `writeToBuffer`：将消息（ByteBuffer）和 Promise 添加到队列
- `flush`：将数据写入 Socket 发送缓冲区
- 支持 TCP 背压机制：当发送窗口满时注册 OP_WRITE 事件
- 内部类 `ByteBufSender` 继承 `ByteBuf`，附加 Promise 用于异步操作完成通知

---

### `FrameCodec`
**位置**: `io.github.specdock.mininetty.channel.FrameCodec`

注解，用于标记编解码器 handler。

**主要职责**:
- `@Target(ElementType.TYPE)` - 应用于类级别
- `@Retention(RetentionPolicy.RUNTIME)` - 运行时保留
- 用于 `addAfter`、`addBefore` 等方法中定位编解码器位置

---

### `EventLoop`
**位置**: `io.github.specdock.mininetty.channel.EventLoop`

事件循环接口，继承自 `EventLoopGroup`。

**主要职责**:
- 定义 `getScheduleTaskQueue()` 获取定时任务队列
- 定义 `inEventLoop()` 判断当前线程是否在事件循环中

---

### `EventLoopGroup`
**位置**: `io.github.specdock.mininetty.channel.EventLoopGroup`

事件循环组接口，管理多个 EventLoop。

**主要职责**:
- 定义任务提交：`execute`、`shedule`、`scheduleAtFixedRate`
- 定义 `next()` 获取下一个 EventLoop（用于负载均衡）
- 定义 `register` 方法将 channel 注册到 Selector

---

### `DefaultChannelPromise`
**位置**: `io.github.specdock.mininetty.channel.DefaultChannelPromise`

Promise 的默认实现，用于异步操作状态同步与回调触发。

**主要职责**:
- 使用 volatile 保证多线程可见性
- 业务线程调用 `addListener` 注册监听器，`sync()` 阻塞等待
- EventLoop 线程调用 `setSuccess()` 或 `setFailure()` 标记完成
- `setSuccess()` 时唤醒所有等待线程并触发所有监听器
- 支持异常传播

---

## NIO Channel 实现

### `NioServerSocketChannel`
**位置**: `io.github.specdock.mininetty.channel.socket.nio.NioServerSocketChannel`

NIO 服务端通道实现，封装 `java.nio.channels.ServerSocketChannel`。

**主要职责**:
- 封装 NIO ServerSocketChannel，配置为非阻塞模式
- 实现 `ServerSocketChannel` 接口的所有方法
- 实现 `accept()` 方法接受客户端连接并返回 `NioSocketChannel`
- 维护 `SelectionKey` 用于 Selector 操作
- 实现 `close()` 方法：取消 SelectionKey 注册并关闭底层通道

---

### `NioSocketChannel`
**位置**: `io.github.specdock.mininetty.channel.socket.nio.NioSocketChannel`

NIO 客户端通道实现，封装 `java.nio.channels.SocketChannel`。

**主要职责**:
- 封装 NIO SocketChannel，配置为非阻塞模式
- 实现 `SocketChannel` 接口的所有方法
- 实现 `connect()` 方法：非阻塞连接，支持 OP_CONNECT 事件
- 实现 `finishConnect()` 方法：完成连接并触发 `fireChannelActive`
- 维护 `ChannelOutboundBuffer` 用于出站数据缓冲
- 实现 `close()` 方法：触发 `fireChannelInactive`、取消 SelectionKey、关闭底层通道

---

### `NioEventLoop`
**位置**: `io.github.specdock.mininetty.channel.nio.NioEventLoop`

NIO 事件循环实现，单线程事件处理器。

**主要职责**:
- 封装 `Selector`，启动专属线程处理事件和任务
- 维护普通任务队列（`ArrayBlockingQueue`）和定时任务队列（`PriorityBlockingQueue`）
- `execute()` 提交普通任务
- `shedule()` / `scheduleAtFixedRate()` 提交定时任务
- `register()` 将 channel 注册到 Selector，触发 pipeline 的 `fireChannelRegistered` 和 `fireChannelActive`
- `processEventsAndTasks()` 协调处理任务和事件：优先处理已到期的定时任务和普通任务，否则阻塞等待事件或超时
- `selectAndDisPatch()` 轮询 Selector 就绪事件并分发处理：
  - OP_ACCEPT → `ServerSocketChannel.accept()` 并触发 `fireChannelRead`
  - OP_READ → 读取数据到 `ByteBufChain` 并触发 `fireChannelRead`
  - OP_WRITE → 调用 `ChannelOutboundBuffer.flush()`
  - OP_CONNECT → 调用 `SocketChannel.finishConnect()`
- 内部类 `NioEventLoopThread`：继承 Thread，不断调用 `processEventsAndTasks()`

---

### `NioEventLoopGroup`
**位置**: `io.github.specdock.mininetty.channel.nio.NioEventLoopGroup`

NIO 事件循环组，管理多个 `NioEventLoop`。

**主要职责**:
- 维护 `NioEventLoop[]` 数组
- `next()` 方法使用 AtomicInteger 轮询获取下一个 EventLoop
- 所有操作委托给 `next()` 返回的 EventLoop 执行

---

## Socket 模块

### `ServerSocketChannel`
**位置**: `io.github.specdock.mininetty.channel.socket.ServerSocketChannel`

服务端通道接口，继承 `ServerChannel`。

**主要职责**:
- 定义 `accept()` 方法接受客户端连接

---

### `SocketChannel`
**位置**: `io.github.specdock.mininetty.channel.socket.SocketChannel`

客户端通道接口，继承 `Channel`。

**主要职责**:
- 定义获取远程/本地地址、写入数据、完成连接的方法

---

## Handler 模块

### `LengthFieldBasedFrameDecoder`
**位置**: `io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameDecoder`

基于长度字段的帧解码器，继承 `ChannelInboundHandler`，带 `@FrameCodec` 注解。

**主要职责**:
- 解析粘包/拆包问题，使用长度字段（默认4字节）标识帧长度
- 从 `ByteBufChain` 链中读取数据
- 先读取长度字段解析帧长度，再读取完整帧数据
- 将完整帧数据包装为 `SimpleByteArray` 传递给下一个 handler
- 支持长度字段为0的空帧处理

---

### `LengthFieldBasedFrameEncoder`
**位置**: `io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameEncoder`

基于长度字段的帧编码器，实现 `ChannelOutboundHandler`，带 `@FrameCodec` 注解。

**主要职责**:
- 在发送数据前添加4字节长度的帧头
- `createTargetBuffer()` 创建 [长度(4字节) + 数据] 格式的字节数组
- 其他方法透传给下一个 handler

---

### `StringDecoder`
**位置**: `io.github.specdock.mininetty.channel.handler.codec.StringDecoder`

字符串解码器，实现 `ChannelInboundHandler`。

**主要职责**:
- 将 `SimpleByteArray` 字节数组转换为 UTF-8 字符串
- 调用 `fireChannelRead(String)` 传递给下一个 handler

---

### `StringEncoder`
**位置**: `io.github.specdock.mininetty.channel.handler.codec.StringEncoder`

字符串编码器，实现 `ChannelOutboundHandler`。

**主要职责**:
- 将字符串转换为 UTF-8 字节数组
- 调用 `ctx.write(byte[])` 传递给下一个 handler

---

### `IdleStateHandler`
**位置**: `io.github.specdock.mininetty.channel.handler.timeout.IdleStateHandler`

空闲状态处理器，实现 `ChannelHandler`。

**主要职责**:
- 检测 channel 读空闲状态
- 当空闲时间超过阈值时触发 `userEventTriggered(READER_IDLE_STATE_EVENT)`
- 使用 `HashedWheelTimer` 调度空闲检测任务
- `channelRead` 时更新最后读时间
- `channelInactive` 时取消定时任务

---

### `ClientHeartbeatHandler`
**位置**: `io.github.specdock.mininetty.channel.handler.timeout.ClientHeartbeatHandler`

客户端心跳策略处理器，同时实现 `ChannelInboundHandler` 和 `ChannelOutboundHandler`。

**主要职责**:
- 协议头：0x00 = 业务数据帧，0x01 = Ping，0x02 = Pong
- `userEventTriggered`：收到读空闲事件时发送 Ping（PING_FRAME = new byte[]{1}）
- `channelRead`：消费 Pong 回执；解析业务数据帧，剥离协议头后透传
- `write`（出站拦截）：为业务数据补齐 0x00 协议头

---

### `ServerHeartbeatHandler`
**位置**: `io.github.specdock.mininetty.channel.handler.timeout.ServerHeartbeatHandler`

服务端心跳策略处理器，同时实现 `ChannelInboundHandler` 和 `ChannelOutboundHandler`。

**主要职责**:
- 协议头：0x00 = 业务数据帧，0x01 = Ping，0x02 = Pong
- `channelRead`：收到 Ping 时回复 Pong（PONG_FRAME = new byte[]{2}）；解析业务数据帧，剥离协议头后透传
- `userEventTriggered`：收到读空闲事件时关闭超时连接
- `write`（出站拦截）：为业务数据补齐 0x00 协议头

---

## Timer 模块

### `HashedWheelTimer`
**位置**: `io.github.specdock.mininetty.timer.HashedWheelTimer`

时间轮定时器（全局静态单例版，毫秒精度）。

**主要职责**:
- 使用环形数组（Bucket）实现时间轮
- 每个 Bucket 是双向链表头，存储 `TimeoutTask`
- `newTimeout(task, lastTimeMs, delayMs)` 创建定时任务
- 后台 Worker 线程每 tickMs（默认1000ms）转动一次指针
- `TimeoutTask` 记录剩余圈数和截止时间
- `TimeoutTask.cancel()` 取消任务并置空 Runnable 以释放引用
- 守护线程模式，不影响 JVM 退出

---

## Util 模块

### `HeartbeatConstant`
**位置**: `io.github.specdock.mininetty.util.HeartbeatConstant`

心跳常量定义。

**主要职责**:
- `HEARTBEAT_INTERVAL_MS = 16000`：客户端心跳间隔（16秒）
- `HEARTBEAT_TIMEOUT_MS = 64000`：服务端读空闲超时（64秒）

---

### `InterestOpsUtil`
**位置**: `io.github.specdock.mininetty.util.InterestOpsUtil`

SelectionKey 操作位工具类。

**主要职责**:
- `interestOpsToString(int interestOps)` 将 SelectionKey 操作位转换为可读字符串
- 支持 OP_ACCEPT、OP_READ、OP_WRITE、OP_CONNECT

---

## Util Concurrent 模块

### `Future`
**位置**: `io.github.specdock.mininetty.util.concurrent.Future`

异步操作结果接口。

**主要职责**:
- 状态查询：`isSuccess()`、`isDone()`、`cause()`
- 回调注册：`addListener(GenericFutureListener)`
- 阻塞等待：`sync()`
- 获取关联的 `Channel`

---

### `Promise`
**位置**: `io.github.specdock.mininetty.util.concurrent.Promise`

可写的异步承诺接口，继承 `Future`。

**主要职责**:
- `setSuccess()` 标记操作成功
- `setFailure(Throwable cause)` 标记操作失败
- `setChannel(Channel channel)` 设置关联的 Channel

---

### `GenericFutureListener`
**位置**: `io.github.specdock.mininetty.util.concurrent.GenericFutureListener`

异步操作监听器接口。

**主要职责**:
- `operationComplete(Future future)` 当异步操作完成时调用

---

### `ScheduleTask`
**位置**: `io.github.specdock.mininetty.util.concurrent.ScheduleTask`

定时任务实现类，实现 `Runnable` 和 `Comparable`。

**主要职责**:
- 维护 `Runnable`、`deadLine`、`period`、`EventLoop`
- 实现 `compareTo()` 按截止时间排序
- `run()` 执行任务后，如果 period > 0 则更新截止时间并重新入队
- 支持周期性任务调度

---

## 测试类（位于 src/test）

以下测试类位于 `src/test/java/io/github/specdock/mininetty/` 目录：

| 类名 | 描述 |
|------|------|
| `BootstrapTest` | Bootstrap 客户端连接测试 |
| `ServerBootstrapTest` | ServerBootstrap 服务端绑定测试 |
| `DirectBufferTest` | 直接内存缓冲区测试 |
| `PooledByteBufAllocatorTest` | 池化分配器测试 |
| `TextTest` | 文本处理测试 |

---

## 模块关系总结

```
bootstrap/
├── Bootstrap          - 客户端启动类
└── ServerBootstrap    - 服务端启动类（含 ServerBootstrapAcceptor 内部类）

buffer/
├── ByteBuf            - ByteBuffer 封装
├── ByteBufChain       - ByteBuf 链表管理器
├── PooledByteBufAllocator - 内存池分配器
└── SimpleByteArray    - 字节数组包装

channel/
├── Channel            - 通道核心接口
├── ServerChannel      - 服务端通道接口
├── ChannelHandler     - 处理器接口
├── ChannelInboundHandler / ChannelOutboundHandler - 入站/出站接口
├── ChannelHandlerContext - 上下文接口
├── AbstractChannelHandlerContext - 上下文抽象类
├── DefaultChannelHandlerContext - 上下文默认实现
├── ChannelPipeline    - 管道接口
├── DefaultChannelPipeline - 管道实现（含 HeadContext/TailContext）
├── ChannelInitializer - 初始化器抽象类
├── ServerChannelInitializer / ClientChannelInitializer - 初始化器实现
├── SimpleChannelInboundHandler - 简单处理器基类
├── ChannelOutboundBuffer - 出站缓冲
├── FrameCodec         - 注解
├── EventLoop / EventLoopGroup - 事件循环接口
├── DefaultChannelPromise - Promise 实现
└── socket/
    ├── ServerSocketChannel / SocketChannel - socket 通道接口
    └── nio/
        ├── NioServerSocketChannel - NIO 服务端实现
        ├── NioSocketChannel      - NIO 客户端实现
        ├── NioEventLoop          - NIO 事件循环
        └── NioEventLoopGroup     - NIO 事件循环组

handler/
└── codec/
    ├── LengthFieldBasedFrameDecoder - 长度字段解码器
    ├── LengthFieldBasedFrameEncoder - 长度字段编码器
    ├── StringDecoder / StringEncoder - 字符串编解码器
└── timeout/
    ├── IdleStateHandler - 空闲检测
    ├── ClientHeartbeatHandler - 客户端心跳
    └── ServerHeartbeatHandler - 服务端心跳

timer/
└── HashedWheelTimer - 时间轮定时器

util/
├── HeartbeatConstant - 心跳常量
├── InterestOpsUtil - 操作位工具
└── concurrent/
    ├── Future / Promise - 异步接口
    ├── GenericFutureListener - 监听器接口
    └── ScheduleTask - 定时任务
```