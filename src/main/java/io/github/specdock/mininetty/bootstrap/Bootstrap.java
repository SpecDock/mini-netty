package io.github.specdock.mininetty.bootstrap;

import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.socket.ServerSocketChannel;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:38
 */
public class Bootstrap {
    private EventLoopGroup workers;

    // workers 的 单个EventLoop 里的Channel类
    private Class<? extends SocketChannel> channelClass;

    // workers 里的 Channel的Handler
    private ChannelHandler handler;

    public Bootstrap group(EventLoopGroup eventLoopGroup){
        workers = eventLoopGroup;
        return this;
    }

    public Bootstrap channel(Class<? extends SocketChannel> channelClass){
        this.channelClass = channelClass;
        return this;
    }

    public Bootstrap handler(ChannelHandler handler){
        this.handler = handler;
        return this;
    }

    public Future connect(SocketAddress remoteAddress){
        try {
            // 1. 通道实例化 (Channel Instantiation)
            // 通过反射创建用户指定的底层 Channel 实例（如 NioSocketChannel）
            SocketChannel channel = channelClass.getDeclaredConstructor().newInstance();

            // 2. 管道初始化 (Pipeline Initialization)
            // 将客户端业务逻辑处理器注入该 Channel 的责任链中
            if (this.handler != null) {
                channel.pipeline().addLast(this.handler);
            }

            // 3. 异步契约创建 (Promise Creation)
            // 生成用于追踪最终物理连接结果的凭证
            Promise promise = new DefaultChannelPromise();
            promise.setChannel(channel);

            // 4. 线程模型注册 (EventLoop Registration)
            // 将该 Channel 绑定到 Worker 线程组中的某一个 EventLoop 上
            // 注意：此时可能仅注册 0（无事件），等待 connect 触发 OP_CONNECT
            Future future = workers.register(channel, 0);
            future.sync();

            // 5. 触发物理连接动作 (Physical Connection Trigger)
            // 由于是非阻塞 I/O，必须将 promise 传递到最底层，
            // 以便在底层 java.nio.channels.SocketChannel.finishConnect() 成功时进行回调
            channel.connect(remoteAddress, promise);

            return promise;

        } catch (Exception e) {
            // 捕获反射或底层初始化的同步异常，转化为运行时异常抛出
            throw new RuntimeException("Failed to initiate connection to " + remoteAddress, e);
        }
    }
}
