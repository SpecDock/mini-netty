package io.github.specdock.mininetty.timer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 极简高性能无锁时间轮定时器 (全局静态版 - 毫秒精度重构)
 *
 * @author specdock
 */
public class HashedWheelTimer {

    // --- 全局静态单例配置 ---
    private static final HashedWheelTimer INSTANCE = new HashedWheelTimer(512, 1000);

    private final Bucket[] wheel;
    private final int mask;

    // 重构点 1：使用 tickMs 替代 tickNanos
    private final long tickMs;

    private final Queue<TimeoutTask> queue = new ConcurrentLinkedQueue<>();
    private final Thread worker;

    // 将 startTime 声明，但不立即赋值
    private final long startTime;

    private HashedWheelTimer(int ticksPerWheel, long tickDurationMs) {
        int size = 1;
        while (size < ticksPerWheel){
            size <<= 1;
        }
        this.mask = size - 1;

        this.wheel = new Bucket[size];
        for (int i = 0; i < size; i++) {
            wheel[i] = new Bucket();
        }

        this.tickMs = tickDurationMs;

        // ★ 核心修复：构造器内立刻对齐时钟并启动后台线程！
        // 确保 tick = 0 的瞬间，就是真实世界的 startTime。彻底消灭时间空洞！
        this.startTime = System.currentTimeMillis();
        this.worker = new Thread(this::runWorker, "MiniNetty-Timer-Worker");
        // 守护线程，不影响 JVM 退出
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public static TimeoutTask newTimeout(Runnable task, long lastTimeMs, long delayMs) {
        return INSTANCE.scheduleTask(task, lastTimeMs, delayMs);
    }

    // 调度方法现在变得极其干净
    private TimeoutTask scheduleTask(Runnable task, long lastTimeMs, long delayMs) {
        // 移除懒加载逻辑
        // if (started.compareAndSet(false, true)) { worker.start(); }

        long deadlineMs = lastTimeMs + delayMs - startTime;
        TimeoutTask timeoutTask = new TimeoutTask(task, deadlineMs, lastTimeMs);
        queue.offer(timeoutTask);
        return timeoutTask;
    }



    private void runWorker() {
        long tick = 0;
        while (!Thread.currentThread().isInterrupted()) {
            // 加在这里：每转 5 圈（5 秒），打印一次心跳
            if (tick % 5 == 0) {
                System.out.println("⏱️ 时间轮正在滴答作响，当前 Tick: " + tick);
            }

            // 重构点 5：时间轮指针推进计算采用毫秒
            long deadline = tickMs * (tick + 1);
            long sleepMs = deadline - (System.currentTimeMillis() - startTime);

            if (sleepMs > 0) {
                try {
                    // 重构点 6：Thread.sleep 原生支持毫秒，移除纳秒取模及除法运算
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    break;
                }
            }

            TimeoutTask task;
            while ((task = queue.poll()) != null) {
                if (task.cancelled) {
                    continue;
                }

                // 重构点 7：圈数与槽位计算基于毫秒
                long calculatedTicks = task.deadlineMs / tickMs;
                task.remainingRounds = (calculatedTicks - tick) / wheel.length;

                wheel[(int) (Math.max(calculatedTicks, tick) & mask)].add(task);
            }

            wheel[(int) (tick & mask)].expire();
            tick++;
        }
    }

    private static class Bucket {
        TimeoutTask head, tail;

        void add(TimeoutTask task) {
            if (head == null) {
                head = tail = task;
            }
            else {
                tail.next = task;
                task.prev = tail;
                tail = task;
            }
        }

        void expire() {
            TimeoutTask t = head;
            while (t != null) {
                TimeoutTask next = t.next;
                if (t.cancelled) {
                    remove(t);
                } else if (t.remainingRounds <= 0) {
                    remove(t);
                    try {
                        t.task.run();
                    } catch (Throwable ignore) {

                    }
                } else {
                    t.remainingRounds--;
                }
                t = next;
            }
        }

        void remove(TimeoutTask t) {
            if (t.prev != null) {
                t.prev.next = t.next;
            }
            else {
                head = t.next;
            }
            if (t.next != null) {
                t.next.prev = t.prev;
            }
            else {
                tail = t.prev;
            }
            t.prev = t.next = null;
        }
    }

    public static class TimeoutTask {
        Runnable task;
        long remainingRounds;

        // 重构点 8：变量名及 getter/setter 同步修改为 Ms
        long deadlineMs;
        long lastTimeMs;
        volatile boolean cancelled = false;
        TimeoutTask prev, next;

        TimeoutTask(Runnable task, long deadlineMs, long lastTimeMs) {
            this.task = task;
            this.deadlineMs = deadlineMs;
            this.lastTimeMs = lastTimeMs;
        }

        public void cancel() {
            if (!this.cancelled) {
                this.cancelled = true;
                // 2. 核心优化：主动置空 Runnable。
                // 彻底切断时间轮对外部业务对象（Channel、Context）的强引用链。
                // 这样即使 TimeoutTask 对象还要在时间轮槽位里待上 60 秒，
                // 那些占用大量内存的 Channel 与缓冲资源也能立刻被 GC 回收。
                this.task = null;
            }
        }

        public Runnable getTask(){
            return task;
        }

        public long getLastTimeMs() {
            return lastTimeMs;
        }

        public void setLastTimeMs(long lastTimeMs) {
            this.lastTimeMs = lastTimeMs;
        }
    }
}