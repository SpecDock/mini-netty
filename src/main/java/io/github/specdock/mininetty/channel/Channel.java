package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;
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
}
