package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.buffer.ByteBuf;
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
        ByteBuffer buffer = (ByteBuffer) msg;
        byteBufSenders.addLast(new ByteBufSender(buffer, promise));
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
                byteBufSenders.pollFirst();
                continue;
            }
            int write = byteBufSender.doWriteToChannel(socketChannel);
            if(write == 0){
                return ;
            }
        }

    }


    private static class ByteBufSender extends ByteBuf{
        private final Promise promise;

        public ByteBufSender(ByteBuffer byteBuffer, Promise promise) {
            super(byteBuffer);
            this.promise = promise;
        }

        public int doWriteToChannel(SocketChannel socketChannel){
            int write = writeToChannel(socketChannel);
            if(readableBytes() <= 0){
                promise.setSuccess();
            }
            return write;
        }
    }
}
