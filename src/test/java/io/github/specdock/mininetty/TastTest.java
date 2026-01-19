package io.github.specdock.mininetty;

import io.github.specdock.mininetty.bootstrap.ServerBootstrap;
import io.github.specdock.mininetty.channel.nio.NioEventLoopGroup;
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
                                .bind(8080);
    }
}
