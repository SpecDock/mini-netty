package io.github.specdock.mininetty.channel;

import java.net.SocketAddress;

/**
 * @author specdock
 * @Date 2026/1/15
 * @Time 21:12
 */
public interface ChannelPipeline {
    // --- Handler 链表管理 API (返回 Pipeline 自身以支持链式调用) ---

    /**
     * 在管道最前面（head 之后）添加处理器
     */
    ChannelPipeline addFirst(ChannelHandler handler);

    /**
     * 在管道最后面（tail 之前）添加处理器
     */
    ChannelPipeline addLast(ChannelHandler handler);

    /**
     * 从链表中移除指定的处理器实例
     */
    ChannelPipeline remove(ChannelHandler handler);



    // --- 入站事件触发 API (Inbound Events: 从 Head 向 Tail 传播) ---

    ChannelPipeline fireChannelRegistered();

    ChannelPipeline fireChannelActive();

    /**
     * 触发数据读取事件，将消息 msg 传入管道
     */
    ChannelPipeline fireChannelRead(Object msg);

    ChannelPipeline fireChannelReadComplete();

    ChannelPipeline fireExceptionCaught(Throwable cause);

    ChannelPipeline fireUserEventTriggered(Object event);


    // --- 出站事件请求 API (Outbound Events: 从 Tail 向 Head 传播) ---
    // TODO 待实现：之后将完成 Future 机制，现在用 void 作为返回值

    void bind(SocketAddress localAddress);

    void connect(SocketAddress remoteAddress);

    /**
     * 将消息写入缓冲区，返回代表异步 IO 结果的 Future
     */
    void write(Object msg);

    /**
     * 冲刷缓冲区，将数据写入 Socket 发送缓冲区
     */
    ChannelPipeline flush();

    /**
     * 结合写入与冲刷动作
     */
    void writeAndFlush(Object msg);

    void close();

    void deregister();


    // --- 组件检索与辅助 API ---

    /**
     * 获取与该管道绑定的 Channel 实例
     */
    Channel channel();

    /**
     * 根据名称查找对应的 ChannelHandlerContext
     */
    ChannelHandlerContext context(String name);

    /**
     * 根据处理器实例查找对应的 ChannelHandlerContext
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * 获取链表中第一个业务处理器（非 head）
     */
    ChannelHandler first();

    /**
     * 获取链表中最后一个业务处理器（非 tail）
     */
    ChannelHandler last();
}

