package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author specdock
 * @Date 2026/2/25
 * @Time 21:14
 */
public class ByteBuf {
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
    private int writeIndex;
    private int readIndex;


    // 防御性并发标志，防止 Double Free 导致 JVM 崩溃
    private final AtomicBoolean isReleased = new AtomicBoolean(false);


    public ByteBuf(ByteBuffer byteBuffer){
        this.byteBuffer = byteBuffer;
        writeIndex = byteBuffer.position();
        readIndex = 0;
    }

    private void ensureAccessible() {
        if (isReleased.get()) {
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
        byteBuffer.position(readIndex);
        byteBuffer.limit(writeIndex);
        byteBuffer.get(focus, offset, length);
        readIndex = byteBuffer.position();
    }

    public int readableBytes(){
        ensureAccessible();
        return writeIndex - readIndex;
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

    public void release() {
        // 利用 CAS 操作，确保底层物理清理只执行一次
        if (isReleased.compareAndSet(false, true)) {
            releaseNative(this.byteBuffer);
        }
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