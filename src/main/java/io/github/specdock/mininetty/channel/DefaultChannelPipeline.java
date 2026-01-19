package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 16:41
 */
public class DefaultChannelPipeline implements ChannelPipeline{
    private final Channel channel;

    // TODO 之后将提取一个抽象类出来
    private DefaultChannelHandlerContext head;
    private final DefaultChannelHandlerContext tail;

    public DefaultChannelPipeline(Channel channel){
        this.channel = channel;
        // TODO 之后这里的参数null改为HeadChannelHandler和TailCHannelHandler
        head = new DefaultChannelHandlerContext(null, this);
        tail = new DefaultChannelHandlerContext(null, this);
        head.next = tail;
        tail.prev = head;
    }


    @Override
    public ChannelPipeline addFirst(ChannelHandler handler) {
        DefaultChannelHandlerContext context = new DefaultChannelHandlerContext(handler, this);
        // 插入到tail节点前面
        context.next = tail;
        context.prev = tail.prev;
        tail.prev.next = context;
        tail.prev = context;
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler handler) {
        DefaultChannelHandlerContext context = new DefaultChannelHandlerContext(handler, this)；
        // 插入到head节点后面
        context.prev = head;
        context.next = head.next;
        head.next.prev = context;
        head.next = context;
        return this;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        DefaultChannelHandlerContext index = head.next;
        while(index != tail){
            if(index.handler() == handler){
                index.prev.next = index.next;
                index.next.prev = index.prev;
                return this;
            }
            index = index.next;
        }
        System.out.println(this.getClass().getName() + ":remove，未找到要删除的ChannelHandler");
        return this;
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        return null;
    }


    // TODO
    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        return null;
    }

    // TODO
    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        return null;
    }

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
    public ChannelPipeline flush() {
        return null;
    }

    @Override
    public void writeAndFlush(Object msg) {

    }

    @Override
    public void close() {

    }

    @Override
    public void deregister() {

    }

    @Override
    public Channel channel() {
        return null;
    }

    @Override
    public ChannelHandlerContext context(String name) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelHandler first() {
        return null;
    }

    @Override
    public ChannelHandler last() {
        return null;
    }
}
