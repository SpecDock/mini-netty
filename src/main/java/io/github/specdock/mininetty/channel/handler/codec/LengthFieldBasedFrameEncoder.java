package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

@FrameCodec
public class LengthFieldBasedFrameEncoder implements ChannelOutboundHandler {

    private final int lengthFieldLength;

    public LengthFieldBasedFrameEncoder(int lengthFieldLength){
        if (lengthFieldLength < 1 || lengthFieldLength > 4) {
            throw new IllegalArgumentException("lengthFieldLength must be between 1 and 4");
        }
        this.lengthFieldLength = lengthFieldLength;
    }

    public LengthFieldBasedFrameEncoder(){
        this(4);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) { ctx.fireChannelRegistered(); }

    @Override
    public void channelActive(ChannelHandlerContext ctx) { ctx.fireChannelActive(); }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) { ctx.fireChannelInactive(); }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) { ctx.fireChannelRead(msg); }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        System.out.println("LengthFieldBasedFrameEncoder");
        ReferenceCounted payload = toReferenceCounted(ctx, msg);
        ByteBufChain frame = null;
        boolean transferred = false;
        try {
            int length = readableBytes(payload);
            frame = new ByteBufChain(true, ctx.executor().allocator());
            writeLength(frame, length);
            appendPayload(frame, payload);
            payload = null;
            ctx.write(frame, promise);
            transferred = true;
        } finally {
            if (!transferred) {
                if (frame != null) {
                    frame.release();
                } else if (payload != null) {
                    payload.release();
                }
            }
        }
        return promise;
    }

    private void writeLength(ByteBufChain header, int length) {
        int max = lengthFieldLength == 4 ? Integer.MAX_VALUE : (1 << (lengthFieldLength * 8)) - 1;
        if (length < 0 || length > max) {
            throw new IllegalArgumentException("Frame length exceeds " + lengthFieldLength + " byte length field: " + length);
        }
        for (int i = lengthFieldLength - 1; i >= 0; i--) {
            header.writeByte((length >>> (i * 8)) & 0xFF);
        }
    }

    private ReferenceCounted toReferenceCounted(ChannelHandlerContext ctx, Object msg){
        if(msg instanceof ReferenceCounted){
            return (ReferenceCounted) msg;
        }
        if(msg instanceof byte[]){
            byte[] bytes = (byte[]) msg;
            ByteBufChain chain = new ByteBufChain(true, ctx.executor().allocator());
            chain.writeBytes(bytes, 0, bytes.length);
            return chain;
        }
        throw new IllegalArgumentException("Unsupported outbound message type: " + msg.getClass().getName());
    }

    private int readableBytes(ReferenceCounted msg){
        if (msg instanceof ByteBuf) return ((ByteBuf) msg).readableBytes();
        if (msg instanceof ByteBufChain) return ((ByteBufChain) msg).readableBytes();
        return ((CompositeByteBuf) msg).readableBytes();
    }

    private void appendPayload(ByteBufChain frame, ReferenceCounted payload) {
        if (payload instanceof ByteBufChain) {
            frame.appendChain((ByteBufChain) payload);
        } else if (payload instanceof ByteBuf) {
            frame.append((ByteBuf) payload);
        } else {
            CompositeByteBuf composite = (CompositeByteBuf) payload;
            byte[] bytes = new byte[composite.readableBytes()];
            composite.read(bytes, 0, bytes.length);
            frame.writeBytes(bytes, 0, bytes.length);
            composite.release();
        }
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) { ctx.flush(); }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) { ctx.fireUserEventTriggered(event); }
}
