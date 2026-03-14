package io.github.specdock.mininetty.channel.socket.nio;

import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.util.InterestOpsUtil;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

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

    private EventLoop eventLoop;

    private SelectionKey selectionKey;

    private final ChannelPipeline pipeline;

    private final ChannelOutboundBuffer channelOutboundBuffer;

    private Promise connectPromise;




    public NioSocketChannel(java.nio.channels.SocketChannel socketChannel){
        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(this.getClass().getName() + ":NioSocketChannel，设置为非阻塞时失败", e);
        }
        this.socketChannel = socketChannel;
        pipeline = new DefaultChannelPipeline(this);
        channelOutboundBuffer = new ChannelOutboundBuffer(this);
    }

    public NioSocketChannel(){
        try {
            java.nio.channels.SocketChannel socketChannel = java.nio.channels.SocketChannel.open();
            socketChannel.configureBlocking(false);
            this.socketChannel = socketChannel;
            pipeline = new DefaultChannelPipeline(this);
            channelOutboundBuffer = new ChannelOutboundBuffer(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isActive() {
        return socketChannel != null
                && socketChannel.isOpen()
                && socketChannel.socket().isBound();
    }

    @Override
    public boolean isRegistered() {
        return selectionKey != null;
    }

    @Override
    public void bind(SocketAddress local) {

    }

    @Override
    public Future connect(SocketAddress remote, Promise promise) {
        if(eventLoop.inEventLoop()){
            doConnect(remote, promise);
        }
        else {
            eventLoop.execute(() -> doConnect(remote, promise));
        }
        return promise;
    }

    private void doConnect(SocketAddress remote, Promise promise){
        try {
            boolean connected = socketChannel.connect(remote);
            if(connected){
                promise.setSuccess();
            }
            else {
                // 3. 挂起状态：向 Selector 注册 OP_CONNECT 意向
                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_CONNECT);
                connectPromise = promise;
                // 注意：此时切勿调用 setFailure。Promise 将被暂存（建议绑定在 Channel 属性中），
                // 等待 EventLoop 监听到 OP_CONNECT 就绪事件后再做核销
            }
        } catch (IOException e) {
            promise.setFailure(e);
        }
    }

    @Override
    public void finishConnect() {
        try {
            // 1. 物理状态确认 (必须执行，否则 Channel 处于中间态，无法进行读写)
            if (socketChannel.finishConnect()) {

                // 2. 清除 OP_CONNECT 兴趣位 (防止 Selector 持续触发),切换至读就绪意向 (通常连接成功后立即准备接收数据)
                int ops = selectionKey.interestOps();
                selectionKey.interestOps((ops & ~SelectionKey.OP_CONNECT) | SelectionKey.OP_READ);

                // 3. 核销异步凭证
                connectPromise.setSuccess();

                pipeline().fireChannelActive();
            }
        } catch (IOException e) {
            // 6. 捕获握手阶段的物理失败 (如 Connection Refused)
            connectPromise.setFailure(e);
            // 关闭损坏的通道
            this.close();
        }
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

    @Override
    public boolean isOpen(){
        return socketChannel.isOpen();
    }

    private void doClose(Promise promise) {
        try {
            pipeline().fireChannelInactive();
            // 1. 幂等性校验：若已关闭则直接返回成功
            if (!socketChannel.isOpen()) {
                promise.setSuccess();
                return;
            }

            // 2. 取消多路复用器的注册关系
            if (selectionKey != null) {
                selectionKey.cancel();
            }

            // 3. 执行物理关闭（释放文件描述符）
            socketChannel.close();

            // 4. 核销 Promise 凭证
            promise.setSuccess();

            System.out.println("NioSocketChannel:doClose," + "已经执行fireChannelInactive并关闭Channel");

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

    @Override
    public void register(Selector selector, int interestOps, Promise promise) {
        try {
            if(selectionKey == null){
                this.selectionKey = socketChannel.register(selector, interestOps);
                selectionKey.attach(this);
            }
            else {
                int currentOps = selectionKey.interestOps();
                // 采用按位或（|）运算，确保在保留原有监听事件的基础上，叠加新的 interestOps
                int newOps = currentOps | interestOps;

                // 仅在事件位确实发生变化时才调用底层同步方法，优化系统调用开销
                if (currentOps != newOps) {
                    selectionKey.interestOps(newOps);
                }
            }
            promise.setSuccess();
            System.out.println("成功注册" + InterestOpsUtil.interestOpsToString(interestOps) + "事件到---" + Thread.currentThread().getName() + "的selector");
        } catch (Exception e) {
            throw new RuntimeException(socketChannel.getClass().getName() + "注册失败", e);
        }
    }

    @Override
    public void unregister(int interestOps){
        if(!selectionKey.isValid()){
            return;
        }
        try {
            selectionKey.interestOps(selectionKey.interestOps() & ~interestOps);
            System.out.println(socketChannel.getRemoteAddress().toString() + "---已注销" + InterestOpsUtil.interestOpsToString(interestOps) + "事件");
        } catch (Exception e) {
            throw new RuntimeException(socketChannel.getClass().getName() + "注销失败", e);
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

    @Override
    public ChannelPipeline pipeline(){
        return pipeline;
    }

    @Override
    public int read(java.nio.ByteBuffer msg){
        try {
            return socketChannel.read(msg);
        } catch (IOException e) {
            throw new RuntimeException("java.nio.channels.SocketChannel的read方法出现异常:" + e.getMessage());
        }
    }

    @Override
    public SocketAddress getRemoveAddress(){
        try {
            return socketChannel.getRemoteAddress();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SocketAddress getLocalAddress() {
        try {
            return socketChannel.getLocalAddress();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ChannelOutboundBuffer channelOutboundBuffer() {
        return channelOutboundBuffer;
    }

    @Override
    public int write(java.nio.ByteBuffer src){
        try {
            return socketChannel.write(src);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}




















