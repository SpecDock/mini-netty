package io.github.specdock.mininetty.util.concurrent;

import io.github.specdock.mininetty.channel.EventLoop;

/**
 * @author specdock
 * @Date 2026/1/16
 * @Time 11:44
 */
public class ScheduleTask implements Runnable, Comparable<ScheduleTask>{
    private final Runnable runnable;

    private long deadLine;

    private long period;

    private EventLoop eventLoop;


    /**
     *
     *
     * @param runnable
     * @param eventLoop
     * @param deadLine
     * @param period 周期大于 0 时，才会被再次添加到到定时任务队列
     */
    public ScheduleTask(Runnable runnable, EventLoop eventLoop, long deadLine, long period){
        this.runnable = runnable;
        this.eventLoop = eventLoop;
        this.deadLine = deadLine;
        this.period = period;
    }

    @Override
    public int compareTo(ScheduleTask o) {
        return Long.compare(this.deadLine, o.deadLine);
    }

    @Override
    public void run() {
        try {
            runnable.run();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(period > 0){
                this.deadLine += period;
                eventLoop.getScheduleTaskQueue().offer(this);
            }
        }
    }

    public long getDeadLine() {
        return deadLine;
    }
}
