package io.github.specdock.mininetty.channel.handler.codec;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.buffer.PooledByteBufAllocator;
import io.github.specdock.mininetty.channel.*;
import io.github.specdock.mininetty.channel.handler.timeout.ClientHeartbeatHandler;
import io.github.specdock.mininetty.channel.handler.timeout.ServerHeartbeatHandler;
import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.Promise;
import io.github.specdock.mininetty.util.concurrent.ScheduleTask;
import org.junit.Test;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CodecAndHeartbeatChainTest {

    @Test
    public void stringEncoderWritesUtf8DirectlyIntoByteBufChain() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(4);
        CapturingContext ctx = new CapturingContext(allocator);

        new StringEncoder().write(ctx, "hello世界", new DefaultChannelPromise());

        assertTrue(ctx.written instanceof ByteBufChain);
        ByteBufChain chain = (ByteBufChain) ctx.written;
        assertSame(allocator, chain.allocator());
        assertTrue("encoded content should span fixed chunks", chain.nioBuffers(16).length > 1);
        byte[] bytes = new byte[chain.readableBytes()];
        chain.read(bytes, 0, bytes.length);
        assertArrayEquals("hello世界".getBytes(StandardCharsets.UTF_8), bytes);
        chain.release();
    }

    @Test
    public void stringDecoderReadsByteBufChainAndReleasesIt() {
        CapturingContext ctx = new CapturingContext(new PooledByteBufAllocator(4));
        ByteBufChain chain = new ByteBufChain(true, ctx.executor().allocator());
        byte[] bytes = "chain解码".getBytes(StandardCharsets.UTF_8);
        chain.writeBytes(bytes, 0, bytes.length);

        new StringDecoder().channelRead(ctx, chain);

        assertEquals("chain解码", ctx.firedRead);
        assertEquals(0, chain.refCnt());
    }

    @Test
    public void clientHeartbeatPassesByteBufChainDataFrames() {
        CapturingContext ctx = new CapturingContext(new PooledByteBufAllocator(4));
        ByteBufChain frame = new ByteBufChain(true, ctx.executor().allocator());
        frame.writeByte(0);
        frame.writeBytes(new byte[]{10, 11}, 0, 2);

        new ClientHeartbeatHandler().channelRead(ctx, frame);

        assertSame(frame, ctx.firedRead);
        assertEquals(2, frame.readableBytes());
        assertEquals(10, frame.readByte());
        assertEquals(11, frame.readByte());
        frame.release();
    }

    @Test
    public void serverHeartbeatRespondsToByteBufChainPingWithChainPong() {
        CapturingContext ctx = new CapturingContext(new PooledByteBufAllocator(4));
        ByteBufChain ping = new ByteBufChain(true, ctx.executor().allocator());
        ping.writeByte(1);

        new ServerHeartbeatHandler().channelRead(ctx, ping);

        assertEquals(0, ping.refCnt());
        assertTrue(ctx.writeAndFlushed instanceof ByteBufChain);
        ByteBufChain pong = (ByteBufChain) ctx.writeAndFlushed;
        assertEquals(1, pong.readableBytes());
        assertEquals(2, pong.readByte());
        pong.release();
    }

    private static final class CapturingContext implements ChannelHandlerContext {
        private final EventLoop eventLoop;
        Object written;
        Object writeAndFlushed;
        Object firedRead;

        CapturingContext(PooledByteBufAllocator allocator) {
            this.eventLoop = new StubEventLoop(allocator);
        }

        @Override public Channel channel() { return null; }
        @Override public EventLoop executor() { return eventLoop; }
        @Override public ChannelPipeline pipeline() { return null; }
        @Override public ChannelHandler handler() { return null; }
        @Override public ChannelHandlerContext fireChannelRegistered() { return this; }
        @Override public ChannelHandlerContext fireChannelActive() { return this; }
        @Override public ChannelHandlerContext fireChannelInactive() { return this; }
        @Override public ChannelHandlerContext fireChannelRead(Object msg) { this.firedRead = msg; return this; }
        @Override public ChannelHandlerContext fireChannelReadComplete() { return this; }
        @Override public ChannelHandlerContext fireUserEventTriggered(Object event) { return this; }
        @Override public void bind(SocketAddress localAddress) { }
        @Override public void connect(SocketAddress remoteAddress) { }
        @Override public Future write(Object msg, Promise promise) { this.written = msg; return promise; }
        @Override public ChannelHandlerContext flush() { return this; }
        @Override public Future writeAndFlush(Object msg, Promise promise) { this.writeAndFlushed = msg; return promise; }
    }

    private static final class StubEventLoop implements EventLoop {
        private final PooledByteBufAllocator allocator;

        StubEventLoop(PooledByteBufAllocator allocator) { this.allocator = allocator; }

        @Override public Queue<ScheduleTask> getScheduleTaskQueue() { return null; }
        @Override public boolean inEventLoop() { return true; }
        @Override public PooledByteBufAllocator allocator() { return allocator; }
        @Override public void execute(Runnable task) { task.run(); }
        @Override public void shedule(Runnable task, long delay, TimeUnit unit) { }
        @Override public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) { }
        @Override public EventLoop next() { return this; }
        @Override public Future register(Channel channel, int interestOps) { return new DefaultChannelPromise(); }
        @Override public Future register(Channel channel, int interestOps, Promise promise) { return promise; }
    }
}
