package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.ByteBuffer;

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
        ReferenceCounted payload = toReferenceCounted(msg);
        ByteBuf header = null;
        CompositeByteBuf frame = null;
        boolean transferred = false;
        try {
            int length = readableBytes(payload);
            header = new ByteBuf(ByteBuffer.allocateDirect(lengthFieldLength));
            writeLength(header, length);
            // length header 与 payload 组合成逻辑连续帧，不复制 payload 字节。
            frame = new CompositeByteBuf().addComponent(header).addComponent(payload);
            header = null;
            payload = null;
            ctx.write(frame, promise);
            transferred = true;
        } finally {
            if (!transferred) {
                if (frame != null) {
                    frame.release();
                } else {
                    if (header != null) {
                        header.release();
                    }
                    if (payload != null) {
                        payload.release();
                    }
                }
            }
        }
        return promise;
    }

    private void writeLength(ByteBuf header, int length) {
        int max = lengthFieldLength == 4 ? Integer.MAX_VALUE : (1 << (lengthFieldLength * 8)) - 1;
        if (length < 0 || length > max) {
            throw new IllegalArgumentException("Frame length exceeds " + lengthFieldLength + " byte length field: " + length);
        }
        for (int i = lengthFieldLength - 1; i >= 0; i--) {
            header.writeByte((length >>> (i * 8)) & 0xFF);
        }
    }

    private ReferenceCounted toReferenceCounted(Object msg){
        if(msg instanceof ReferenceCounted){
            return (ReferenceCounted) msg;
        }
        if(msg instanceof byte[]){
            // 兼容旧 byte[] 出站；主链路由 StringEncoder 或业务层直接产出 ByteBuf。
            byte[] bytes = (byte[]) msg;
            ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
            buf.writeBytes(bytes);
            return buf;
        }
        throw new IllegalArgumentException("Unsupported outbound message type: " + msg.getClass().getName());
    }

    private int readableBytes(ReferenceCounted msg){
        return msg instanceof ByteBuf ? ((ByteBuf) msg).readableBytes() : ((CompositeByteBuf) msg).readableBytes();
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
