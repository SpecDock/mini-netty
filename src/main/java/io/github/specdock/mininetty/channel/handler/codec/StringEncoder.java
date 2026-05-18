package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelOutboundHandler;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

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
        // 字符串编码是允许的类型转换边界；转换后继续以 direct ByteBuf 进入零拷贝出站链路。
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
        buf.writeBytes(bytes);
        ctx.write(buf, promise);
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

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        ctx.fireUserEventTriggered(event);
    }
}
