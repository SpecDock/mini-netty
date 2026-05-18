package io.github.specdock.mininetty.buffer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class PooledByteBufAllocatorRecycleTest {

    @Test(expected = IllegalStateException.class)
    public void releasedPooledBufferIsNotAccessibleBeforeReallocation() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(8);
        ByteBuf buf = allocator.allocate(false);

        buf.release();

        buf.writeByte(1);
    }

    @Test
    public void defaultCapacityBufferReturnsToPoolAfterRelease() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(8);
        ByteBuf buf = allocator.allocate(false);
        buf.writeByte(1);

        buf.release();
        ByteBuf reused = allocator.allocate(false);

        assertSame(buf, reused);
        assertSame(8, reused.writableBytes());
    }

    @Test
    public void nonDefaultCapacityBufferIsNotReusedByFixedPool() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(8);
        ByteBuf custom = allocator.allocate(false, 4);

        custom.release();
        ByteBuf fixed = allocator.allocate(false);

        assertNotSame(custom, fixed);
    }

    @Test
    public void retainedSliceDelaysRecycleUntilLastReferenceRelease() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(8);
        ByteBuf buf = allocator.allocate(false);
        buf.writeByte(1);
        ByteBuf slice = buf.retainedSlice(1);

        assertFalse(buf.release());
        ByteBuf other = allocator.allocate(false);
        assertNotSame(buf, other);

        slice.release();
        ByteBuf reused = allocator.allocate(false);
        assertSame(buf, reused);
    }
}
