package io.github.specdock.mininetty.channel;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 16:44
 */
public abstract class ChannelInitializer<C extends Channel> implements ChannelHandler {

    protected abstract void initChannel(C ch) throws Exception;
}
