package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelInboundHandler;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.charset.StandardCharsets;

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
        ReferenceCounted buffer = (ReferenceCounted) msg;
        try {
            // 字符串解码是明确的类型转换边界：这里允许复制到 byte[] 后创建 String。
            int length = readableBytes(buffer);
            byte[] bytes = new byte[length];
            if (buffer instanceof ByteBuf) {
                ((ByteBuf) buffer).read(bytes, 0, length);
            } else if (buffer instanceof ByteBufChain) {
                ((ByteBufChain) buffer).read(bytes, 0, length);
            } else {
                ((CompositeByteBuf) buffer).read(bytes, 0, length);
            }
            ctx.fireChannelRead(new String(bytes, StandardCharsets.UTF_8));
        } finally {
            // 转换后 ByteBuf/CompositeByteBuf 不再向后传播，必须释放入站引用。
            buffer.release();
        }
    }

    private int readableBytes(ReferenceCounted buffer) {
        if (buffer instanceof ByteBuf) return ((ByteBuf) buffer).readableBytes();
        if (buffer instanceof ByteBufChain) return ((ByteBufChain) buffer).readableBytes();
        return ((CompositeByteBuf) buffer).readableBytes();
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

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        ctx.fireUserEventTriggered(event);
    }
}
