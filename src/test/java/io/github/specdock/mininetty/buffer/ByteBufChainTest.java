package io.github.specdock.mininetty.buffer;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class ByteBufChainTest {

    @Test
    public void defaultAllocatorChunkSizeIsFourKb() {
        assertEquals(4 * 1024, new PooledByteBufAllocator().bufferSize());
    }

    @Test
    public void readableBytesCacheTracksCrossChunkReadSkipAndWrite() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(4);
        ByteBufChain chain = new ByteBufChain(true, allocator);

        chain.writeBytes(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6);
        assertEquals(6, chain.readableBytes());
        assertEquals(2, chain.nioBuffers(16).length);

        assertEquals(1, chain.readByte());
        chain.skipBytes(2);
        assertEquals(3, chain.readableBytes());

        byte[] out = new byte[3];
        chain.read(out, 0, out.length);
        assertArrayEquals(new byte[]{4, 5, 6}, out);
        assertEquals(0, chain.readableBytes());
        chain.release();
    }

    @Test
    public void writableNioBufferAdvanceWriterIndexWritesIntoFixedChunks() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(4);
        ByteBufChain chain = new ByteBufChain(true, allocator);

        ByteBuffer first = chain.writableNioBuffer();
        first.put(new byte[]{1, 2, 3, 4});
        chain.advanceWriterIndex(4);
        ByteBuffer second = chain.writableNioBuffer();
        second.put((byte) 5);
        chain.advanceWriterIndex(1);

        assertEquals(5, chain.readableBytes());
        assertEquals(2, chain.nioBuffers(16).length);
        byte[] out = new byte[5];
        chain.read(out, 0, 5);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, out);
        chain.release();
    }

    @Test
    public void retainedFrameReturnsChainAndReleasesSlicesIndependently() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(4);
        ByteBufChain chain = new ByteBufChain(true, allocator);
        chain.writeBytes(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6);

        ByteBufChain frame = chain.readRetainedFrame(5);

        assertEquals(1, chain.readableBytes());
        assertEquals(5, frame.readableBytes());
        byte[] out = new byte[5];
        frame.read(out, 0, 5);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, out);
        frame.release();
        chain.release();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nioBuffersRejectsNonPositiveMaxCount() {
        new ByteBufChain(true, new PooledByteBufAllocator(4)).nioBuffers(0);
    }
}
