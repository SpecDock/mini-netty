package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.ByteBufChain;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:11
 */
public interface Channel {
    void bind(SocketAddress local);

    void connect(SocketAddress remote);

    void register(Selector selector, int interestOps);

    SelectionKey getSelectionKey();

    void setEventLoop(EventLoop eventLoop);

    EventLoop getEventLoop();

    ChannelPipeline pipeline();

    int read(ByteBuffer msg);
}
