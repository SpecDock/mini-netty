```text
 __  __ _       _          _   _      _   _         
|  \/  (_)_ __ (_)        | \ | | ___| |_| |_ _   _ 
| |\/| | | '_ \| |  ____  |  \| |/ _ \ __| __| | | |
| |  | | | | | | | |____| | |\  |  __/ |_| |_| |_| |
|_|  |_|_|_| |_|_|        |_| \_|\___|\__|\__|\__, |
                                              |___/
```

# 🚀 Mini-Netty
一个基于 Java NIO 从零构建的高性能、异步事件驱动的网络通信框架。

## 📖 项目简介
Mini-Netty 并非对原生 Netty 的简单 API 封装，而是脱离 Netty 源码，直接基于 JDK 原生 NIO (Non-blocking I/O) 底层物理机制手写实现的基础通信底座。

本项目旨在彻底打通操作系统底层的多路复用（Epoll/Selector）、TCP/IP 协议栈流转、Java 内存模型（JMM）与高并发无锁化编程之间的技术壁垒。具备极高的鲁棒性，能够完美应对海量并发连接与极端的网络物理分片场景。

## 🎯 核心架构与特性

### 1. 🧵 主从 Reactor 多线程模型 (Thread Model)
* **职责隔离**：基于 BossGroup (处理 `OP_ACCEPT`) 与 WorkerGroup (处理 `OP_READ`/`OP_WRITE`) 的职责分离，大幅提升服务端接入吞吐量。
* **线程封闭 (Thread Confinement)**：内置任务队列（Task Queue）与 `inEventLoop` 线程校验机制。所有的 I/O 读写与 Channel 状态转移均在绑定的单线程内串行执行，实现全局无锁化 (Lock-free Serialization)。

### 2. 🧠 内存池化与复合缓冲区 (Composite Buffer)
* **双指针读写模型**：彻底废弃 JDK 原生 `ByteBuffer` 繁琐的 `flip()` 模式。自定义 `ByteBuf` 与 `ByteBufChain`，通过 `readIndex` 与 `writeIndex` 实现读写状态的绝对隔离。
* **零拷贝视图与堆外内存**：支持 Direct Buffer 分配，减少内核态到用户态的 CPU 拷贝开销。通过底层链表结构物理拼装内存碎片，向上层提供连续的逻辑内存视图。
* **精准堆外内存回收 (Deterministic Off-Heap Deallocation)**：针对 Direct Buffer 依赖 GC（如 Cleaner 机制）导致物理内存回收滞后的痛点，引入显式内存释放机制（如基于引用计数 Reference Counting）。通过底层 API 直接干预物理内存地址，实现大额堆外内存的即时销毁，彻底根绝 Native Memory 积压引发的系统级 OOM 隐患。

### 3. 🛡️ 责任链模式与双向事件流转 (ChannelPipeline)
* **高度可插拔**：构建 `ChannelHandlerContext` 双向链表。入站事件 (Inbound) 严格遵循 Head -> Tail 顺向传播，出站事件 (Outbound) 严格遵循 Tail -> Head 逆向流转。
* **TCP 背压控制 (Backpressure)**：针对 `OP_WRITE` 缓冲区满载场景，实现动态的事件注册与注销机制，彻底杜绝底层 EventLoop CPU 100% 空转。

### 4. ⚡ 异步契约与跨线程唤醒 (Promise/Future)
* **独立实现异步状态机**：利用 volatile 内存可见性屏障与对象监视器 (`wait`/`notifyAll`)，安全实现跨业务线程与 I/O 线程的阻塞挂起、无锁回调与虚假唤醒防御。

## 🔬 极限压测与鲁棒性验证 (Robustness)
在真实的广域网传输中，TCP 会发生不可预知的粘包与拆包。本项目内置了高度严密的**有状态解码器** (`LengthFieldBasedFrameDecoder`)。

* **极限碎片化压测场景**：
    通过强制干预底层物理层，将单次 `SocketChannel.read()` 的拉取上限压缩至极端的 **1 字节**，并设置 EventLoop 的单次读取循环上限为 16 次（防线程饥饿机制）。
* **测试结果**：面对被撕裂成数十个碎片的协议头与 Payload，底层的游标挂起与恢复逻辑精准运作。在跨越多次异步物理中断后，完美重组数据帧，达成零数据丢失、零内存越界、零指针偏移的工业级稳定性。

## 🚀 快速开始 (Quick Start)

### 服务端启动示例 (ServerBootstrap)
仅需寥寥数行代码，即可启动一个具备完整解码、编码及业务处理能力的非阻塞服务端：

```java
public class ServerBootstrapTest {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup(3))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>(){

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
                                        String s = (String) msg;
                                        if(s != null && !s.isEmpty()){
                                            System.out.println("这里是服务端接受到的消息：" + s);
                                        }
                                        ctx.fireChannelRead(msg);
                                    }
                                });
                    }
                })
                .bind(8080);
    }
}
```

### 客户端启动示例 (Bootstrap)
支持异步非阻塞的连接建立与定时心跳/业务数据推送：

