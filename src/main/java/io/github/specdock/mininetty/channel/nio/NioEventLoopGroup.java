package io.github.specdock.mininetty.channel.nio;

import io.github.specdock.mininetty.channel.Channel;
import io.github.specdock.mininetty.channel.EventLoop;
import io.github.specdock.mininetty.channel.EventLoopGroup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:10
 */
public class NioEventLoopGroup implements EventLoopGroup {
     //作用于负载均衡，轮询的index
     private final AtomicInteger index = new AtomicInteger(0);

     // NioEventLoopGroup维护的一组NioEventLoop
     private final NioEventLoop[] nioEventLoops;


     /**
      * 无参构造函数，默认EventLoopGroup维护的EventLoop数量为 1
      */
     public NioEventLoopGroup(){
          this(1);
     }

     /**
      * 构造函数，初始化EventLoopGroup维护的EventLoop
      *
      * @param threadNum
      */
     public NioEventLoopGroup(int threadNum){
          nioEventLoops = new NioEventLoop[threadNum];
          for(int i = 0; i < nioEventLoops.length; i++){
               nioEventLoops[i] = new NioEventLoop();
          }
     }


     /**
      * 把 channel 注册到 selector
      * 采用轮询的方法，注册到这个EventLoopGroup所维护的EventLoop的Selector上
      *
      * @param channel
      * @param interestOps
      */
     @Override
     public void register(Channel channel, int interestOps) {
          next().register(channel, interestOps);
     }

     /**
      * 提交任务
      * 如果是目标任务（TargetRunnable）类型，则提交给指定EventLoop，否则就轮询的策略，提交给下一个EvenLoop
      *
      * @param task
      */
     @Override
     public void execute(Runnable task) {
          next().execute(task);
     }

     /**
      * 提交一个只执行一次的定时任务
      *
      * @param task
      * @param delay
      * @param unit
      */
     @Override
     public void shedule(Runnable task, long delay, TimeUnit unit) {
          scheduleAtFixedRate(task, delay, -1, unit);
     }

     /**
      * 提交一个周期执行的定时任务
      *
      * @param task
      * @param initialDelay
      * @param period
      * @param unit
      */
     @Override
     public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
          next().scheduleAtFixedRate(task, initialDelay, period, unit);
     }

     /**
      * 返回轮询策略中的下一个EventLoop
      *
      * @return
      */
     @Override
     public EventLoop next() {
          return nioEventLoops[index.getAndIncrement() % nioEventLoops.length];
     }

}
