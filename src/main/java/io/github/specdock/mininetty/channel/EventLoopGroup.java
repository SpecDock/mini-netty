package io.github.specdock.mininetty.channel;

import java.util.concurrent.TimeUnit;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:21
 */
public interface EventLoopGroup {
    void execute(Runnable task);

    void shedule(Runnable task, long delay, TimeUnit unit);

    void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

    EventLoop next();


    void register(Channel channel, int interestOps);
}
