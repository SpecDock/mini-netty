package io.github.specdock.mininetty.channel.nio;

import io.github.specdock.mininetty.buffer.ByteBufChain;
import io.github.specdock.mininetty.channel.Channel;
import io.github.specdock.mininetty.channel.EventLoop;
import io.github.specdock.mininetty.channel.ServerChannel;
import io.github.specdock.mininetty.channel.socket.SocketChannel;
import io.github.specdock.mininetty.util.concurrent.ScheduleTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:27
 */
public class NioEventLoop implements EventLoop {
    private static final AtomicInteger THREAD_NAME_INDEX = new AtomicInteger();

    private final Selector selector;
    private final Thread thread;
    private final PriorityBlockingQueue<ScheduleTask> scheduleTaskQueue;
    private final BlockingQueue<Runnable> taskQueue;


    /**
     * 初始化，并启动线程
     */
    public NioEventLoop(){
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("NioEventLoop开启监听器失败", e);
        }

        taskQueue = new ArrayBlockingQueue<>(1024);
        scheduleTaskQueue = new PriorityBlockingQueue<>(1024);










        this.thread = new NioEventLoopThread("io-github-specdock-mininetty-eventLoop-thread" + THREAD_NAME_INDEX.getAndIncrement());
        this.thread.start();
        System.out.println("成功启动一个EventLoop");
    }


    /**
     * 提交一个普通任务
     * 该方法会将任务放入普通任务队列
     *
     * @param task
     */
    @Override
    public void execute(Runnable task) {
        if (!taskQueue.offer(task)) {
            throw new RuntimeException("普通任务阻塞队列已经满了");
        }
        selector.wakeup();
    }

    /**
     * 提交一个定时任务
     * 该方法会将任务放入定时任务队列
     *
     * @param task
     * @param delay
     * @param unit
     *
     */
    @Override
    public void shedule(Runnable task, long delay, TimeUnit unit) {
        scheduleAtFixedRate(task, delay, -1, unit);
    }

    /**
     * 提交一个定时任务
     * 该方法会将任务放入定时任务队列
     *
     * @param task
     * @param initialDelay
     * @param period
     * @param unit
     */
    @Override
    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        ScheduleTask scheduleTask = new ScheduleTask(task, this, deadLineMs(initialDelay, unit), unit.toMillis(period));
        if (!scheduleTaskQueue.offer(scheduleTask)) {
            throw new RuntimeException("阻塞队列已经满了");
        }
        selector.wakeup();
    }

    /**
     *
     * @param delay
     * @param unit
     * @return
     *
     * 算出截至时间，所有时间都会被换算为 ms
     */
    private long deadLineMs(long delay, TimeUnit unit){
        long l = System.currentTimeMillis();
        return l + unit.toMillis(delay);
    }


    /**
     * EventLoop中 返回自己
     * EventLoopGroup中 返回下一个EvenLoop
     *
     * @return
     */
    @Override
    public EventLoop next() {
        return this;
    }


    @Override
    public void register(Channel channel, int interestOps) {
        if(this.thread == Thread.currentThread()){
            register0(channel, interestOps);
        }
        else {
            execute(() -> {
                register0(channel, interestOps);
            });
        }
    }
    private void register0(Channel channel, int interestOps){
        try {
            // 1. 绑定 EventLoop 上下文
            channel.setEventLoop(this);

            // 2. 核心 JNI 调用：向底层 Selector 注册感兴趣的事件
            // 注意：底层 NIO 的 register 方法会抛出 ClosedChannelException
            channel.register(selector, interestOps);

            // 3. 触发责任链的 Registered 生命周期
            channel.pipeline().fireChannelRegistered();
        }catch (Exception e){
            throw new RuntimeException("NioEventLoop中的register0方法出现异常", e);
        }
    }


    @Override
    public Queue<ScheduleTask> getScheduleTaskQueue() {
        return scheduleTaskQueue;
    }










    /**
     * TODO 待实现：分配 50% 的时间给定时任务和普通任务，另外 50% 的事件用来select()监听
     * 每提交一个任何任务（定时任务或者普通任务）会打断阻塞监听
     *
     *
     * @return
     */
    private Runnable processEventsAndTasks() {
        try{
            ScheduleTask scheduleTask = scheduleTaskQueue.peek();
            // 检查是否有定时任务
            if(scheduleTask == null){
                // 检查是否有普通任务
                if(taskQueue.peek() != null){
                    return taskQueue.poll();
                }
                // 什么任务都没有就阻塞等待事件，或者被添加任务打断
                selectAndDisPatch(-1);
                return null;
            }
            // 判断定时任务是否应该执行
            if(scheduleTask.getDeadLine() <= System.currentTimeMillis()) {
                // 这里返回的可能不是前面peek()的scheduleTask，因为期间可能添加了新的比上面的deadLine更早定时任务
                return scheduleTaskQueue.poll();
            }

            // 一定要看一下有没有普通任务，不然下面就要阻塞了
            if(taskQueue.peek() != null){
                return taskQueue.poll();
            }
            selectAndDisPatch(scheduleTask.getDeadLine() - System.currentTimeMillis());


            // 前面的阻塞停止有三种情况：1.获取到事件了，2.新提交了任务被打断了，3.到timeout时间了

            if(taskQueue.peek() != null){
                return taskQueue.poll();
            }

            ScheduleTask latestHead = scheduleTaskQueue.peek();
            if (latestHead != null && latestHead.getDeadLine() <= System.currentTimeMillis()) {
                return scheduleTaskQueue.poll();
            }
            return null;
        }catch (Exception e){
            System.out.println(this.getClass().getName() + ":processEventsAndTasks, 发生了一次异常\n" + e);
            return null;
        }
    }

    /**
     *
     *
     * @param timeout 单位ms，值为 -1 时：永久阻塞等待
     */
    private void selectAndDisPatch(long timeout) {
        try{
            int select;
            if(timeout < 0){
                select = selector.select();
            }
            else {
                select = selector.select(timeout);
            }

            if(select != 0) {
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while(iterator.hasNext()){
                    SelectionKey selectionKey = iterator.next();
                    // 手动移除 key
                    iterator.remove();
                    // 检查key是否有效
                    try{
                        if(!selectionKey.isValid()){
                            continue;
                        }
                        if ((selectionKey.readyOps() & SelectionKey.OP_ACCEPT) != 0) {
                            // 监听到OP_ACCEPT事件
                            System.out.println("监听到OP_ACCEPT事件");
                            ServerChannel serverChannel = (ServerChannel) selectionKey.attachment();
                            serverChannel.pipeline().fireChannelRead(serverChannel);
                        }
                        if((selectionKey.readyOps() & SelectionKey.OP_READ) != 0){
                            // TODO channel针对读取事件的api

                            System.out.println("监听到OP_READ事件");
                            ByteBufChain msg = new ByteBufChain(true);
                            SocketChannel socketChannel = (SocketChannel) selectionKey.attachment();
                            int write = msg.write(socketChannel);
                            socketChannel.pipeline().fireChannelRead(msg);
                            if(write == -1){
                                throw new RuntimeException(socketChannel.getRemoveAddress().toString() + "：断开连接");
                            }
                        }
                        if((selectionKey.readyOps() & SelectionKey.OP_WRITE) != 0){
                            // TODO 当写入时返回值 < length，注册一个OP_WRITE事件，当写入完后，应该要取消OP_WRITE事件

                            System.out.println("监听到OP_WRITE事件");
                        }
                        if((selectionKey.readyOps() & SelectionKey.OP_CONNECT) != 0){
                            // TODO 连接事件，当channel主动发出连接时，应该注册一个连接事件，后续应该完善 Future 的编写

                            System.out.println("监听到OP_CONNECT事件");
                        }
                    }catch (Exception e){
                        System.err.println("处理单个 Channel 时发生异常，强制关闭该连接: " + e.getMessage());
                        selectionKey.cancel();
                        try{
                            if(selectionKey.channel() != null){
                                selectionKey.channel().close();
                            }
                        }catch (Exception ex){

                        }
                    }
                }
            }
        } catch (Exception e) {
            // 这里捕获的是 selector.select() 抛出的致命系统级异常，通常是底层 OS 资源耗尽
            throw new RuntimeException("Selector 轮询发生系统级异常", e);
        }
    }


    class NioEventLoopThread extends Thread {

        public NioEventLoopThread(String name){
            super(name);
        }

        @Override
        public void run(){
            while(true){
                Runnable runnable = processEventsAndTasks();
                if(runnable != null){
                    runnable.run();
                }
            }
        }
    }

}
