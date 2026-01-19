package io.github.specdock.mininetty.channel.socket.nio;

import io.github.specdock.mininetty.channel.ChannelPipeline;
import io.github.specdock.mininetty.channel.DefaultChannelPipeline;
import io.github.specdock.mininetty.channel.EventLoop;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.util.InterestOpsUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:20
 */
public class NioSocketChannel implements SocketChannel {
    private final java.nio.channels.SocketChannel socketChannel;
    // 内部要维护一个eventLoop的引用，方便write方法调用
    private EventLoop eventLoop;

    private SelectionKey selectionKey;

    private final ChannelPipeline pipeline;

    // TODO 储存上下文的变量，应该用 LinkedList<ByteBuffer> 链式储存



    public NioSocketChannel(java.nio.channels.SocketChannel socketChannel){
        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(this.getClass().getName() + ":NioSocketChannel，设置为非阻塞时失败", e);
        }
        this.socketChannel = socketChannel;
        pipeline = new DefaultChannelPipeline(this);
    }

    @Override
    public void bind(SocketAddress local) {

    }

    @Override
    public void connect(SocketAddress remote) {

    }

    @Override
    public void register(Selector selector, int interestOps) {
        try {
            this.selectionKey = socketChannel.register(selector, interestOps);
            selectionKey.attach(this);
            System.out.println(socketChannel.getRemoteAddress().toString() + "---成功注册" + InterestOpsUtil.interestOpsToString(interestOps) + "事件到---" + Thread.currentThread().getName() + "的selector");
        } catch (Exception e) {
            throw new RuntimeException(socketChannel.getClass().getName() + "注册失败", e);
        }
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    @Override
    public void setEventLoop(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public EventLoop getEventLoop() {
        return eventLoop;
    }

    public ChannelPipeline pipeline(){
        return pipeline;
    }
}
