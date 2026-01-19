package io.github.specdock.mininetty.channel;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:17
 */
public interface ServerChannel extends Channel{
    public void registerToWorkers();

    public void setWorkerChannelInitializer(ChannelHandler workerChannelInitializer);

    public ChannelHandler getWorkerChannelInitializer();
}
