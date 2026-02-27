# mini-netty

本项目是一个基于 Java NIO 开发的轻量级、事件驱动网络通信框架，核心架构深度参考 Netty 实现。

## 核心实现说明

* **基于 Java NIO 多路复用器 (Selector)**，实现了主从 Reactor 线程模型（`NioEventLoopGroup` 与 `NioEventLoop`），完成底层网络连接与读写事件的非阻塞轮询与调度。
* **基于责任链模式 (Chain of Responsibility)**，实现了 `ChannelPipeline` 与 `ChannelHandlerContext` 架构，完成入站 (Inbound) 与出站 (Outbound) 事件的业务解耦与动态流转。
* **基于有限状态机机制**，实现了 `LengthFieldBasedFrameDecoder` (基于长度字段的帧解码器) 与编码器，解决 TCP 底层字节流的粘包与半包问题。
* **基于链式数据结构**，实现了自定义的 `ByteBufChain` 动态缓冲区，兼容堆内存与直接内存 (Direct Memory)，完成跨节点网络数据的动态扩容与读取。

## 待完成功能 (TODO)

* **Future 异步通知模型**：`ChannelFuture` 与 `ChannelPromise` 的状态流转机制及监听器 (Listener) 回调逻辑。
* **Write 出站数据流**：`ChannelHandler.write()` 与 `flush()` 的完整底层实现，包括 `ChannelOutboundBuffer` (出站缓冲区) 的构建与网络拥塞时的 `OP_WRITE` 动态注册逻辑。

