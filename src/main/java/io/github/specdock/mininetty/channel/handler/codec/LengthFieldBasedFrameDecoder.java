package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.util.LinkedList;

/**
 * @author specdock
 * @Date 2026/2/26
 * @Time 14:41
 */

@FrameDecoder
public class LengthFieldBasedFrameDecoder implements ChannelInboundHandler{
    private final int lengthFieldLength;
    private final LinkedList<ByteBufChain> byteBufChainList;
    private int lengthField;
    private byte[] target;
    private int lengthFieldOffset;
    private int targetOffset;
    private byte[] lengthFieldBytes;

    public LengthFieldBasedFrameDecoder(int lengthFieldLength){
        this.lengthFieldLength = lengthFieldLength;
        byteBufChainList = new LinkedList<>();
        lengthField = 0;
        target = null;
        lengthFieldOffset = 0;
        targetOffset = 0;
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
        while(true){
            if(byteBufChainList.isEmpty()){
                return ;
            }
            ByteBufChain byteBufChain = byteBufChainList.getFirst();
            if(byteBufChain.length() <= 0){
                byteBufChainList.remove(0);
                continue;
            }
            if(target == null){
                if(lengthFieldLength - lengthFieldOffset > byteBufChainListLength()){
                    return;
                }
                if(lengthFieldBytes == null){
                    lengthFieldBytes = new byte[lengthFieldLength];
                }

                int readLength = Math.min(lengthFieldLength - lengthFieldOffset, byteBufChain.length());
                byteBufChain.read(lengthFieldBytes, lengthFieldOffset, readLength);
                lengthFieldOffset += readLength;

                if(lengthFieldOffset < lengthFieldLength){
                    continue;
                }

                lengthFieldOffset = 0;
                lengthField = bytesToInt(lengthFieldBytes);
                target = new byte[lengthField];
                if(lengthField == 0){
                    ctx.fireChannelRead(target);
                    target = null;
                    continue;
                }
            }
            int readLength = Math.min(lengthField, byteBufChain.length());
            if(readLength <= 0){
                continue;
            }
            byteBufChain.read(target, targetOffset, readLength);
            targetOffset += readLength;
            lengthField -= readLength;
            if(lengthField > 0){
                continue;
            }
            ctx.fireChannelRead(target);
            lengthField = 0;
            target = null;
            targetOffset = 0;
        }
    }

    private int byteBufChainListLength(){
        int sum = 0;
        for(ByteBufChain byteBufChain : byteBufChainList){
            sum += byteBufChain.length();
        }
        return sum;
    }

    private int bytesToInt(byte[] bytes){
        int sum = 0;
        for (byte aByte : bytes) {
            // 1. 将之前累加的值整体左移 8 位（腾出一个字节的空间）
            sum <<= 8;

            // 2. 消除符号位扩展，并将其"镶嵌"到刚刚腾出的低 8 位空间中
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
}