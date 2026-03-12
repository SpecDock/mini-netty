package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.function.Function;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 16:41
 */
public class DefaultChannelPipeline implements ChannelPipeline{
    private final Channel channel;

    // TODO 之后将提取一个抽象类出来
    private AbstractChannelHandlerContext head;
    private final AbstractChannelHandlerContext tail;

    public DefaultChannelPipeline(Channel channel){
        this.channel = channel;
        // TODO 之后这里的参数null改为HeadChannelHandler和TailCHannelHandler
        head = new HeadContext(this);
        tail = new TailContext(this);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler handler) {
        DefaultChannelHandlerContext context = new DefaultChannelHandlerContext(handler, this);
        // 插入到tail节点前面
        context.next = tail;
        context.prev = tail.prev;
        tail.prev.next = context;
        tail.prev = context;
        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler handler) {
        DefaultChannelHandlerContext context = new DefaultChannelHandlerContext(handler, this);
        // 插入到head节点后面
        context.prev = head;
        context.next = head.next;
        head.next.prev = context;
        head.next = context;
        return this;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        AbstractChannelHandlerContext index = head.next;
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

    /**
     * 用来唤醒ChannelInitializer（pipeline中的唯一一个ChannelHandler）
     * 要用Channel所注册的Thread来执行，为了线程安全，所以会在注册后调用
     *
     * @return
     */
    @Override
    public ChannelPipeline fireChannelRegistered() {
        head.fireChannelRegistered();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        head.fireChannelActive();
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        head.fireChannelInactive();
        return this;
    }

    /// TODO
    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        head.fireChannelRead(msg);
        return this;
    }

    /// TODO
    @Override
    public ChannelPipeline fireChannelReadComplete() {
        return null;
    }



    @Override
    public void bind(SocketAddress localAddress) {

    }

    @Override
    public void connect(SocketAddress remoteAddress) {

    }

    @Override
    public Future write(Object msg) {
        Promise promise = new DefaultChannelPromise();
        tail.write(msg, promise);
        return promise;
    }

    @Override
    public ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    @Override
    public void writeAndFlush(Object msg) {
        write(msg);
        flush();
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public void deregister() {

    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelHandlerContext context(String fullyQualifiedClassName) {
        AbstractChannelHandlerContext index = head.next;
        while(index != tail){
            String name = index.handler().getClass().getName();
            if(name.equals(fullyQualifiedClassName)){
                return index;
            }
            index = index.next;
        }
        return null;
    }

    @Override
    public ChannelHandlerContext filterContext(Function<ChannelHandler, Boolean> function){
        AbstractChannelHandlerContext index = head.next;
        while(index != tail){
            ChannelHandler channelHandler = index.handler();
            if(function.apply(channelHandler)){
                return index;
            }
            index = index.next;
        }
        return null;
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        AbstractChannelHandlerContext index = head.next;
        while(index != tail){
            ChannelHandler channelHandler = index.handler();
            if(channelHandler == handler){
                return index;
            }
            index = index.next;
        }
        return null;
    }

    @Override
    public ChannelHandler first() {
        if(head.next == tail){
            return null;
        }
        return head.next.handler();
    }

    @Override
    public ChannelHandler last() {
        if(tail.prev == head){
            return null;
        }
        return tail.prev.handler();
    }



    private static class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler{

        public HeadContext(ChannelPipeline pipeline){
            super(null, pipeline);
            setHandler(this);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {

            ctx.fireChannelRegistered();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {

            ctx.fireChannelRead(msg);
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
            System.out.println("HeadContext");
            if(executor().inEventLoop()){
                channel().channelOutboundBuffer().writeToBuffer(msg, promise);
            }
            else {
                executor().execute(() -> {
                    channel().channelOutboundBuffer().writeToBuffer(msg, promise);
                });
            }
            return promise;
        }

        @Override
        public Future write(ChannelHandlerContext ctx, Object msg) {
            Promise promise = new DefaultChannelPromise();
            return write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            if(executor().inEventLoop()){
                channel().channelOutboundBuffer().flush();
            }
            else {
                executor().execute(() -> {
                    channel().channelOutboundBuffer().flush();
                });
            }
        }
    }

    private static class TailContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler{

        public TailContext(ChannelPipeline pipeline){
            super(null, pipeline);
            setHandler(this);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            // 因为是TailContext，所以最后的信息应该销毁掉，不应该再继续传递
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // 因为是TailContext，所以最后的信息应该销毁掉，不应该再继续传递
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // 因为是TailContext，所以最后的信息应该销毁掉，不应该再继续传递
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {

            // 因为是TailContext，所以最后的信息应该销毁掉，不应该再继续传递
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
            ctx.write(msg, promise);
            return promise;
        }

        @Override
        public Future write(ChannelHandlerContext ctx, Object msg) {
            Promise promise = new DefaultChannelPromise();
            ctx.write(msg, promise);
            return promise;
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            ctx.flush();
        }
    }




}