package io.github.specdock.mininetty.bootstrap;


import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.socket.ServerSocketChannel;
import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:38
 */
public class ServerBootstrap {
    private EventLoopGroup boss;
    private EventLoopGroup workers;

    // boss group的监听套接字
    private InetSocketAddress inetSocketAddress;

    // boss 的 单个EventLoop 里的Channel类
    private Class<? extends ServerChannel> serverChannelClass;

    // boss里的CHannel的Handler
    private ChannelHandler handler;

    // workers里每个Channel的初始Handler
    private ChannelHandler childHandler;

    public ServerBootstrap(){

    }

    public ServerBootstrap group(EventLoopGroup boss, EventLoopGroup workers){
        this.boss = boss;
        this.workers = workers;
        return this;
    }

    public ServerBootstrap channel(Class<? extends ServerChannel> channelClass){
        serverChannelClass = channelClass;
        return this;
    }

    public ServerBootstrap handler(ChannelHandler handler){
        this.handler = handler;
        return this;
    }

    public ServerBootstrap childHandler(ChannelHandler childHandler){
        this.childHandler = childHandler;
        return this;
    }


    /**
     * 提交bind任务给boss线程
     * 一定不能主线程来做，因为可能selector在boss线程还在监听，主线程再操作selector会发生异常
     *
     * @param hostname
     * @param port
     */
    public void bind(String hostname, int port){
        try{
            inetSocketAddress = new InetSocketAddress(hostname, port);
            // 加载 ServerChannel 实例
            ServerChannel serverChannel = serverChannelClass.getDeclaredConstructor(EventLoopGroup.class).newInstance(workers);
            //serverChannel监听套接字
            serverChannel.bind(inetSocketAddress);
            //将serverChannel注册到selector
            boss.register(serverChannel, SelectionKey.OP_ACCEPT);
            //初始话ServerBootstrapAcceptor
            //将给worker添加childHandler的Handler添加到serverChannel的pipeline
            ServerBootstrapAcceptor serverBootstrapAcceptor = new ServerBootstrapAcceptor(workers, childHandler);
            serverChannel.pipeline().addLast(serverBootstrapAcceptor);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void bind(int port){
        bind("127.0.0.1", port);
    }

    // 在 ServerBootstrap.java 内部
    private static class ServerBootstrapAcceptor implements ChannelInboundHandler {

        private final EventLoopGroup workers;
        private final ChannelHandler childHandler;

        // 构造函数：在 bind() 时被调用，传入用户配置的参数
        ServerBootstrapAcceptor(EventLoopGroup workers, ChannelHandler childHandler) {
            this.workers = workers;
            this.childHandler = childHandler;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 1. 类型转换：对于 ServerChannel 来说，读取到的 msg 就是新连接
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) msg;
            SocketChannel socketChannel = serverSocketChannel.accept();

            // 2. ★ 核心动作：将用户配置的 childHandler 加入新连接的 Pipeline
            // 注意：这里不需要管它是 Initializer 还是普通 Handler，直接 addLast 即可
            socketChannel.pipeline().addLast(childHandler);

            // 3. 注册：将新连接移交给 Worker 线程组
            // 这一步会触发 worker 的 channelRegistered 事件
            workers.register(socketChannel, SelectionKey.OP_ACCEPT);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.fireChannelRegistered();
        }
    }

}
