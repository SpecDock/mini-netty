package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-size pooled ByteBuf chunk chain.
 */
public class ByteBufChain implements ReferenceCounted {

    private final LinkedList<ByteBuf> bufferChain = new LinkedList<>();
    private final boolean isDirect;
    private final PooledByteBufAllocator allocator;
    private final AtomicInteger refCnt = new AtomicInteger(1);
    private int readableBytes;

    public ByteBufChain(boolean isDirect, PooledByteBufAllocator allocator) {
        if (allocator == null) {
            throw new NullPointerException("allocator");
        }
        this.isDirect = isDirect;
        this.allocator = allocator;
    }

    public ByteBufChain(boolean isDirect) {
        this(isDirect, new PooledByteBufAllocator());
    }

    public PooledByteBufAllocator allocator() {
        return allocator;
    }

    public void read(byte[] target, int offset, int length) {
        ensureAccessible();
        checkReadable(length);
        while (length > 0) {
            ByteBuf buf = firstReadableBuf();
            int readLength = Math.min(length, buf.readableBytes());
            buf.read(target, offset, readLength);
            readableBytes -= readLength;
            offset += readLength;
            length -= readLength;
            discardReadBuffers();
        }
    }

    public int readableBytes() {
        ensureAccessible();
        return readableBytes;
    }

    public int length() {
        return readableBytes();
    }

    public byte readByte() {
        ensureAccessible();
        checkReadable(1);
        ByteBuf buf = firstReadableBuf();
        byte value = buf.readByte();
        readableBytes--;
        discardReadBuffers();
        return value;
    }

    public void skipBytes(int length) {
        ensureAccessible();
        checkReadable(length);
        while (length > 0) {
            ByteBuf buf = firstReadableBuf();
            int skip = Math.min(length, buf.readableBytes());
            buf.skipBytes(skip);
            readableBytes -= skip;
            length -= skip;
            discardReadBuffers();
        }
    }

    public ByteBufChain readRetainedFrame(int length) {
        ensureAccessible();
        checkReadable(length);
        ByteBufChain frame = new ByteBufChain(isDirect, allocator);
        boolean success = false;
        try {
            while (length > 0) {
                ByteBuf buf = firstReadableBuf();
                int sliceLength = Math.min(length, buf.readableBytes());
                frame.append(buf.retainedSlice(sliceLength));
                buf.skipBytes(sliceLength);
                readableBytes -= sliceLength;
                length -= sliceLength;
                discardReadBuffers();
            }
            success = true;
            return frame;
        } finally {
            if (!success) {
                frame.release();
            }
        }
    }

