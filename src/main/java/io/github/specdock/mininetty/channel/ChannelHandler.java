package io.github.specdock.mininetty.channel;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:13
 */
public interface ChannelHandler {
     void channelRegistered(ChannelHandlerContext ctx);

    public void channelRead(ChannelHandlerContext ctx, Object msg);

    // TODO 还没构建好Future，构建好后将promise的类型改为ChannelPromise
    public void write(ChannelHandlerContext ctx, Object msg, Object promise);

    public void flush(ChannelHandlerContext ctx);

}
