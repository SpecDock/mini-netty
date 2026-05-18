package io.github.specdock.mininetty.buffer;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 池化ByteBuf分配器，用于减少内存分配和释放的开销
 * @author specdock
 * @Date 2026/3/7
 */
public class PooledByteBufAllocator {
    // 默认缓冲区大小
    // 4KB
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
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
        this.directBufferPool = new ConcurrentLinkedQueue<>();
        this.heapBufferPool = new ConcurrentLinkedQueue<>();
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
            buf = new ByteBuf(byteBuffer, this);
        } else {
            buf.resetForReuse();
        }
        return buf;
    }

    public ByteBuf allocate(boolean isDirect, int capacity) {
        // 非固定大小的协议头/字符串转换 buffer 不进入固定 chunk 池，避免回池后容量不一致。
        ByteBuffer byteBuffer = isDirect ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        return new ByteBuf(byteBuffer, this);
    }

    /**
     * 回收ByteBuf到池中
     * @param buf 要回收的ByteBuf
     */
    public void recycle(ByteBuf buf) {
        if (!buf.isCurrentForAllocator()) {
            return;
        }

        if (!buf.isRootForAllocator()) {
            buf.release();
            return;
        }

        if (buf.refCnt() > 1) {
            // 仍有 retained slice 或组合视图持有时不能回池，否则会出现 use-after-recycle。
            return;
        }

        if (buf.refCnt() == 1) {
            buf.release();
            return;
        }

        Queue<ByteBuf> pool = buf.isDirectForAllocator() ? directBufferPool : heapBufferPool;
        if (buf.capacityForAllocator() == bufferSize && pool.size() < MAX_POOL_SIZE) {
            buf.markRecycledForAllocator();
            pool.offer(buf);
        } else {
            buf.deallocateForAllocator();
        }
    }

    /**
     * 关闭分配器，释放所有池中的缓冲区
     */
    public void close() {
        // 释放直接内存缓冲区
        while (!directBufferPool.isEmpty()) {
            ByteBuf buf = directBufferPool.poll();
            buf.deallocateForAllocator();
        }
        // 释放堆内存缓冲区
        while (!heapBufferPool.isEmpty()) {
            ByteBuf buf = heapBufferPool.poll();
            buf.deallocateForAllocator();
        }
    }

    public int bufferSize(){
        return bufferSize;
    }
}
