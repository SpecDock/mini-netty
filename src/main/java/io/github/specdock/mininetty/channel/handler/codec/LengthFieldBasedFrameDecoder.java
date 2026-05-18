package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.util.LinkedList;

@FrameCodec
public class LengthFieldBasedFrameDecoder implements ChannelInboundHandler{
    private final int lengthFieldLength;
    private int lengthField;
    private final byte[] lengthFieldBytes;
    private final LinkedList<ByteBufChain> byteBufChainList;

    public LengthFieldBasedFrameDecoder(int lengthFieldLength){
        if (lengthFieldLength < 1 || lengthFieldLength > 4) {
            throw new IllegalArgumentException("lengthFieldLength must be between 1 and 4");
        }
        this.lengthFieldLength = lengthFieldLength;
        this.lengthFieldBytes = new byte[lengthFieldLength];
        this.byteBufChainList = new LinkedList<>();
        this.lengthField = -1;
    }

    public LengthFieldBasedFrameDecoder(){
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
        System.out.println("LengthFieldBasedFrameDecoder");
        byteBufChainList.addLast((ByteBufChain) msg);
        while (true) {
            discardEmptyChains();
            if (byteBufChainList.isEmpty()) {
                return;
            }
            if (lengthField < 0) {
                if (byteBufChainListLength() < lengthFieldLength) {
                    return;
                }
                readBytesFromChains(lengthFieldBytes, 0, lengthFieldLength);
                lengthField = bytesToInt(lengthFieldBytes);
            }
            if (byteBufChainListLength() < lengthField) {
                return;
            }
            ReferenceCounted frame = readFrameFromChains(lengthField);
            lengthField = -1;
            ctx.fireChannelRead(frame);
        }
    }

    private void discardEmptyChains() {
        while (!byteBufChainList.isEmpty() && byteBufChainList.getFirst().readableBytes() <= 0) {
            byteBufChainList.removeFirst().release();
        }
    }

    private void readBytesFromChains(byte[] target, int offset, int length){
        // 长度字段属于协议元数据，允许少量复制；payload 仍通过 readFrameFromChains 零拷贝输出。
        while(length > 0){
            ByteBufChain chain = byteBufChainList.getFirst();
            int read = Math.min(length, chain.readableBytes());
            chain.read(target, offset, read);
            offset += read;
            length -= read;
            discardEmptyChains();
        }
    }

    private ReferenceCounted readFrameFromChains(int length){
        CompositeByteBuf frame = new CompositeByteBuf();
        while(length > 0){
            ByteBufChain chain = byteBufChainList.getFirst();
            int read = Math.min(length, chain.readableBytes());
            // 每个 ByteBufChain 片段都以 retained frame 形式加入，避免跨 OP_READ 拼帧时复制 payload。
            frame.addComponent(chain.readRetainedFrame(read));
            length -= read;
            discardEmptyChains();
        }
        return frame;
    }

    private int byteBufChainListLength(){
        int sum = 0;
        for(ByteBufChain byteBufChain : byteBufChainList){
            sum += byteBufChain.readableBytes();
        }
        return sum;
    }

    private int bytesToInt(byte[] bytes){
        int sum = 0;
        for (byte aByte : bytes) {
            sum <<= 8;
            sum |= (aByte & 0xFF);
        }
        return sum;
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        return ctx.write(msg, promise);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        ctx.write(msg, promise);
        return promise;
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
