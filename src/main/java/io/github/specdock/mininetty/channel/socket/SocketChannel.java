package io.github.specdock.mininetty.channel.socket;

import io.github.specdock.mininetty.channel.Channel;
import io.github.specdock.mininetty.channel.ChannelOutboundBuffer;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:10
 */
public interface SocketChannel extends Channel {

    SocketAddress getRemoveAddress();

    SocketAddress getLocalAddress();

    int write(java.nio.ByteBuffer src);

    void finishConnect();
}
