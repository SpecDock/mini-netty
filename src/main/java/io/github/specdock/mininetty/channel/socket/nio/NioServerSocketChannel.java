package io.github.specdock.mininetty.channel.socket.nio;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.socket.ServerSocketChannel;
import io.github.specdock.mininetty.util.InterestOpsUtil;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
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

    private ChannelPipeline pipeline;

    /// 已弃用
    /// 现在使用构造器方法，每一个ServerChannel都维护一个EventLoopGroup，方便在boss线程里accept到的channel注册到workers
//    private final EventLoopGroup workers;

    private ChannelHandler workerChannelInitializer;


    public NioServerSocketChannel(){
        try {
            ssc = java.nio.channels.ServerSocketChannel.open();
            ssc.configureBlocking(false);
            pipeline = new DefaultChannelPipeline(this);
        } catch (IOException e) {
            throw new RuntimeException(this.getClass().getName() + "开启失败", e);
        }
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
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
    public Future connect(SocketAddress remote, Promise promise) {
            return null;
    }


    @Override
    public boolean isOpen(){
        return ssc.isOpen();
    }

    @Override
    public Future close() {
        Promise promise = new DefaultChannelPromise();
        if(eventLoop.inEventLoop()){
            doClose(promise);
        }
        else {
            eventLoop.execute(() -> doClose(promise));
        }
        return promise;
    }

    private void doClose(Promise promise) {
        try {
            // 1. 幂等性校验：若已关闭则直接返回成功
            if (!ssc.isOpen()) {
                promise.setSuccess();
                return;
            }

            // 2. 取消多路复用器的注册关系
            if (selectionKey != null) {
                selectionKey.cancel();
            }

            // 3. 执行物理关闭（释放文件描述符）
            ssc.close();

            // 4. 核销 Promise 凭证
            promise.setSuccess();

        } catch (IOException e) {
            // 物理关闭异常反馈
            promise.setFailure(e);
        }
    }




    @Override
    public void register(Selector selector, int interestOps) {
        Promise promise = new DefaultChannelPromise();
        register(selector, interestOps, promise);
    }

    /**
     * 将此channel注册到selector
     * 实现channel与selectionKey双向持有
     *
     * @param selector
     * @param interestOps
     */
    @Override
    public void register(Selector selector, int interestOps, Promise promise) {
        try {
            this.selectionKey = ssc.register(selector, interestOps);
            selectionKey.attach(this);
            promise.setSuccess();
            System.out.println(ssc.getLocalAddress().toString() + "---成功注册" + InterestOpsUtil.interestOpsToString(interestOps) + "事件到---" + Thread.currentThread().getName() + "的selector");
        } catch (Exception e) {
            throw new RuntimeException(ssc.getClass().getName() + "注册失败", e);
        }
    }

    @Override
    public void unregister(int interestOps){
        if(!selectionKey.isValid()){
            return;
        }
        try {
            selectionKey.interestOps(selectionKey.interestOps() & ~interestOps);
            System.out.println(ssc.getLocalAddress().toString() + "---已注销" + InterestOpsUtil.interestOpsToString(interestOps) + "事件");
        } catch (Exception e) {
            throw new RuntimeException(ssc.getClass().getName() + "注销失败", e);
        }
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    /**
     * 提交注册任务给workers
     * 一定要提交给workers线程做，因为workers的execute方法会打断selector监听，这样操作selector注册的时候selector不会监听
     * 已经弃用
     */
//    @Override
//    public void registerToWorkers(){
//        NioSocketChannel accept = this.accept();
//        if(accept == null){
//            return;
//        }
//        workers.register(accept, SelectionKey.OP_READ);
//    }

    @Override
    public io.github.specdock.mininetty.channel.socket.SocketChannel accept(){
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
    public int read(ByteBuffer msg) {
        return 0;
    }

    @Override
    public ChannelOutboundBuffer channelOutboundBuffer() {
        return null;
    }
}
