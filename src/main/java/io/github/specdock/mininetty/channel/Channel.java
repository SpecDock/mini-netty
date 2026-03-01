package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

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

    Future connect(SocketAddress remote, Promise promise);

    Future close();

    void register(Selector selector, int interestOps);

    void register(Selector selector, int interestOps, Promise promise);

    void unregister(int interestOps);

    SelectionKey getSelectionKey();

    void setEventLoop(EventLoop eventLoop);

    EventLoop getEventLoop();

    ChannelPipeline pipeline();

    int read(ByteBuffer msg);

    ChannelOutboundBuffer channelOutboundBuffer();

}
