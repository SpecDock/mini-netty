package io.github.specdock.mininetty.channel.handler.timeout;

import io.github.specdock.mininetty.buffer.SimpleByteArray;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author specdock
 * @Date 2026/3/12
 * @Time 20:00
 *
 * 客户端心跳策略拦截器
 * 职责：主动发送心跳探测包，并静默处理服务端的探活回执
 */
public class ClientHeartbeatHandler implements ChannelInboundHandler, ChannelOutboundHandler {

    // 预分配复用的 Ping 字节帧，消除探测带来的 GC 开销
    private static final byte[] PING_FRAME = new byte[]{1};
    private static final byte[] PAYLOAD_HEADER = new byte[]{0};

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("ClientHeartbeatHandler");
        SimpleByteArray frameData = (SimpleByteArray) msg;

        if (frameData == null || frameData.end - frameData.begin == 0) {
            return;
        }

        byte frameType = frameData.bytes[0];

        if (frameType == 2) {
            // 拦截到服务端的 Pong 探活回执：静默消费
            System.out.println("Pong");
            return;
        } else if (frameType == 0) {
            // 拦截到业务数据帧：执行协议头剥离与负载透传
            if (frameData.end - frameData.begin > 1) {
                frameData.begin++;
                ctx.fireChannelRead(frameData);
            }
            return;
        }

        // 针对非法协议类型执行防御性丢弃
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        System.out.println("ClientHeartbeatHandler:userEventTriggered");
        if (event == IdleStateHandler.READER_IDLE_STATE_EVENT) {
            // 触发空闲判定：客户端主动发射探测帧
            Promise promise = new DefaultChannelPromise();
            ctx.writeAndFlush(PING_FRAME, promise);
            return;
        }
        ctx.fireUserEventTriggered(event);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg, Promise promise) {
        // 出站数据拦截拓展：
        // 若业务层写入的是序列化好的纯业务负载，理论上应在此处（或专门的 Encoder 中）补齐前置的 0x00 协议头。
        byte[] payload = (byte[]) msg;
        byte[] target = new byte[payload.length + 1];
        System.arraycopy(PAYLOAD_HEADER, 0, target, 0, PAYLOAD_HEADER.length);
        System.arraycopy(payload, 0, target, 1, payload.length);
        return ctx.write(target, promise);
    }

    @Override
    public Future write(ChannelHandlerContext ctx, Object msg) {
        Promise promise = new DefaultChannelPromise();
        return write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
