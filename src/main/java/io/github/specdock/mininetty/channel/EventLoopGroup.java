package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

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


    Future register(Channel channel, int interestOps);

    Future register(Channel channel, int interestOps, Promise promise);
}
