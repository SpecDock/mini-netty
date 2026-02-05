package io.github.specdock.mininetty.channel;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:13
 */
public interface ChannelHandler {
    public void channelRegistered(ChannelHandlerContext ctx);

    public void channelRead(ChannelHandlerContext ctx, Object msg);
}
