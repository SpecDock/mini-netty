package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.timer.HashedWheelTimer;
import io.github.specdock.mininetty.util.HeartbeatConstant;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author specdock
 * @Date 2026/3/10
 * @Time 19:45
 */
public class HeartbeatHandler implements ChannelHandler {
    private HashedWheelTimer.TimeoutTask timeoutTask;

    // 缓存 Runnable 实例，避免每次 getTask() 可能引发的 null 传递
    private Runnable heartbeatCheckTask;


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        EventLoop eventLoop = channel.getEventLoop();

        // 1. 初始化检查任务，消除闭包重复创建的开销
        this.heartbeatCheckTask = () -> {
            eventLoop.execute(() -> {
                if (!channel.isOpen()) {
                    return;
                }
                if (timeoutTask.getLastTimeMs() + HeartbeatConstant.HEARTBEAT_TIMEOUT_MS < System.currentTimeMillis()) {
                    channel.close();
                    return;
                }
                // 使用缓存的 Runnable 进行下一次调度
                timeoutTask = HashedWheelTimer.newTimeout(
                        heartbeatCheckTask,
                        timeoutTask.getLastTimeMs(),
                        HeartbeatConstant.HEARTBEAT_TIMEOUT_MS
                );
            });
        };

        // 2. 发起首次调度
        timeoutTask = HashedWheelTimer.newTimeout(
                heartbeatCheckTask,
                System.currentTimeMillis(),
                HeartbeatConstant.HEARTBEAT_TIMEOUT_MS
        );

        ctx.fireChannelActive();
    }

    /**
     * 3. 【核心补全】连接断开时的资源清理钩子
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 触发底层的 task = null，彻底释放 Channel 等大内存对象
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (timeoutTask != null) {
            timeoutTask.setLastTimeMs(System.currentTimeMillis());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        return ctx.write(msg, promise);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}