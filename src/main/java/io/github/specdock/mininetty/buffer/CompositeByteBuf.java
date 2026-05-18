package io.github.specdock.mininetty.buffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多段 ByteBuf 的零拷贝组合视图。
 *
 * <p>用于长度字段头 + payload、心跳头 + payload、跨多个入站 chunk 的 frame 等场景。
 * 它不合并复制字节，只按顺序读取各 component，并在自身 release 归零时级联释放组件。</p>
 * @author 29287
 */
public class CompositeByteBuf implements ReferenceCounted {
    private final List<ReferenceCounted> components = new ArrayList<>();
    private final AtomicInteger refCnt = new AtomicInteger(1);

    public CompositeByteBuf addComponent(ReferenceCounted component) {
        ensureAccessible();
        // addComponent 表示 Composite 接管 component 的释放责任，调用方不应再单独 release。
        components.add(component);
        return this;
    }

    public int readableBytes() {
        ensureAccessible();
        int sum = 0;
        for (ReferenceCounted component : components) {
            if (component instanceof ByteBuf) {
                sum += ((ByteBuf) component).readableBytes();
            } else if (component instanceof CompositeByteBuf) {
                sum += ((CompositeByteBuf) component).readableBytes();
            }
        }
        return sum;
    }

    public byte readByte() {
        ensureAccessible();
        for (ReferenceCounted component : components) {
            int readable = readableBytes(component);
            if (readable > 0) {
                return component instanceof ByteBuf
                        ? ((ByteBuf) component).readByte()
                        : ((CompositeByteBuf) component).readByte();
            }
        }
        throw new IndexOutOfBoundsException("No readable bytes");
    }

    public void skipBytes(int length) {
        ensureAccessible();
        while (length > 0) {
            ReferenceCounted component = firstReadable();
            if (component == null) {
                throw new IndexOutOfBoundsException("skipBytes out of range");
            }
            int skip = Math.min(length, readableBytes(component));
            if (component instanceof ByteBuf) {
                ((ByteBuf) component).skipBytes(skip);
            } else {
                ((CompositeByteBuf) component).skipBytes(skip);
            }
            length -= skip;
        }
    }

    public void read(byte[] target, int offset, int length) {
        ensureAccessible();
        while (length > 0) {
            ReferenceCounted component = firstReadable();
            if (component == null) {
                throw new IndexOutOfBoundsException("Not enough readable bytes");
            }
            int read = Math.min(length, readableBytes(component));
            if (component instanceof ByteBuf) {
                ((ByteBuf) component).read(target, offset, read);
            } else {
                ((CompositeByteBuf) component).read(target, offset, read);
            }
            offset += read;
            length -= read;
        }
    }

    public ByteBuffer[] nioBuffers() {
        ensureAccessible();
        // 暴露多段 ByteBuffer 给 SocketChannel.write(ByteBuffer[])，避免出站合并拷贝。
        List<ByteBuffer> buffers = new ArrayList<>();
        collectNioBuffers(this, buffers);
        return buffers.toArray(new ByteBuffer[0]);
    }

    private static void collectNioBuffers(CompositeByteBuf composite, List<ByteBuffer> out) {
        for (ReferenceCounted component : composite.components) {
            if (component instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) component;
                if (buf.readableBytes() > 0) {
                    out.add(buf.nioBuffer());
                }
            } else if (component instanceof CompositeByteBuf) {
                collectNioBuffers((CompositeByteBuf) component, out);
            }
        }
    }

    private ReferenceCounted firstReadable() {
        for (ReferenceCounted component : components) {
            if (readableBytes(component) > 0) {
                return component;
            }
        }
        return null;
    }

    private int readableBytes(ReferenceCounted component) {
        return component instanceof ByteBuf ? ((ByteBuf) component).readableBytes() : ((CompositeByteBuf) component).readableBytes();
    }

    private void ensureAccessible() {
        if (refCnt.get() <= 0) {
            throw new IllegalStateException("Illegal access: CompositeByteBuf has already been released.");
        }
    }

    @Override
    public int refCnt() {
        return refCnt.get();
    }

    @Override
    public CompositeByteBuf retain() {
        int count;
        do {
            count = refCnt.get();
            if (count <= 0) {
                throw new IllegalStateException("Illegal retain: CompositeByteBuf has already been released.");
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
                throw new IllegalStateException("Illegal release: CompositeByteBuf has already been released.");
            }
        } while (!refCnt.compareAndSet(count, count - 1));
        if (count == 1) {
            RuntimeException failure = null;
            // Composite 是逻辑视图，真正资源在 component 上，归零时逐个释放 component。
            for (ReferenceCounted component : components) {
                try {
                    component.release();
                } catch (RuntimeException e) {
                    if (failure == null) failure = e;
                }
            }
            components.clear();
            if (failure != null) throw failure;
            return true;
        }
        return false;
    }
}
