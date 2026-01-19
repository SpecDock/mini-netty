package io.github.specdock.mininetty.channel.socket.nio;

import io.github.specdock.mininetty.channel.ChannelHandler;
import io.github.specdock.mininetty.channel.EventLoop;
import io.github.specdock.mininetty.channel.EventLoopGroup;
import io.github.specdock.mininetty.channel.socket.ServerSocketChannel;
import io.github.specdock.mininetty.util.InterestOpsUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:17
 */
public class NioServerSocketChannel implements ServerSocketChannel {
    private final java.nio.channels.ServerSocketChannel ssc;

    private EventLoop eventLoop;

    private SelectionKey selectionKey;

    /// TODO 之后优化为netty的handler方法
    /// 现在使用构造器方法，每一个ServerChannel都维护一个EventLoopGroup，方便在boss线程里accept到的channel注册到workers
    private final EventLoopGroup workers;

    private ChannelHandler workerChannelInitializer;


    public NioServerSocketChannel(EventLoopGroup workers){
        this.workers = workers;
        try {
            ssc = java.nio.channels.ServerSocketChannel.open();
            ssc.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(this.getClass().getName() + "开启失败", e);
        }
    }

    @Override
    public void bind(SocketAddress local) {
        try {
            ssc.bind(local);
            System.out.println(local.toString() + "-------------success");
        } catch (IOException e) {
            throw new RuntimeException(this.getClass().getName() + "监听失败", e);
        }
    }

    @Override
    public void connect(SocketAddress remote) {

    }


    /**
     * 将此channel注册到selector
     * 实现channel与selectionKey双向持有
     *
     * @param selector
     * @param interestOps
     */
    @Override
    public void register(Selector selector, int interestOps) {
        try {
            this.selectionKey = ssc.register(selector, interestOps);
            this.selectionKey.attach(this);
            System.out.println(ssc.getLocalAddress().toString() + "---成功注册" + InterestOpsUtil.interestOpsToString(interestOps) + "事件到---" + Thread.currentThread().getName() + "的selector");
        } catch (Exception e) {
            throw new RuntimeException(this.getClass().getName() + "注册失败", e);
        }
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    /**
     * 提交注册任务给workers
     * 一定要提交给workers线程做，因为workers的execute方法会打断selector监听，这样操作selector注册的时候selector不会监听
     */
    @Override
    public void registerToWorkers(){
        NioSocketChannel accept = this.accept();
        if(accept == null){
            return;
        }
        workers.register(accept, SelectionKey.OP_READ);
    }

    public NioSocketChannel accept(){
        try {
            SocketChannel accept = ssc.accept();
            if(accept == null){
                System.out.println(this.getClass().getName() + ":accept，值为null");
                return null;
            }
            return new NioSocketChannel(accept);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setEventLoop(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public EventLoop getEventLoop() {
        return eventLoop;
    }

    @Override
    public void setWorkerChannelInitializer(ChannelHandler workerChannelInitializer) {
        this.workerChannelInitializer = workerChannelInitializer;
    }

    @Override
    public ChannelHandler getWorkerChannelInitializer() {
        return workerChannelInitializer;
    }
}
