package io.github.specdock.mininetty.channel.handler.timeout;

import io.github.specdock.mininetty.channel.ChannelHandler;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.DefaultChannelPromise;
import io.github.specdock.mininetty.timer.HashedWheelTimer;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author 29287
 * @Date 2026/3/12
 * @Time 19:49
 */
public class IdleStateHandler implements ChannelHandler {

    private final long readerIdleTimeMs;
    private long lastReadTimeMs;
    private HashedWheelTimer.TimeoutTask timeoutTask;
    private Runnable idleCheckTask;

    // 定义标准的空闲事件常量（避免创建大量事件对象引发 GC）
    public static final Object READER_IDLE_STATE_EVENT = new Object();

    public IdleStateHandler(long readerIdleTimeMs) {
        this.readerIdleTimeMs = readerIdleTimeMs;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        System.out.println("IdleStateHandler:channelRegistered");
        this.lastReadTimeMs = System.currentTimeMillis();

        this.idleCheckTask = () -> {
            System.out.println("IdleStateHandler.idleCheckTask 成功触发");
            ctx.channel().getEventLoop().execute(() -> {
                if (!ctx.channel().isOpen()) {
                    return;
                }

                long currentTime = System.currentTimeMillis();
                long idleTime = currentTime - lastReadTimeMs;

                if (idleTime >= readerIdleTimeMs) {
                    // 核心解耦：只触发事件，不执行具体的断开或发送逻辑
                    ctx.fireUserEventTriggered(READER_IDLE_STATE_EVENT);

                    // 触发事件后，继续下一轮调度（由具体策略决定是否断开）
                    scheduleNextCheck(ctx, readerIdleTimeMs);
                } else {
                    scheduleNextCheck(ctx, readerIdleTimeMs - idleTime);
                }
            });
        };

        scheduleNextCheck(ctx, readerIdleTimeMs);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    private void scheduleNextCheck(ChannelHandlerContext ctx, long delayMs) {
        this.timeoutTask = HashedWheelTimer.newTimeout(idleCheckTask, System.currentTimeMillis(), delayMs);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        this.lastReadTimeMs = System.currentTimeMillis();
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        ctx.fireUserEventTriggered(event);
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