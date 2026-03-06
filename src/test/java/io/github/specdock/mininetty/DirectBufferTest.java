package io.github.specdock.mininetty;

import io.github.specdock.mininetty.buffer.ByteBuf;

import java.nio.ByteBuffer;

/**
 * @author specdock
 * @Date 2026/3/6
 * @Time 15:45
 */
public class DirectBufferTest {
    public static void main(String[] args) throws InterruptedException {
        //test direct buffer
        while(true){
            ByteBuf byteBuf = new ByteBuf(ByteBuffer.allocateDirect(1024 * 1024));
            Thread.sleep(100);
            //byteBuf.release();
        }
    }
}
