package io.github.specdock.mininetty.util.concurrent;

import io.github.specdock.mininetty.channel.Channel;

/**
 * @author specdock
 * @Date 2026/2/27
 * @Time 16:04
 */

public interface Future {

    // 查询状态
    boolean isSuccess();
    boolean isDone();
    Throwable cause();

    // 注册回调机制
    Future addListener(GenericFutureListener listener);

    // 阻塞等待
    Future sync() throws InterruptedException;

    Channel channel();
}
