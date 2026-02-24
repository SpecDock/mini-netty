package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/18
 * @Time 20:15
 */
public class DefaultChannelHandlerContext extends AbstractChannelHandlerContext{

    public DefaultChannelHandlerContext(ChannelHandler handler, ChannelPipeline pipeline){
        super(handler, pipeline);
    }

    // ---------------------------------- 组件获取
    @Override
    public ChannelHandler handler() {
        return handler;
    }


}
