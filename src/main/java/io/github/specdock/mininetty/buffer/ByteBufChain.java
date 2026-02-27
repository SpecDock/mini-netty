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
    private final static int DEFAULT_CHUNK_SIZE = 100;

    private LinkedList<ByteBuf> bufferChain;
    private int chunkSize;
    private boolean isDirect;



    public ByteBufChain(int chunkSize, boolean isDirect){
        bufferChain = new LinkedList<>();
        this.chunkSize = chunkSize;
        this.isDirect = isDirect;
    }

    public ByteBufChain(boolean isDirect){
        this(DEFAULT_CHUNK_SIZE, isDirect);
    }

    public LinkedList<ByteBuf> getBufferChain(){
        return bufferChain;
    }

    public void read(byte[] target, int offset, int length){
        while(length > 0){
            ByteBuf buf = bufferChain.getFirst();
            if(buf.readableBytes() <= 0){
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
        if(isDirect){
            bufferChain.addLast(new ByteBuf(ByteBuffer.allocateDirect(chunkSize)));
        }
        else {
            bufferChain.addLast(new ByteBuf(ByteBuffer.allocate(chunkSize)));
        }
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
            int write = buf.write(socketChannel);
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
