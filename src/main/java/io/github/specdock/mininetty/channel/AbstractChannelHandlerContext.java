package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/2/24
 * @Time 17:58
 */
public abstract class AbstractChannelHandlerContext implements ChannelHandlerContext {
    // 前面不加修饰符，字段在包内可以直接访问
    AbstractChannelHandlerContext prev;
    AbstractChannelHandlerContext next;
    ChannelHandler handler;
    final ChannelPipeline pipeline;

    public AbstractChannelHandlerContext(ChannelHandler handler, ChannelPipeline pipeline){
        this.handler = handler;
        this.pipeline = pipeline;
    }

    // ------------------------------- super只能在第一行调用，所以实现这个方法来后续传入handler
    public void setHandler(ChannelHandler handler){
        this.handler = handler;
    }

    // ---------------------------------- 组件获取
    @Override
    public Channel channel() {
        return pipeline().channel();
    }

    @Override
    public EventLoop executor() {
        return pipeline().channel().getEventLoop();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }





    // ------------------------------------------------- 入栈事件传播（向后传播）
    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        next.handler().channelRegistered(next);
        return this;
    }


    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        next.handler().channelRead(next, msg);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        return null;
    }







    // --------------------------------------------- 出站事件请求（向前传播）
    @Override
    public void bind(SocketAddress localAddress) {

    }

    @Override
    public void connect(SocketAddress remoteAddress) {

    }

    @Override
    public void write(Object msg, Object promise) {
        prev.handler().write(prev, msg, promise);
    }

    @Override
    public ChannelHandlerContext flush() {
        prev.handler().flush(prev);
        return this;
    }


    @Override
    public void writeAndFlush(Object msg, Object promise) {
        write(msg, promise);
        flush();
    }

    @Override
    public void close() {

    }

}
