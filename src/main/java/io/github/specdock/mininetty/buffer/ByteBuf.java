package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author specdock
 * @Date 2026/2/25
 * @Time 21:14
 */
public class ByteBuf implements ReferenceCounted {
    // 全局静态缓存，避免运行时的反射查找开销
    private static final Object UNSAFE_INSTANCE;
    private static final Method INVOKE_CLEANER_METHOD;

    // Java 8 兜底专用的静态缓存
    private static final Method SUN_NIO_CLEANER_METHOD;
    private static final Method SUN_MISC_CLEAN_METHOD;
    private static final Method ATTACHMENT_METHOD;

    static {
        Object unsafe = null;
        Method invokeCleaner = null;
        Method nioCleaner = null;
        Method miscClean = null;
        Method attachment = null;

        try {
            // 1. 破解 Unsafe 实例：绕过 getUnsafe() 的校验，直接反射获取内部字段
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafe = theUnsafeField.get(null);

            // 2. 尝试探测 Java 9+ 的高性能专有清理方法
            invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
        } catch (Throwable t1) {
            // 3. Java 8 降级探测：初始化传统反射所需的 Method 句柄
            try {
                Class<?> directBufferClazz = Class.forName("sun.nio.ch.DirectBuffer");
                nioCleaner = directBufferClazz.getMethod("cleaner");
                attachment = directBufferClazz.getMethod("attachment");

                Class<?> cleanerClazz = Class.forName("sun.misc.Cleaner");
                miscClean = cleanerClazz.getMethod("clean");
            } catch (Throwable t2) {
                // 此处抛出异常意味着运行环境极度异常，既不支持 Java 9 方案也不支持 Java 8 方案
                System.err.println("DirectMemoryManager initialization failed. Native memory leaks are imminent.");
            }
        }

        UNSAFE_INSTANCE = unsafe;
        INVOKE_CLEANER_METHOD = invokeCleaner;
        SUN_NIO_CLEANER_METHOD = nioCleaner;
        SUN_MISC_CLEAN_METHOD = miscClean;
        ATTACHMENT_METHOD = attachment;
    }



    private final ByteBuffer byteBuffer;
    // retainedSlice 只创建视图，所有视图共享 root 的引用计数，避免 slice 单独清理导致 double free。
    private final ByteBuf root;
    private final AtomicInteger refCnt;
    private final AtomicInteger generation;
    private int generationSnapshot;
    // 只有根 ByteBuf 拥有真实内存；派生视图 release 时只递减 root.refCnt。
    private final boolean ownsMemory;
    private final PooledByteBufAllocator allocator;
    private int writeIndex;
    private int readIndex;


    public ByteBuf(ByteBuffer byteBuffer){
        this(byteBuffer, null);
    }

    ByteBuf(ByteBuffer byteBuffer, PooledByteBufAllocator allocator){
        this.byteBuffer = byteBuffer;
        this.root = this;
        this.refCnt = new AtomicInteger(1);
        this.generation = new AtomicInteger(0);
        this.generationSnapshot = 0;
        this.ownsMemory = true;
        this.allocator = allocator;
        writeIndex = byteBuffer.position();
        readIndex = 0;
    }

    private ByteBuf(ByteBuf root, ByteBuffer byteBuffer, int readIndex, int writeIndex) {
        this.byteBuffer = byteBuffer;
        this.root = root.root;
        this.refCnt = this.root.refCnt;
        this.generation = this.root.generation;
        this.generationSnapshot = this.root.generation.get();
        this.ownsMemory = false;
        this.allocator = this.root.allocator;
        this.readIndex = readIndex;
        this.writeIndex = writeIndex;
    }

    public void ensureAccessible() {
        if (refCnt.get() <= 0 || generationSnapshot != root.generation.get()) {
            throw new IllegalStateException("Illegal access: ByteBuf has already been released.");
            // 在 Netty 中通常会抛出专用的 IllegalReferenceCountException
        }
    }

    public int writeFromChannel(SocketChannel socketChannel){
        ensureAccessible();
        byteBuffer.position(writeIndex);
        byteBuffer.limit(byteBuffer.capacity());
        int read = socketChannel.read(byteBuffer);
        writeIndex = byteBuffer.position();
        return read;
    }

    public void read(byte[] focus){
        ensureAccessible();
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        byteBuffer.get(focus);
        readIndex = byteBuffer.position();
    }

    public void read(byte[] focus, int offset, int length){
        ensureAccessible();
        if (length > readableBytes()) {
            throw new IndexOutOfBoundsException("Not enough readable bytes");
        }
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        byteBuffer.get(focus, offset, length);
        readIndex = byteBuffer.position();
    }

    public int readableBytes(){
        ensureAccessible();
        return writeIndex - readIndex;
    }

    public byte readByte() {
        ensureAccessible();
        if (readableBytes() <= 0) {
            throw new IndexOutOfBoundsException("No readable bytes");
        }
        byte b = byteBuffer.get(readIndex);
        readIndex++;
        return b;
    }

    public void skipBytes(int length) {
        ensureAccessible();
        if (length < 0 || length > readableBytes()) {
            throw new IndexOutOfBoundsException("skipBytes out of range");
        }
        readIndex += length;
    }

    public void writeByte(int value) {
        ensureAccessible();
        if (writableBytes() < 1) {
            throw new IndexOutOfBoundsException("No writable bytes");
        }
        byteBuffer.put(writeIndex++, (byte) value);
    }

