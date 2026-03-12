package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelOutboundHandler;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.charset.StandardCharsets;

/**
 * @author specdock
 * @Date 2026/2/26
 * @Time 15:26
 */
public class StringEncoder implements ChannelOutboundHandler {
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
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        System.out.println("StringEncoder");
        String s = (String) msg;
        ctx.write(s.getBytes(StandardCharsets.UTF_8), promise);
        return promise;
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}