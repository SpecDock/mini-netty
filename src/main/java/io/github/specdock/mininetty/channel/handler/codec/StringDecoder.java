package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelInboundHandler;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author specdock
 * @Date 2026/2/26
 * @Time 15:26
 */
public class StringDecoder implements ChannelInboundHandler {

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
        System.out.println("StringDecoder");
        byte[] buffer = (byte[]) msg;
        String target = new String(buffer, StandardCharsets.UTF_8);
        ctx.fireChannelRead(target);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        return ctx.write(msg, promise);

    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return ctx.write(msg, promise);

    }



    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}