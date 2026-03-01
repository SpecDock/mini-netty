package io.github.specdock.mininetty.util.concurrent;

/**
 * @author specdock
 * @Date 2026/2/27
 * @Time 16:03
 */


import io.github.specdock.mininetty.channel.Channel;

/**
 * 极简版 Promise（可写视图）
 */
public interface Promise extends Future {

    // 标记成功，触发 listener
    Promise setSuccess();

    // 标记失败，触发 listener
    Promise setFailure(Throwable cause);

    void setChannel(Channel channel);
}
