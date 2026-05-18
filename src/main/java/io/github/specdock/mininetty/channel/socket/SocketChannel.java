package io.github.specdock.mininetty.channel.socket;

import io.github.specdock.mininetty.channel.Channel;
import io.github.specdock.mininetty.channel.ChannelOutboundBuffer;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:10
 */
public interface SocketChannel extends Channel {

    SocketAddress getRemoveAddress();

    SocketAddress getLocalAddress();

    int write(ByteBuffer src);

    long write(ByteBuffer[] srcs);


    void finishConnect();
}
