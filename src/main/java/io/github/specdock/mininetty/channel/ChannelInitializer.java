package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 16:44
 */
public abstract class ChannelInitializer<C extends Channel> implements ChannelHandler {

    protected abstract void initChannel(C ch) throws Exception;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("ChannelInitializer");
        ctx.fireChannelRead(msg);
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
    public void channelRegistered(ChannelHandlerContext ctx) {
        try {
            System.out.println("-------------------------开始执行ChannelInitializer的channelRegistered方法");
            C channel = (C) ctx.channel();
            initChannel(channel);
            ctx.pipeline().remove(this);
            ctx.fireChannelRegistered();
            System.out.println("-------------------------ChannelInitializer执行完毕，已移除");
        } catch (Exception e) {
            throw new RuntimeException("ChannelInitializer的channelRegistered方法出现异常", e);
        }

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