    public void writeInt(int value) {
        ensureAccessible();
        if (writableBytes() < 4) {
            throw new IndexOutOfBoundsException("No writable bytes");
        }
        byteBuffer.put(writeIndex++, (byte) ((value >>> 24) & 0xFF));
        byteBuffer.put(writeIndex++, (byte) ((value >>> 16) & 0xFF));
        byteBuffer.put(writeIndex++, (byte) ((value >>> 8) & 0xFF));
        byteBuffer.put(writeIndex++, (byte) (value & 0xFF));
    }

    public void writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
    }

    public void writeBytes(byte[] src, int offset, int length) {
        ensureAccessible();
        if (length > writableBytes()) {
            throw new IndexOutOfBoundsException("No writable bytes");
        }
        ByteBuffer duplicate = byteBuffer.duplicate();
        duplicate.position(writeIndex);
        duplicate.put(src, offset, length);
        writeIndex += length;
    }

    public ByteBuffer nioBuffer() {
        ensureAccessible();
        // 返回 duplicate/slice 视图，避免修改原 ByteBuffer 的 position/limit。
        ByteBuffer duplicate = byteBuffer.duplicate();
        duplicate.position(readIndex);
        duplicate.limit(writeIndex);
        return duplicate.slice();
    }

    public ByteBuf retainedSlice(int length) {
        ensureAccessible();
        if (length < 0 || length > readableBytes()) {
            throw new IndexOutOfBoundsException("retainedSlice out of range");
        }
        // 切片与当前 ByteBuf 共享底层内存，必须 retain 保证下游持有期间 root 不被释放。
        retain();
        ByteBuffer duplicate = byteBuffer.duplicate();
        duplicate.position(readIndex);
        duplicate.limit(readIndex + length);
        return new ByteBuf(root, duplicate.slice(), 0, length);
    }

    public int writableBytes(){
        ensureAccessible();
        return byteBuffer.capacity() - writeIndex;
    }

    public int writeToChannel(SocketChannel socketChannel){
        ensureAccessible();
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        int read = socketChannel.write(byteBuffer);
        readIndex = byteBuffer.position();
        return read;
    }

    public void reset(){
        ensureAccessible();
        readIndex = 0;
        writeIndex = 0;
        // 重置底层ByteBuffer
        byteBuffer.clear();
    }


    @Override
    public int refCnt() {
        if (generationSnapshot != root.generation.get()) {
            return 0;
        }
        return refCnt.get();
    }

    @Override
    public ByteBuf retain() {
        int count;
        do {
            if (generationSnapshot != root.generation.get()) {
                throw new IllegalStateException("Illegal retain: ByteBuf has already been released.");
            }
            count = refCnt.get();
            if (count <= 0) {
                throw new IllegalStateException("Illegal retain: ByteBuf has already been released.");
            }
        } while (!refCnt.compareAndSet(count, count + 1));
        return this;
    }

    @Override
    public boolean release() {
        int count;
        do {
            if (generationSnapshot != root.generation.get()) {
                throw new IllegalStateException("Illegal release: ByteBuf has already been released.");
            }
            count = refCnt.get();
            if (count <= 0) {
                throw new IllegalStateException("Illegal release: ByteBuf has already been released.");
            }
        } while (!refCnt.compareAndSet(count, count - 1));
        if (count == 1) {
            // 最后一个引用释放时，只允许 root 清理真实 direct memory。
            if (root.ownsMemory) {
                if (root.allocator != null) {
                    root.allocator.recycle(root);
                } else {
                    releaseNative(root.byteBuffer);
                }
            }
            return true;
        }
        return false;
    }

    public boolean isDirect(){
        ensureAccessible();
        return byteBuffer.isDirect();
    }

    void resetForReuse() {
        root.generationSnapshot = root.generation.get();
        root.refCnt.set(1);
        root.readIndex = 0;
        root.writeIndex = 0;
        root.byteBuffer.clear();
    }

    void markRecycledForAllocator() {
        root.generation.incrementAndGet();
        root.refCnt.set(0);
        root.readIndex = 0;
        root.writeIndex = 0;
        root.byteBuffer.clear();
    }

    void deallocateForAllocator() {
        root.refCnt.set(0);
        if (root.ownsMemory) {
            releaseNative(root.byteBuffer);
        }
    }

    int capacityForAllocator() {
        return root.byteBuffer.capacity();
    }

    boolean isDirectForAllocator() {
        return root.byteBuffer.isDirect();
    }

    boolean isCurrentForAllocator() {
        return generationSnapshot == root.generation.get();
    }

    boolean isRootForAllocator() {
        return this == root;
    }

    private static void releaseNative(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        try {
            if (INVOKE_CLEANER_METHOD != null) {
                INVOKE_CLEANER_METHOD.invoke(UNSAFE_INSTANCE, buffer);
            } else if (SUN_NIO_CLEANER_METHOD != null) {
                Object cleaner = SUN_NIO_CLEANER_METHOD.invoke(buffer);
                if (cleaner == null) {
                    // 正确解析切片：获取真正的物理 buffer 并递归释放
                    Object attached = ATTACHMENT_METHOD.invoke(buffer);
                    if (attached instanceof ByteBuffer) {
                        releaseNative((ByteBuffer) attached);
                    }
                    return;
                }
                SUN_MISC_CLEAN_METHOD.invoke(cleaner);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to release direct memory", e);
        }
    }
}
