package io.github.specdock.mininetty;

import io.github.specdock.mininetty.buffer.ByteBuf;
import io.github.specdock.mininetty.buffer.PooledByteBufAllocator;

/**
 * @author specdock
 * @Date 2026/3/7
 * @Time 14:07
 */
public class PooledByteBufAllocatorTest {
    public static void main(String[] args) throws InterruptedException {
    // 测试直接内存缓冲区池
        PooledByteBufAllocator allocator = new PooledByteBufAllocator();
        while(true){
            ByteBuf byteBuf = allocator.allocate(true);
            Thread.sleep(10);
            allocator.recycle(byteBuf);
        }
    }
}
