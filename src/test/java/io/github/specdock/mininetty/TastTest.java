package io.github.specdock.mininetty;

import io.github.specdock.mininetty.bootstrap.ServerBootstrap;
import io.github.specdock.mininetty.channel.ChannelHandler;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelInitializer;
import io.github.specdock.mininetty.channel.SimpleChannelInboundHandler;
import io.github.specdock.mininetty.channel.nio.NioEventLoopGroup;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author specdock
 * @Date 2026/1/17
 * @Time 23:00
 */
public class TastTest {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup(3))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new ChannelHandler() {
                                    @Override
                                    public void channelRegistered(ChannelHandlerContext ctx) {

                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {

                                        ctx.fireChannelRead(msg);
                                    }
                                });
                    }

                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) {

                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        System.out.println("----------------------我是ChannelInitializer");
                        ctx.fireChannelRead(msg);
                    }
                })
                .bind(8080);
    }
}
