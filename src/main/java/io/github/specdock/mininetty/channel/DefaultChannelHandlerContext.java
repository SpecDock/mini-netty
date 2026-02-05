package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 20:15
 */
public class DefaultChannelHandlerContext implements ChannelHandlerContext{
    // 前面不加修饰符，字段在包内可以直接访问
    DefaultChannelHandlerContext prev;
    DefaultChannelHandlerContext next;
    ChannelHandler handler;
    private final ChannelPipeline pipeline;

    public DefaultChannelHandlerContext(ChannelHandler handler, ChannelPipeline pipeline){
        this.handler = handler;
        this.pipeline = pipeline;
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

    @Override
    public ChannelHandler handler() {
        return handler;
    }




    // ------------------------------------------------- 入栈事件传播（向后传播）
    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        return null;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        return null;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        return null;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        return null;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
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
    public void write(Object msg) {

    }

    @Override
    public ChannelHandlerContext flush() {
        return null;
    }


    @Override
    public void writeAndFlush(Object msg) {

    }

    @Override
    public void close() {

    }
}
