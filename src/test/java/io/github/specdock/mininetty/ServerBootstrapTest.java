package io.github.specdock.mininetty;

import io.github.specdock.mininetty.bootstrap.ServerBootstrap;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameDecoder;
import io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameEncoder;
import io.github.specdock.mininetty.channel.handler.codec.StringDecoder;
import io.github.specdock.mininetty.channel.handler.codec.StringEncoder;
import io.github.specdock.mininetty.channel.nio.NioEventLoopGroup;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author specdock
 * @Date 2026/1/17
 * @Time 23:00
 */
public class ServerBootstrapTest {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup(3))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ServerChannelInitializer<SocketChannel>(){

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder())
                                .addLast(new LengthFieldBasedFrameEncoder())
                                .addLast(new StringDecoder())
                                .addLast(new StringEncoder())
                                .addLast(new SimpleChannelInboundHandler() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        String s = (String) msg;
                                        if(s != null && !s.isEmpty()){
                                            System.out.println("这里是Server端接受到的消息：" + s);
                                        }
                                        ctx.pipeline().writeAndFlush("ACK");
                                        ctx.fireChannelRead(msg);
                                    }
                                });
                    }
                })
                .bind(8080);
    }
}
