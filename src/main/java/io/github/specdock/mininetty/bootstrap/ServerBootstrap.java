package io.github.specdock.mininetty.bootstrap;


import io.github.specdock.mininetty.channel.Channel;
import io.github.specdock.mininetty.channel.ChannelHandler;
import io.github.specdock.mininetty.channel.EventLoopGroup;
import io.github.specdock.mininetty.channel.ServerChannel;

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

    public ServerBootstrap childHandler(ChannelHandler channelHandler){
        this.childHandler = channelHandler;
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void bind(int port){
        bind("127.0.0.1", port);
    }



}
