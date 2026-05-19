package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelOutboundHandler;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetEncoder;

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
        ByteBufChain chain = new ByteBufChain(true, ctx.executor().allocator());
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        CharBuffer src = CharBuffer.wrap(s);
        boolean success = false;
        try {
            while (true) {
                ByteBuffer dst = chain.writableNioBuffer();
                int before = dst.position();
                CoderResult result = encoder.encode(src, dst, true);
                chain.advanceWriterIndex(dst.position() - before);
                if (result.isOverflow()) continue;
                if (result.isError()) result.throwException();
                break;
            }
            while (true) {
                ByteBuffer dst = chain.writableNioBuffer();
                int before = dst.position();
                CoderResult result = encoder.flush(dst);
                chain.advanceWriterIndex(dst.position() - before);
                if (result.isOverflow()) continue;
                if (result.isError()) result.throwException();
                break;
            }
            ctx.write(chain, promise);
            success = true;
        } catch (Exception e) {
            promise.setFailure(e);
            throw new RuntimeException("Failed to encode string", e);
        } finally {
            if (!success) chain.release();
        }
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