```java
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
                                        System.out.println(msg);
                                        Future close = ctx.channel().close();
                                        close.addListener(f -> {
                                            if(f.isSuccess()){
                                                System.out.println("客户端TCP连接已经关闭");
                                            }
                                        });
                                    }
                                });
                    }
                });

        Future connect = bootstrap.connect(new InetSocketAddress("127.0.0.1", 8080));
        connect.addListener(future -> {
            if(future.isSuccess()){
                System.out.println("成功连接");
                EventLoop eventExecutors = connect.channel().getEventLoop();
                eventExecutors.scheduleAtFixedRate(() -> {
                    System.out.println(">> 定时任务已触发，准备将数据压入 Pipeline");
                    connect.channel().pipeline().writeAndFlush("你好，我是客户端");
                }, 0, 10, TimeUnit.MILLISECONDS);
            }
            else {
                System.out.println("连接失败");
            }
        });
    }
}
```

## 👨‍💻 关于作者

* **开发者**: SpecDock
* **定位**: 专注于底层基础架构建设、高并发微服务设计与 Java 后端开发。

如果这个项目对你在理解网络底层编程时有所启发，欢迎点亮 Star ⭐️。

---

📚 技术拓展：Java 堆外内存管理与显式释放的底层机制
针对上文提及的“堆外内存释放”，在 Java 虚拟机架构中具有深刻的工程意义。常规的 ByteBuffer.allocateDirect() 分配的内存属于 JVM 堆外内存（Native Memory）。

1. 痛点分析与常规机制的局限
在默认的 JVM 机制中，堆外内存的释放依赖于堆内对应的 DirectByteBuffer 对象被垃圾收集器回收（通常借助 Cleaner 和 PhantomReference 机制）。然而，DirectByteBuffer 本身在 JVM 堆中占用的内存极小。在高并发网络 I/O 场景下，虽然堆外物理内存已经被大量申请，但堆内的 DirectByteBuffer 尚未达到触发垃圾回收（如 Full GC）的阈值。这会导致大额的物理内存被“变相泄漏”，直至操作系统抛出 OOM 异常。

2. 跨版本显式释放方案与切片防御（底层实现思路）
底层通信框架必须掌握内存的生命周期控制权。为了兼顾执行效率与跨 JDK 版本的兼容性（特别是 Java 9 引入的 JPMS 模块化系统），Mini-Netty 摒弃了单一的 Unsafe.freeMemory 调用，转而采用静态预热与自适应降级探测机制：

静态反射预热：在类加载阶段即完成所有底层 Method 句柄的探测与解析，将其固化为全局常量，彻底消除高频 I/O 运行时的反射性能惩罚。

Java 9+ 高效释放：优先探测并调用 sun.misc.Unsafe.invokeCleaner(ByteBuffer)，实现最短路径的物理销毁。

Java 8 经典降级：回退使用 sun.nio.ch.DirectBuffer 获取 Cleaner 实例进行释放。

切片内存泄漏防御 (Sliced Buffer Trap)：针对通过 slice() 或 duplicate() 创建的视图缓冲区，其内部的 cleaner 通常为空。此时框架会通过反射精准调用 attachment() 方法，提取隐藏在后方的真实物理宿主 Buffer，并触发递归释放。若忽略此防御逻辑，将导致大量分片数据引发实质性的内存泄漏。

核心多态释放逻辑抽象示例：

```java
private static void releaseNative(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect()) return;
    try {
        if (INVOKE_CLEANER_METHOD != null) {
            // 兼容 Java 9+ 专有高性能释放
            INVOKE_CLEANER_METHOD.invoke(UNSAFE_INSTANCE, buffer);
        } else if (SUN_NIO_CLEANER_METHOD != null) {
            // 兼容 Java 8 降级释放
            Object cleaner = SUN_NIO_CLEANER_METHOD.invoke(buffer);
            if (cleaner == null) {
                // 视图切片陷阱防御：精准解析 attachment 并递归释放宿主 Buffer
                Object attached = ATTACHMENT_METHOD.invoke(buffer);
                if (attached instanceof ByteBuffer) {
                    releaseNative((ByteBuffer) attached);
                }
                return;
            }
            SUN_MISC_CLEAN_METHOD.invoke(cleaner);
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to release direct memory", e);
    }
}
```
3. 无锁化并发状态防御 (CAS)
底层物理内存的显式回收极度危险。若同一块物理内存被二次释放（Double Free），将直接触发操作系统段错误 (SIGSEGV) 致使 JVM 进程宕机崩溃。

结合引用计数法 (Reference Counting)，框架在内部引入 AtomicBoolean 构筑硬件级的 CAS (Compare-And-Swap) 内存状态屏障。在入站处理链（ChannelPipeline）生命周期终结时，主动调用释放逻辑。该屏障确保了无论在极端并发下有多少个业务线程尝试销毁内存，底层的物理清理逻辑有且仅会被安全执行一次，将内存控制粒度从“GC 周期级别”精细化到“I/O 事件级别”。
