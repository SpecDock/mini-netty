package io.github.specdock.mininetty.util.concurrent;

import java.util.EventListener;

/**
 * @author specdock
 * @Date 2026/2/27
 * @Time 16:04
 */

public interface GenericFutureListener {
    /**
     * 当异步操作完成时触发
     */
    void operationComplete(Future future) throws Exception;
}
