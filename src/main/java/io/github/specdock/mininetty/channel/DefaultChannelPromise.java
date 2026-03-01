package io.github.specdock.mininetty.channel;

import io.github.specdock.mininetty.util.concurrent.Future;
import io.github.specdock.mininetty.util.concurrent.GenericFutureListener;
import io.github.specdock.mininetty.util.concurrent.Promise;

import java.util.ArrayList;
import java.util.List;

/**
  @author specdock
 * @Date 2026/2/27
 * @Time 16:04
 *
 *
 * 极简版异步承诺实现类
 * 负责跨线程的状态同步与回调触发
 */
public class DefaultChannelPromise implements Promise {
    // 核心状态位（使用 volatile 保证 EventLoop 线程与业务线程间的内存可见性）
    private volatile boolean isDone = false;
    private volatile boolean isSuccess = false;
    private volatile Throwable cause;
    private Channel channel;

    // 监听器集合 （非线程安全集合，对其访问必须处于同步块中）
    private List<GenericFutureListener> listeners;

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    //  业务线程调用的方法

    @Override
    public Future addListener(GenericFutureListener listener) {
        boolean notifyNow = false;

        // 临界区：保护共享资源 listeners
        synchronized (this){
            if(isDone){
                // 极限并发场景：添加监听器时，底层的 I/O 操作已经完成，直接在当前线程执行回调
                notifyNow = true;
            }
            else {
                // 懒加载初始化 List，节省内存
                if(listeners == null){
                    listeners = new ArrayList<>();
                }
                listeners.add(listener);
            }
        }
        if(notifyNow){
            notifyListener(listener);
        }

        return this;
    }

    @Override
    public synchronized Future sync() throws InterruptedException {
        // 自旋阻塞等待，直到 EventLoop 线程将其标记为 Done
        while(!isDone){
            this.wait();
        }
        // 如果底层发生了异常，在调用 sync() 的业务线程中抛出
        if(cause != null){
            throw new RuntimeException(cause);
        }
        return this;
    }


    // --- EventLoop 底层线程调用的方法 ---

    @Override
    public Promise setSuccess() {
        // 临界区：保证状态翻转与线程唤醒的原子性
        synchronized (this){
            if(isDone){
                return this;
            }
            this.isSuccess = true;
            this.isDone = true;

            // 核心机制1：唤醒所有正在 sync() 阻塞的线程业务
            this.notifyAll();
        }
        // 核心机制2：触发所有的异步回调监听器
        notifyListeners();
        return this;
    }

    @Override
    public Promise setFailure(Throwable cause) {
        synchronized (this){
            if(isDone){
                return this;
            }
            this.isSuccess = false;
            this.cause = cause;
            this.isDone = true;

            this.notifyAll();
        }
        notifyListeners();
        return this;
    }

    // --- 内部辅助方法 ---

    private void notifyListeners() {
        List<GenericFutureListener> localListeners;

        synchronized (this) {
            if (this.listeners == null) {
                return;
            }
            // 引用隔离与核销：将指针转移至局部变量，并清空成员变量
            // 彻底杜绝 ConcurrentModificationException 与 Listener 内存泄漏
            localListeners = this.listeners;
            this.listeners = null;
        }

        // 无锁化遍历执行
        for (GenericFutureListener listener : localListeners) {
            notifyListener(listener);
        }
    }

    private void notifyListener(GenericFutureListener listener) {
        try {
            listener.operationComplete(this);
        } catch (Exception e) {
            // 框架级容错：严禁某个 Listener 抛出的异常导致整个底层的 EventLoop 线程崩溃
            System.err.println("Listener执行期间发生异常: " + e.getMessage());
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
