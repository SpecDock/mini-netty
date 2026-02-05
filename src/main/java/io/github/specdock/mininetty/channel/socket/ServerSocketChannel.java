package io.github.specdock.mininetty.channel.socket;

import io.github.specdock.mininetty.channel.ServerChannel;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:17
 */
public interface ServerSocketChannel extends ServerChannel {
    SocketChannel accept();
}
