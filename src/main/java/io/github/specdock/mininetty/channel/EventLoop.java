package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.ScheduleTask;

import java.util.Queue;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:28
 */
public interface EventLoop extends EventLoopGroup{
    Queue<ScheduleTask> getScheduleTaskQueue();
}