    public ByteBuffer[] nioBuffers(int maxCount) {
        ensureAccessible();
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive");
        }
        List<ByteBuffer> buffers = new ArrayList<>(Math.min(maxCount, bufferChain.size()));
        for (ByteBuf buf : bufferChain) {
            if (buf.readableBytes() > 0) {
                buffers.add(buf.nioBuffer());
                if (buffers.size() == maxCount) {
                    break;
                }
            }
        }
        return buffers.toArray(new ByteBuffer[0]);
    }

    public ByteBuffer writableNioBuffer() {
        ensureAccessible();
        return getLastWritableBuf().writableNioBuffer();
    }

    public void advanceWriterIndex(int bytes) {
        ensureAccessible();
        ByteBuf last = bufferChain.isEmpty() ? null : bufferChain.getLast();
        if (last == null || bytes < 0 || bytes > last.writableBytes()) {
            throw new IndexOutOfBoundsException("advanceWriterIndex out of range");
        }
        last.advanceWriterIndex(bytes);
        readableBytes += bytes;
    }

    public void writeByte(int value) {
        ensureAccessible();
        ByteBuf buf = getLastWritableBuf();
        buf.writeByte(value);
        readableBytes++;
    }

    public void writeInt(int value) {
        writeByte((value >>> 24) & 0xFF);
        writeByte((value >>> 16) & 0xFF);
        writeByte((value >>> 8) & 0xFF);
        writeByte(value & 0xFF);
    }

    public void writeBytes(byte[] src, int offset, int length) {
        ensureAccessible();
        if (length < 0 || offset < 0 || offset + length > src.length) {
            throw new IndexOutOfBoundsException("writeBytes out of range");
        }
        while (length > 0) {
            ByteBuf buf = getLastWritableBuf();
            int write = Math.min(length, buf.writableBytes());
            buf.writeBytes(src, offset, write);
            readableBytes += write;
            offset += write;
            length -= write;
        }
    }

    public ByteBufChain append(ByteBuf buf) {
        ensureAccessible();
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        bufferChain.addLast(buf);
        readableBytes += buf.readableBytes();
        return this;
    }

    public ByteBufChain appendChain(ByteBufChain chain) {
        ensureAccessible();
        if (chain == null) {
            throw new NullPointerException("chain");
        }
        chain.ensureAccessible();
        while (!chain.bufferChain.isEmpty()) {
            bufferChain.addLast(chain.bufferChain.removeFirst());
        }
        readableBytes += chain.readableBytes;
        chain.readableBytes = 0;
        chain.refCnt.set(0);
        return this;
    }

    public int write(SocketChannel socketChannel) {
        ensureAccessible();
        int sum = 0;
        for (int i = 0; i < 16; i++) {
            ByteBuf buf = getLastWritableBuf();
            int read = buf.writeFromChannel(socketChannel);
            if (read == -1) {
                return -1;
            }
            sum += read;
            readableBytes += read;
            if (read == 0) {
                break;
            }
        }
        return sum;
    }

    public byte[] getByteArray() {
        ensureAccessible();
        byte[] byteArray = new byte[readableBytes];
        read(byteArray, 0, byteArray.length);
        return byteArray;
    }

    public void recycle() {
        release();
    }

    @Override
    public int refCnt() {
        return refCnt.get();
    }

    @Override
    public ByteBufChain retain() {
        int count;
        do {
            count = refCnt.get();
            if (count <= 0) {
                throw new IllegalStateException("Illegal retain: ByteBufChain has already been released.");
            }
        } while (!refCnt.compareAndSet(count, count + 1));
        return this;
    }

    @Override
    public boolean release() {
        int count;
        do {
            count = refCnt.get();
            if (count <= 0) {
                throw new IllegalStateException("Illegal release: ByteBufChain has already been released.");
            }
        } while (!refCnt.compareAndSet(count, count - 1));
        if (count != 1) {
            return false;
        }
        RuntimeException failure = null;
        for (ByteBuf buf : bufferChain) {
            try {
                buf.release();
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
            }
        }
        bufferChain.clear();
        readableBytes = 0;
        if (failure != null) throw failure;
        return true;
    }

    private void discardReadBuffers() {
        while (!bufferChain.isEmpty() && bufferChain.getFirst().readableBytes() <= 0) {
            bufferChain.removeFirst().release();
        }
    }

    private ByteBuf firstReadableBuf() {
        discardReadBuffers();
        if (bufferChain.isEmpty()) {
            throw new IndexOutOfBoundsException("No readable bytes");
        }
        return bufferChain.getFirst();
    }

    private void createLast() {
        bufferChain.addLast(allocator.allocate(isDirect));
    }

    private ByteBuf getLastWritableBuf() {
        if (bufferChain.isEmpty() || bufferChain.getLast().writableBytes() == 0) {
            createLast();
        }
        return bufferChain.getLast();
    }

    private void checkReadable(int length) {
        if (length < 0 || length > readableBytes) {
            throw new IndexOutOfBoundsException("Not enough readable bytes");
        }
    }

    private void ensureAccessible() {
        if (refCnt.get() <= 0) {
            throw new IllegalStateException("Illegal access: ByteBufChain has already been released.");
        }
    }
}
