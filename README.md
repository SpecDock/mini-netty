🚀 Mini-Netty
一个基于 Java NIO 从零构建的高性能、异步事件驱动的网络通信框架。

📖 项目简介
Mini-Netty 并非对原生 Netty 的简单 API 封装，而是脱离 Netty 源码，直接基于 JDK 原生 NIO (Non-blocking I/O) 底层物理机制手写实现的基础通信底座。

本项目旨在彻底打通操作系统底层的多路复用（Epoll/Selector）、TCP/IP 协议栈流转、Java 内存模型（JMM）与高并发无锁化编程之间的技术壁垒。具备极高的鲁棒性，能够完美应对海量并发连接与极端的网络物理分片场景。

🎯 核心架构与特性
1. 🧵 主从 Reactor 多线程模型 (Thread Model)
职责隔离：基于 BossGroup (处理 OP_ACCEPT) 与 WorkerGroup (处理 OP_READ/OP_WRITE) 的职责分离，大幅提升服务端接入吞吐量。

线程封闭 (Thread Confinement)：内置任务队列（Task Queue）与 inEventLoop 线程校验机制。所有的 I/O 读写与 Channel 状态转移均在绑定的单线程内串行执行，实现全局无锁化 (Lock-free Serialization)。

2. 🧠 内存池化与复合缓冲区 (Composite Buffer)
双指针读写模型：彻底废弃 JDK 原生 ByteBuffer 繁琐的 flip() 模式。自定义 ByteBuf 与 ByteBufChain，通过 readIndex 与 writeIndex 实现读写状态的绝对隔离。

零拷贝视图与堆外内存：支持 Direct Buffer 分配，减少内核态到用户态的 CPU 拷贝开销。通过底层链表结构物理拼装内存碎片，向上层提供连续的逻辑内存视图。

3. 🛡️ 责任链模式与双向事件流转 (ChannelPipeline)
高度可插拔：构建 ChannelHandlerContext 双向链表。入站事件 (Inbound) 严格遵循 Head -> Tail 顺向传播，出站事件 (Outbound) 严格遵循 Tail -> Head 逆向流转。

TCP 背压控制 (Backpressure)：针对 OP_WRITE 缓冲区满载场景，实现动态的事件注册与注销机制，彻底杜绝底层 EventLoop CPU 100% 空转。

4. ⚡ 异步契约与跨线程唤醒 (Promise/Future)
独立实现异步状态机。利用 volatile 内存可见性屏障与对象监视器 (wait/notifyAll)，安全实现跨业务线程与 I/O 线程的阻塞挂起、无锁回调与虚假唤醒防御。

🔬 极限压测与鲁棒性验证 (Robustness)
在真实的广域网传输中，TCP 会发生不可预知的粘包与拆包。本项目内置了高度严密的 有状态解码器 (LengthFieldBasedFrameDecoder)。

极限碎片化压测场景：
通过强制干预底层物理层，将单次 SocketChannel.read() 的拉取上限压缩至极端的 1 字节，并设置 EventLoop 的单次读取循环上限为 16 次（防线程饥饿机制）。

测试结果：面对被撕裂成数十个碎片的协议头与 Payload，底层的游标挂起与恢复逻辑精准运作。在跨越多次异步物理中断后，完美重组数据帧，达成零数据丢失、零内存越界、零指针偏移的工业级稳定性。

🚀 快速开始 (Quick Start)
服务端启动示例 (ServerBootstrap)
仅需寥寥数行代码，即可启动一个具备完整解码、编码及业务处理能力的非阻塞服务端：

Java
public class ServerBootstrapTest {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        // 1 个 Boss 线程负责接入，3 个 Worker 线程负责 I/O
        serverBootstrap.group(new NioEventLoopGroup(1), new NioEventLoopGroup(3))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder()) // 解决 TCP 粘包/半包
                                .addLast(new LengthFieldBasedFrameEncoder()) // 协议头组装
                                .addLast(new StringDecoder())                // 字节转字符串
                                .addLast(new StringEncoder())                // 字符串转字节
                                .addLast(new SimpleChannelInboundHandler() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        System.out.println("服务端接收到报文：" + msg);
                                        // 触发后续 Pipeline 或回写响应
                                        ctx.fireChannelRead(msg); 
                                    }
                                });
                    }
                })
                .bind(8080);
    }
}
客户端启动示例 (Bootstrap)
支持异步非阻塞的连接建立与定时心跳/业务数据推送：

Java
public class BootstrapTest {
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder())
                                .addLast(new LengthFieldBasedFrameEncoder())
                                .addLast(new StringDecoder())
                                .addLast(new StringEncoder())
                                .addLast(new SimpleChannelInboundHandler() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        System.out.println("客户端收到响应: " + msg);
                                    }
                                });
                    }
                });

        // 异步发起连接，利用 Future 监听握手状态
        Future connect = bootstrap.connect(new InetSocketAddress("127.0.0.1", 8080));
        connect.addListener(future -> {
            if(future.isSuccess()){
                System.out.println("🎯 TCP 连接建立成功！");
                EventLoop eventLoop = connect.channel().getEventLoop();
                // 提交定时任务至该 Channel 绑定的专属 EventLoop 中，保证无锁化线程安全
                eventLoop.scheduleAtFixedRate(() -> {
                    connect.channel().pipeline().writeAndFlush("Hello, Mini-Netty!");
                }, 0, 5, TimeUnit.SECONDS);
            }
        });
    }
}
👨‍💻 关于作者
开发者: SpecDock

定位: 专注于底层基础架构建设、高并发微服务设计与 Java 后端开发。

如果这个项目对你在理解网络底层编程时有所启发，欢迎点亮 Star ⭐️。
