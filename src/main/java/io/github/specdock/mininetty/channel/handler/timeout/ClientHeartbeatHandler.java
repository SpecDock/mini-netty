package io.github.specdock.mininetty.channel.handler.timeout;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.buffer.SimpleByteArray;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.ByteBuffer;

public class ClientHeartbeatHandler implements ChannelInboundHandler, ChannelOutboundHandler {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) { ctx.fireChannelRegistered(); }
    @Override
    public void channelActive(ChannelHandlerContext ctx) { ctx.fireChannelActive(); }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) { ctx.fireChannelInactive(); }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("ClientHeartbeatHandler");
        if (msg instanceof SimpleByteArray) {
            SimpleByteArray frameData = (SimpleByteArray) msg;
            if (frameData == null || frameData.end - frameData.begin == 0) return;
            byte frameType = frameData.bytes[frameData.begin];
            if (frameType == 2) { System.out.println("Pong"); return; }
            if (frameType == 0 && frameData.end - frameData.begin > 1) { frameData.begin++; ctx.fireChannelRead(frameData); }
            return;
        }
        ReferenceCounted frame = (ReferenceCounted) msg;
        if (readableBytes(frame) == 0) { frame.release(); return; }
        // readByte 直接推进 readerIndex，相当于零拷贝剥离心跳协议头。
        byte frameType = readByte(frame);
        if (frameType == 2) {
            System.out.println("Pong");
            frame.release();
        } else if (frameType == 0) {
            if (readableBytes(frame) > 0) ctx.fireChannelRead(frame); else frame.release();
        } else {
            frame.release();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        System.out.println("ClientHeartbeatHandler:userEventTriggered");
        if (event == IdleStateHandler.READER_IDLE_STATE_EVENT) {
            Promise promise = new DefaultChannelPromise();
            ctx.writeAndFlush(singleByteBuf(1), promise);
            return;
        }
        ctx.fireUserEventTriggered(event);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        return ctx.write(withHeader(0, msg), promise);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) { ctx.flush(); }

    private ReferenceCounted withHeader(int headerValue, Object payload) {
        // 通过 1 字节 header + payload 组合添加协议头，不复制业务 payload。
        return new CompositeByteBuf().addComponent(singleByteBuf(headerValue)).addComponent(toReferenceCounted(payload));
    }

    private ByteBuf singleByteBuf(int value) {
        ByteBuf header = new ByteBuf(ByteBuffer.allocateDirect(1));
        header.writeByte(value);
        return header;
    }

    private ReferenceCounted toReferenceCounted(Object msg) {
        if (msg instanceof ReferenceCounted) return (ReferenceCounted) msg;
        byte[] bytes = (byte[]) msg;
        ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
        buf.writeBytes(bytes);
        return buf;
    }

    private int readableBytes(ReferenceCounted msg) { return msg instanceof ByteBuf ? ((ByteBuf) msg).readableBytes() : ((CompositeByteBuf) msg).readableBytes(); }
    private byte readByte(ReferenceCounted msg) { return msg instanceof ByteBuf ? ((ByteBuf) msg).readByte() : ((CompositeByteBuf) msg).readByte(); }
}
