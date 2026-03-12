package io.github.specdock.mininetty.buffer;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 池化ByteBuf分配器，用于减少内存分配和释放的开销
 * @author specdock
 * @Date 2026/3/7
 */
public class PooledByteBufAllocator {
    // 默认缓冲区大小
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    // 最大池大小
    private static final int MAX_POOL_SIZE = 1024;

    // 直接内存缓冲区池
    private final Queue<ByteBuf> directBufferPool;
    // 堆内存缓冲区池
    private final Queue<ByteBuf> heapBufferPool;
    // 缓冲区大小
    private final int bufferSize;

    /**
     * 默认构造函数，使用默认缓冲区大小
     */
    public PooledByteBufAllocator() {
        this(DEFAULT_BUFFER_SIZE);
    }

    /**
     * 构造函数，指定缓冲区大小
     * @param bufferSize 缓冲区大小
     */
    public PooledByteBufAllocator(int bufferSize) {
        this.bufferSize = bufferSize;
        this.directBufferPool = new LinkedList<>();
        this.heapBufferPool = new LinkedList<>();
    }

    /**
     * 分配一个ByteBuf
     * @param isDirect 是否使用直接内存
     * @return ByteBuf对象
     */
    public ByteBuf allocate(boolean isDirect) {
        Queue<ByteBuf> pool = isDirect ? directBufferPool : heapBufferPool;
        ByteBuf buf = pool.poll();
        if (buf == null) {
            // 池中没有可用的缓冲区，创建新的
            ByteBuffer byteBuffer = isDirect ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
            buf = new ByteBuf(byteBuffer);
        }
        return buf;
    }

    /**
     * 回收ByteBuf到池中
     * @param buf 要回收的ByteBuf
     */
    public void recycle(ByteBuf buf) {
        // 检查缓冲区是否已经被释放
        try {
            // 尝试访问缓冲区，如果已经被释放会抛出异常
            buf.ensureAccessible();
        } catch (IllegalStateException e) {
            // 缓冲区已经被释放，直接返回
            return;
        }

        // 检查池大小是否达到上限
        Queue<ByteBuf> pool = buf.isDirect() ? directBufferPool : heapBufferPool;
        if (pool.size() < MAX_POOL_SIZE) {
            // 重置缓冲区的读写指针
            buf.reset();
            pool.offer(buf);
        } else {
            // 池已满，释放缓冲区
            buf.release();
        }
    }

    /**
     * 关闭分配器，释放所有池中的缓冲区
     */
    public void close() {
        // 释放直接内存缓冲区
        while (!directBufferPool.isEmpty()) {
            ByteBuf buf = directBufferPool.poll();
            buf.release();
        }
        // 释放堆内存缓冲区
        while (!heapBufferPool.isEmpty()) {
            ByteBuf buf = heapBufferPool.poll();
            buf.release();
        }
    }

    public int bufferSize(){
        return bufferSize;
    }
}