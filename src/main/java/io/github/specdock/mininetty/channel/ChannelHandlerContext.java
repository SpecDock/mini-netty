package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:13
 */
public interface ChannelHandlerContext {

    // ------- 组件获取 --------
    Channel channel();
    EventLoop executor();
    ChannelPipeline pipeline();
    ChannelHandler handler();




    // ------- 入栈事件传播（向后传播）-------
    ChannelHandlerContext fireChannelRegistered();
    ChannelHandlerContext fireChannelRead(Object msg);
    ChannelHandlerContext fireChannelReadComplete();




    // --- 出站事件请求 (向前传播) ---
    // TODO 待完成：还未实现 Future 机制，暂时先返回 void
    void bind(SocketAddress localAddress);
    void connect(SocketAddress remoteAddress);
    Future write(Object msg, Promise promise);
    ChannelHandlerContext flush();
    Future writeAndFlush(Object msg, Promise promise);





}
