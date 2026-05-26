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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;

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
            ctx.fireChannelRead(decode(buffer));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode string", e);
        } finally {
            // 转换后 ByteBuf/CompositeByteBuf 不再向后传播，必须释放入站引用。
            buffer.release();
        }
    }

    private String decode(ReferenceCounted buffer) throws Exception {
        ByteBuffer[] buffers = nioBuffers(buffer);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        CharBuffer out = CharBuffer.allocate(Math.max(1, readableBytes(buffer)));
        byte[] pending = new byte[4];
        int pendingLength = 0;
        for (ByteBuffer in : buffers) {
            pendingLength = decodePending(decoder, out, pending, pendingLength, in);
            while (true) {
                CoderResult result = decoder.decode(in, out, false);
                if (result.isOverflow()) {
                    out = grow(out);
                    continue;
                }
                if (result.isError()) {
                    result.throwException();
                }
                if (in.hasRemaining()) {
                    pendingLength = copyRemaining(in, pending);
                    in.position(in.limit());
                }
                break;
            }
        }
        out = finishDecode(decoder, out, pending, pendingLength);
        out.flip();
        return out.toString();
    }

    private int decodePending(CharsetDecoder decoder, CharBuffer out, byte[] pending, int pendingLength, ByteBuffer in) throws Exception {
        while (pendingLength > 0 && in.hasRemaining()) {
            while (pendingLength < pending.length && in.hasRemaining()) {
                pending[pendingLength++] = in.get();
            }
            ByteBuffer pendingBuffer = ByteBuffer.wrap(pending, 0, pendingLength);
            while (true) {
                CoderResult result = decoder.decode(pendingBuffer, out, false);
                if (result.isOverflow()) {
                    out = grow(out);
                    continue;
                }
                if (result.isError()) {
                    result.throwException();
                }
                break;
            }
            pendingLength = copyRemaining(pendingBuffer, pending);
        }
        return pendingLength;
    }

    private int copyRemaining(ByteBuffer source, byte[] target) {
        int length = source.remaining();
        if (length > target.length) {
            throw new IllegalStateException("UTF-8 pending bytes exceed maximum character length");
        }
        source.get(target, 0, length);
        return length;
    }

    private ByteBuffer[] nioBuffers(ReferenceCounted buffer) {
        if (buffer instanceof ByteBuf) {
            return new ByteBuffer[]{((ByteBuf) buffer).nioBuffer()};
        }
        if (buffer instanceof ByteBufChain) {
            return ((ByteBufChain) buffer).nioBuffers(Integer.MAX_VALUE);
        }
        return ((CompositeByteBuf) buffer).nioBuffers();
    }

    private CharBuffer finishDecode(CharsetDecoder decoder, CharBuffer out, byte[] pending, int pendingLength) throws Exception {
        ByteBuffer input = pendingLength == 0 ? ByteBuffer.allocate(0) : ByteBuffer.wrap(pending, 0, pendingLength);
        while (true) {
            CoderResult result = decoder.decode(input, out, true);
            if (result.isOverflow()) {
                out = grow(out);
                continue;
            }
            if (result.isError()) {
                result.throwException();
            }
            break;
        }
        while (true) {
            CoderResult result = decoder.flush(out);
            if (result.isOverflow()) {
                out = grow(out);
                continue;
            }
            if (result.isError()){
                result.throwException();
            }
            return out;
        }
    }

    private CharBuffer grow(CharBuffer buffer) {
        int newCapacity = Math.max(buffer.capacity() * 2, buffer.capacity() + 1);
        CharBuffer grown = CharBuffer.allocate(newCapacity);
        buffer.flip();
        grown.put(buffer);
        return grown;
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
