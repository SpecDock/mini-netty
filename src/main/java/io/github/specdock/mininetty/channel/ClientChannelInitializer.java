package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.channel.handler.timeout.ClientHeartbeatHandler;
import io.github.specdock.mininetty.channel.handler.timeout.IdleStateHandler;
import io.github.specdock.mininetty.channel.handler.timeout.ServerHeartbeatHandler;
import io.github.specdock.mininetty.util.HeartbeatConstant;

/**
 * @author specdock
 * @Date 2026/3/13
 * @Time 20:46
 */
public abstract class ClientChannelInitializer<C extends Channel> extends ChannelInitializer<C>{
    @Override
    protected void preInit(C ch) throws Exception {
        ch.pipeline().addLast(new IdleStateHandler(HeartbeatConstant.HEARTBEAT_INTERVAL_MS));
    }

    @Override
    protected void postInit(C ch) throws Exception {
        ch.pipeline().addAfter(FrameCodec.class, new ClientHeartbeatHandler());
    }
}
