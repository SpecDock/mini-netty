package io.github.specdock.mininetty.buffer;

/**
 * @author specdock
 * @Date 2026/3/12
 * @Time 20:36
 */
public class SimpleByteArray {
    public byte[] bytes;
    public int begin;
    public int end;
    public SimpleByteArray(byte[] bytes, int begin, int end){
        this.bytes = bytes;
        if(begin < 0){
            throw new RuntimeException("SimpleByteArray：数组越界，begin小于0");
        }
        if(end > bytes.length){
            throw new RuntimeException("SimpleByteArray：数组越界，end大于数组长度");
        }
        if(begin > end){
            throw new RuntimeException("SimpleByteArray：begin大于end");
        }
        this.begin = begin;
        this.end = end;
    }

    public SimpleByteArray(){
        throw new RuntimeException("SimpleByteArray：不接受无参构造，请使用其他构造方法");
    }
}
