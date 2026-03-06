package io.github.specdock.mininetty;

import io.github.specdock.mininetty.bootstrap.Bootstrap;
import io.github.specdock.mininetty.channel.ChannelHandlerContext;
import io.github.specdock.mininetty.channel.ChannelInitializer;
import io.github.specdock.mininetty.channel.EventLoop;
import io.github.specdock.mininetty.channel.SimpleChannelInboundHandler;
import io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameDecoder;
import io.github.specdock.mininetty.channel.handler.codec.LengthFieldBasedFrameEncoder;
import io.github.specdock.mininetty.channel.handler.codec.StringDecoder;
import io.github.specdock.mininetty.channel.handler.codec.StringEncoder;
import io.github.specdock.mininetty.channel.nio.NioEventLoopGroup;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.channel.socket.nio.NioSocketChannel;
import io.github.specdock.mininetty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author specdock
 * @Date 2026/2/28
 * @Time 15:00
 */
public class BootstrapTest {
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
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
                                        System.out.println(msg);
                                        Future close = ctx.channel().close();
                                        close.addListener(f -> {
                                            if(f.isSuccess()){
                                                System.out.println("客户端TCP连接已经关闭");
                                            }
                                        });
                                    }
                                });
                    }
                });

        Future connect = bootstrap.connect(new InetSocketAddress("127.0.0.1", 8080));
        connect.addListener(future -> {
            if(future.isSuccess()){
                System.out.println("成功连接");
                EventLoop eventExecutors = connect.channel().getEventLoop();
                eventExecutors.scheduleAtFixedRate(() -> {
                    System.out.println(">> 定时任务已触发，准备将数据压入 Pipeline");
                    connect.channel().pipeline().writeAndFlush("你好，我是客户端");
                }, 0, 10, TimeUnit.MILLISECONDS);
            }
            else {
                System.out.println("连接失败");
            }
        });
    }
}
