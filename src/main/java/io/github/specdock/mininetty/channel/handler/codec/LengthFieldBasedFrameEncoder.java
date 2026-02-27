package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.channel.ChannelHandler;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelOutboundHandler;

import java.util.LinkedList;

/**
 * @author specdock
 * @Date 2026/2/26
 * @Time 14:42
 */
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("LengthFieldBasedFrameEncoder");
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, Object promise) {
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
