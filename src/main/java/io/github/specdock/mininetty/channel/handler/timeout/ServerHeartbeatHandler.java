package io.github.specdock.mininetty.channel.handler.timeout;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

public class ServerHeartbeatHandler implements ChannelInboundHandler, ChannelOutboundHandler {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) { ctx.fireChannelRegistered(); }
    @Override
    public void channelActive(ChannelHandlerContext ctx) { ctx.fireChannelActive(); }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) { ctx.fireChannelInactive(); }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("ServerHeartbeatHandler");
        ReferenceCounted frame = (ReferenceCounted) msg;
        if (readableBytes(frame) == 0) { frame.release(); return; }
        // readByte 直接推进 readerIndex，相当于零拷贝剥离心跳协议头。
        byte frameType = readByte(frame);
        if (frameType == 1) {
            System.out.println("ping");
            ctx.writeAndFlush(singleByteBuf(ctx, 2), new DefaultChannelPromise());
            frame.release();
        } else if (frameType == 0) {
            if (readableBytes(frame) > 0) {
                ctx.fireChannelRead(frame);
            }else {
                frame.release();
            }
        } else {
            frame.release();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        System.out.println("ClientHeartbeatHandler:userEventTriggered");
        if (event == IdleStateHandler.READER_IDLE_STATE_EVENT) {
            System.err.println("心跳超时，Channel自动关闭");
            ctx.channel().close();
            return;
        }
        ctx.fireUserEventTriggered(event);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        System.out.println("ServerHeartbeatHandler");
        return ctx.write(withHeader(ctx, 0, msg), promise);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) { ctx.flush(); }

    private ReferenceCounted withHeader(ChannelHandlerContext ctx, int headerValue, Object payload) {
        ByteBufChain frame = new ByteBufChain(true, ctx.executor().allocator());
        frame.writeByte(headerValue);
        ReferenceCounted body = toReferenceCounted(ctx, payload);
        if (body instanceof ByteBufChain) frame.appendChain((ByteBufChain) body);
        else if (body instanceof ByteBuf) frame.append((ByteBuf) body);
        else {
            CompositeByteBuf composite = (CompositeByteBuf) body;
            byte[] bytes = new byte[composite.readableBytes()];
            composite.read(bytes, 0, bytes.length);
            frame.writeBytes(bytes, 0, bytes.length);
            composite.release();
        }
        return frame;
    }

    private ByteBufChain singleByteBuf(ChannelHandlerContext ctx, int value) {
        ByteBufChain header = new ByteBufChain(true, ctx.executor().allocator());
        header.writeByte(value);
        return header;
    }

    private ReferenceCounted toReferenceCounted(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ReferenceCounted) return (ReferenceCounted) msg;
        byte[] bytes = (byte[]) msg;
        ByteBufChain chain = new ByteBufChain(true, ctx.executor().allocator());
        chain.writeBytes(bytes, 0, bytes.length);
        return chain;
    }

    private int readableBytes(ReferenceCounted msg) { if (msg instanceof ByteBuf) return ((ByteBuf) msg).readableBytes(); if (msg instanceof ByteBufChain) return ((ByteBufChain) msg).readableBytes(); return ((CompositeByteBuf) msg).readableBytes(); }
    private byte readByte(ReferenceCounted msg) { if (msg instanceof ByteBuf) return ((ByteBuf) msg).readByte(); if (msg instanceof ByteBufChain) return ((ByteBufChain) msg).readByte(); return ((CompositeByteBuf) msg).readByte(); }
}
