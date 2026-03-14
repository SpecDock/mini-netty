package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author specdock
 * @Date 2026/2/26
 * @Time  14:42
 */

@FrameCodec
public class LengthFieldBasedFrameEncoder implements ChannelOutboundHandler {

    private final int lengthFieldLength;

    public LengthFieldBasedFrameEncoder(int lengthFieldLength){
        this.lengthFieldLength = lengthFieldLength;
    }

    public LengthFieldBasedFrameEncoder(){
        this(4);
    }


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
        System.out.println("LengthFieldBasedFrameEncoder");
        byte[] buffer = (byte[]) msg;
        byte[] targetBuffer = createTargetBuffer(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(targetBuffer);
        byteBuffer.position(byteBuffer.limit());
        ctx.write(byteBuffer, promise);
        return promise;
    }

    private byte[] createTargetBuffer(byte[] buffer){
        byte[] targetBuffer = new byte[buffer.length + lengthFieldLength];
        int length = buffer.length;
        targetBuffer[0] = (byte) ((length >> 24) & 0xFF);
        targetBuffer[1] = (byte) ((length >> 16) & 0xFF);
        targetBuffer[2] = (byte) ((length >> 8) & 0xFF);
        targetBuffer[3] = (byte) (length & 0xFF);
        System.arraycopy(buffer, 0, targetBuffer, 4, buffer.length);
        return targetBuffer;
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