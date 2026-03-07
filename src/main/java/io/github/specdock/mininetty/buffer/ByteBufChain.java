package io.github.specdock.mininetty.buffer;

import io.github.specdock.mininetty.channel.socket.SocketChannel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author specdock
 * @Date 2026/2/25
 * @Time 21:15
 */
public class ByteBufChain {

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
                allocator.recycle(buf);
                bufferChain.remove(0);
                continue;
            }
            int readLength = Math.min(length, buf.readableBytes());
            buf.read(target, offset, readLength);
            offset += readLength;
            length -= readLength;
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

    public int write(SocketChannel socketChannel) throws Exception{
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
        for(ByteBuf buf : bufferChain){
            allocator.recycle(buf);
        }
        bufferChain.clear();
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