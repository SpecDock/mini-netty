package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.util.LinkedList;

/**
 * @author specdock
 * @Date 2026/2/25
 * @Time 21:15
 */
public class ByteBufChain implements ReferenceCounted {

    private LinkedList<ByteBuf> bufferChain;
    private boolean isDirect;
    private final PooledByteBufAllocator allocator;
    private final int chunkSize;



    public ByteBufChain(boolean isDirect, PooledByteBufAllocator allocator){
        bufferChain = new LinkedList<>();
        this.isDirect = isDirect;
        this.allocator = allocator;
        this.chunkSize = allocator.bufferSize();
    }


    public ByteBufChain(boolean isDirect){
        this(isDirect, new PooledByteBufAllocator());
    }



    

    public void read(byte[] target, int offset, int length){
        while(length > 0){
            ByteBuf buf = bufferChain.getFirst();
            if(buf.readableBytes() <= 0){
                buf.release();
                bufferChain.remove(0);
                continue;
            }
            int readLength = Math.min(length, buf.readableBytes());
            buf.read(target, offset, readLength);
            offset += readLength;
            length -= readLength;
        }
    }

    public int readableBytes(){
        return length();
    }

    public byte readByte(){
        while(true){
            ByteBuf buf = bufferChain.getFirst();
            if(buf.readableBytes() <= 0){
                buf.release();
                bufferChain.removeFirst();
                continue;
            }
            byte value = buf.readByte();
            discardReadBuffers();
            return value;
        }
    }

    public void skipBytes(int length){
        while(length > 0){
            ByteBuf buf = bufferChain.getFirst();
            if(buf.readableBytes() <= 0){
                buf.release();
                bufferChain.removeFirst();
                continue;
            }
            int skip = Math.min(length, buf.readableBytes());
            buf.skipBytes(skip);
            length -= skip;
            discardReadBuffers();
        }
    }

    /**
     * 从链上读取一个完整 frame 的零拷贝视图。
     *
     * <p>如果 frame 横跨多个 ByteBuf，会返回 CompositeByteBuf；每段通过 retainedSlice
     * 保留底层内存引用，原链消费完的 chunk 可以安全 release。</p>
     */
    public ReferenceCounted readRetainedFrame(int length){
        if(length < 0 || length > readableBytes()){
            throw new IndexOutOfBoundsException("Not enough readable bytes for frame");
        }
        CompositeByteBuf composite = new CompositeByteBuf();
        while(length > 0){
            ByteBuf buf = bufferChain.getFirst();
            if(buf.readableBytes() <= 0){
                buf.release();
                bufferChain.removeFirst();
                continue;
            }
            int sliceLength = Math.min(length, buf.readableBytes());
            // retainedSlice 保证 frame 交给下游后，底层 chunk 不会被链表回收提前释放。
            composite.addComponent(buf.retainedSlice(sliceLength));
            buf.skipBytes(sliceLength);
            length -= sliceLength;
            discardReadBuffers();
        }
        return composite;
    }

    private void discardReadBuffers(){
        while(!bufferChain.isEmpty() && bufferChain.getFirst().readableBytes() <= 0){
            bufferChain.removeFirst().release();
        }
    }


    private void creatLast(){
        ByteBuf buf = allocator.allocate(isDirect);
        bufferChain.addLast(buf);
    }

    private ByteBuf getLastWritableBuf(){
        if(bufferChain.isEmpty()){
            creatLast();
        }
        ByteBuf buf = bufferChain.getLast();

        if(buf.writableBytes() == 0){
            creatLast();
        }

        return bufferChain.getLast();
    }

    public int write(SocketChannel socketChannel){
        int sum = 0;
        for(int i = 0; i < 16; i++){
            ByteBuf buf = getLastWritableBuf();
            int write = buf.writeFromChannel(socketChannel);
            if(write == -1){
                return -1;
            }
            sum += write;
            if(write == 0){
               break;
            }
        }
        return sum;
    }

    public byte[] getByteArray(){
        int length = chunkSize * (bufferChain.size() - 1) + bufferChain.getLast().readableBytes();
        System.out.println(bufferChain.getLast().readableBytes());
        byte[] byteArray = new byte[length];
        int offset = 0;
        for(ByteBuf byteBuf : bufferChain){
            byteBuf.read(byteArray, offset, Math.min(chunkSize, length - offset));
            offset += chunkSize;
        }
        return byteArray;
    }

    public int length(){
        int sum = 0;
        for(ByteBuf buf : bufferChain){
            sum += buf.readableBytes();
        }
        return sum;
    }

    /**
     * 释放所有的ByteBuf到池中
     */
    public void recycle() {
        release();
    }

    @Override
    public int refCnt() {
        return bufferChain.isEmpty() ? 0 : 1;
    }

    @Override
    public ReferenceCounted retain() {
        for(ByteBuf buf : bufferChain){
            buf.retain();
        }
        return this;
    }

    @Override
    public boolean release() {
        RuntimeException failure = null;
        for(ByteBuf buf : bufferChain){
            try {
                buf.release();
            } catch (RuntimeException e) {
                if(failure == null) {
                    failure = e;
                }
            }
        }
        bufferChain.clear();
        if(failure != null) {
            throw failure;
        }
        return true;
    }


//    public boolean isEnd(){
//        byte[] code = "\n\n\r\r\n\r".getBytes(StandardCharsets.UTF_8);
//        List<byte[]> byteList = new ArrayList<>();
//        int codeLength = code.length - 1;
//        int index = bufferChain.size() - 1;
//        while(codeLength >= 0){
//            ByteBuf byteBuf = bufferChain.get(index);
//            byte[] bytes = new byte[byteBuf.readableBytes()];
//            byteBuf.read(bytes);
//            for(int i = bytes.length - 1; i >= 0 && codeLength >= 0; i--, codeLength--){
//                if(bytes[i] != code[codeLength]){
//                    return false;
//                }
//            }
//        }
//
//        return true;
//    }
}
