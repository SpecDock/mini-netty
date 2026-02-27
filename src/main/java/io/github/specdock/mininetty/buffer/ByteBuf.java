package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.nio.ByteBuffer;

/**
 * @author specdock
 * @Date 2026/2/25
 * @Time 21:14
 */
public class ByteBuf {
    private final ByteBuffer byteBuffer;
    private int writeIndex;
    private int readIndex;


    public ByteBuf(ByteBuffer byteBuffer){
        this.byteBuffer = byteBuffer;
        writeIndex = 0;
        readIndex = 0;
    }

    public int write(SocketChannel socketChannel){
        byteBuffer.position(writeIndex);
        byteBuffer.limit(byteBuffer.capacity());
        int read = socketChannel.read(byteBuffer);
        writeIndex = byteBuffer.position();
        return read;
    }

    public void read(byte[] focus){
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        byteBuffer.get(focus);
        readIndex = byteBuffer.position();
    }

    public void read(byte[] focus, int offset, int length){
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        byteBuffer.get(focus, offset, length);
        readIndex = byteBuffer.position();
    }

    public int readableBytes(){
        return writeIndex - readIndex;
    }

    public int writableBytes(){
        return byteBuffer.capacity() - writeIndex;
    }
}
