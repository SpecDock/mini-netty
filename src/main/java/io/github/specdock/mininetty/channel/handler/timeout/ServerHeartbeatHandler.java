package io.github.specdock.mininetty.channel.handler.timeout;

import io.github.specdock.mininetty.buffer.SimpleByteArray;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;

/**
 * @author specdock
 * @Date 2026/3/12
 * @Time 19:58
 *
 * 服务端心跳策略拦截器
 * 职责：被动响应客户端心跳探测，并在连接超时时执行资源剔除
 */
public class ServerHeartbeatHandler implements ChannelInboundHandler, ChannelOutboundHandler {

    // 预分配复用的 Pong 字节帧，避免高并发下频繁引发小对象分配
    private static final byte[] PONG_FRAME = new byte[]{2};
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
        System.out.println("ServerHeartbeatHandler");
        // 强转为底层字节数组 (需确保前置 FrameDecoder 交付的必为 byte[])
        SimpleByteArray frameData = (SimpleByteArray) msg;

        // 防御性编程：规避空帧引发的越界异常
        if (frameData == null || frameData.end - frameData.begin == 0) {
            return;
        }

        byte frameType = frameData.bytes[0];

        if (frameType == 1) {
            // 拦截到 Ping 控制帧：被动响应 Pong
            System.out.println("ping");
            Promise promise = new DefaultChannelPromise();
            ctx.writeAndFlush(PONG_FRAME, promise);
            // 消费该帧，阻断向后传播
            return;
        } else if (frameType == 0) {
            // 拦截到业务数据帧：执行协议头剥离与负载透传
            if (frameData.end - frameData.begin > 1) {
                frameData.begin++;
                ctx.fireChannelRead(frameData);
            }
            return;
        }

        // 针对未定义的协议类型（或 frameType == 2 的非法客户端回执），可记录日志或丢弃
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        System.out.println("ClientHeartbeatHandler:userEventTriggered");
        if (event == IdleStateHandler.READER_IDLE_STATE_EVENT) {
            // 触发读空闲阈值：执行防御性资源剔除
            System.err.println("心跳超时，Channel自动关闭");
            ctx.channel().close();
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
