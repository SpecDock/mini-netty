package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:13
 */
public interface ChannelHandler {
     void channelRegistered(ChannelHandlerContext ctx);

    void channelActive(ChannelHandlerContext ctx);

    void channelInactive(ChannelHandlerContext ctx);

    public void channelRead(ChannelHandlerContext ctx, Object msg);


    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise);

    public Future write(ChannelHandlerContext ctx, Object msg);

    public void flush(ChannelHandlerContext ctx);

}