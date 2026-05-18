package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.CompositeByteBuf;
import io.github.specdock.mininetty.buffer.ReferenceCounted;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Deque;
import java.util.LinkedList;

/**
 * @author specdock
 * @Date 2026/2/27
 * @Time 20:02
 */
public class ChannelOutboundBuffer {
    private final SocketChannel socketChannel;
    private final LinkedList<ByteBufSender> byteBufSenders;

    public ChannelOutboundBuffer(SocketChannel socketChannel){
        byteBufSenders = new LinkedList<>();
        this.socketChannel = socketChannel;
    }



    public void writeToBuffer(Object msg, Promise promise){
        try {
            // 入队后由 outbound buffer 接管 ReferenceCounted 的最终 release 责任。
            byteBufSenders.addLast(new ByteBufSender(toReferenceCounted(msg), promise));
        } catch (RuntimeException e) {
            promise.setFailure(e);
            throw e;
        }
    }

    public void flush(){
        try{
            doWriteToChannel();
            if((socketChannel.getSelectionKey().interestOps() & SelectionKey.OP_WRITE) != 0){
                return ;
            }
            // 2. 状态机安全断言：只有在队列没清空（即触发了 TCP 发送窗口满的背压机制）时，才需要注册 OP_WRITE
            if (!byteBufSenders.isEmpty()) {
                SelectionKey key = socketChannel.getSelectionKey();
                if (key != null && (key.interestOps() & SelectionKey.OP_WRITE) == 0) {
                    Selector selector = socketChannel.getSelectionKey().selector();
                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                }
            }
        } catch (Exception e) {
            close(e);
            throw new RuntimeException("缓冲区写入内核出现异常", e);
        }
    }

    private void doWriteToChannel(){
        if(byteBufSenders.isEmpty()){
            socketChannel.unregister(SelectionKey.OP_WRITE);
            return ;
        }
        while(!byteBufSenders.isEmpty()){
            ByteBufSender byteBufSender = byteBufSenders.peekFirst();
            if(byteBufSender.readableBytes() <= 0){
                byteBufSenders.pollFirst().success();
                continue;
            }
            int write = byteBufSender.doWriteToChannel(socketChannel);
            if(write == 0){
                return ;
            }
        }
        socketChannel.unregister(SelectionKey.OP_WRITE);

    }

    public void close() {
        close(null);
    }

    private void close(Throwable cause) {
        while(!byteBufSenders.isEmpty()){
            ByteBufSender sender = byteBufSenders.pollFirst();
            sender.fail(cause == null ? new IllegalStateException("Channel closed") : cause);
        }
    }

    private ReferenceCounted toReferenceCounted(Object msg) {
        if (msg instanceof ReferenceCounted) {
            return (ReferenceCounted) msg;
        }
        if (msg instanceof byte[]) {
            byte[] bytes = (byte[]) msg;
            ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
            buf.writeBytes(bytes);
            return buf;
        }
        if (msg instanceof ByteBuffer) {
            // ByteBuffer 兼容路径需要复制到 direct ByteBuf；主链路应直接传 ByteBuf/CompositeByteBuf。
            ByteBuffer source = ((ByteBuffer) msg).duplicate();
            if (source.position() == source.limit()) {
                source.position(0);
            }
            byte[] bytes = new byte[source.remaining()];
            source.get(bytes);
            ByteBuf buf = new ByteBuf(ByteBuffer.allocateDirect(bytes.length));
            buf.writeBytes(bytes);
            return buf;
        }
        throw new IllegalArgumentException("Unsupported outbound message type: " + msg.getClass().getName());
    }


    private static class ByteBufSender {
        private final ReferenceCounted msg;
        private final Promise promise;

        public ByteBufSender(ReferenceCounted msg, Promise promise) {
            this.msg = msg;
            this.promise = promise;
        }

        public int readableBytes(){
            if(msg instanceof ByteBuf){
                return ((ByteBuf) msg).readableBytes();
            }
            return ((CompositeByteBuf) msg).readableBytes();
        }

        public int doWriteToChannel(SocketChannel socketChannel){
            int write;
            if(msg instanceof ByteBuf){
                ByteBuf byteBuf = (ByteBuf) msg;
                write = socketChannel.write(byteBuf.nioBuffer());
                byteBuf.skipBytes(write);
            }
            else {
                CompositeByteBuf composite = (CompositeByteBuf) msg;
                // Gathering write 直接写多段 ByteBuffer，避免把 Composite 合并成单个数组。
                write = (int) socketChannel.write(composite.nioBuffers());
                composite.skipBytes(write);
            }
            return write;
        }

        public void success(){
            msg.release();
            promise.setSuccess();
        }

        public void fail(Throwable cause){
            try {
                msg.release();
            } finally {
                promise.setFailure(cause);
            }
        }
    }
}
