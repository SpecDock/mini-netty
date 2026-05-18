# 交付报告
## 原始需求
用户要求基于既有优化思路对 mini-netty 项目执行全链路代码改造，并说明代码已完成 git 提交，需要对本轮交付结果进行归档汇总。

## 需求提纯
本次交付目标提纯为：在保持最小必要改动与既有 API 兼容性的前提下，将 pipeline 入站/出站主链路尽量改造为以堆外 `ByteBuf` / `CompositeByteBuf` 承载数据，降低协议编解码、`ChannelOutboundBuffer` 与 `NioSocketChannel` 写出环节的 `byte[]` payload 拷贝；仅在 `StringEncoder`、`StringDecoder` 等语义类型转换边界允许发生必要转换；同时引入引用计数释放语义，避免提前释放、重复释放与缓冲区泄露。

## 架构方案
本次方案采用最小 Netty-like 缓冲区抽象与引用计数生命周期模型：

- 在 buffer 层新增 `ReferenceCounted` 抽象，并由 `ByteBuf`、`CompositeByteBuf` 承载堆外缓冲区与组合缓冲区生命周期。
- 通过 `PooledByteBufAllocator` 与 `ByteBufChain` 组织入站累计、切片与组合读取，减少协议拆包/聚合过程中的数组复制。
- 在 codec 层以 `ByteBuf` / `CompositeByteBuf` 作为帧解码与帧编码的主数据结构，仅在字符串编解码等业务语义边界执行字节与字符串转换。
- 在出站链路中由 `ChannelOutboundBuffer` 统一持有 `ReferenceCounted` 消息，并在写成功、失败或 Channel 关闭时释放引用。
- 在 `NioSocketChannel` 暴露单缓冲区写与 `ByteBuffer[]` gathering write 能力，使 `CompositeByteBuf` 可映射为 NIO 多缓冲区写出。
- 对心跳处理器同步适配新的缓冲区消息模型，保证控制消息与业务消息在同一引用计数语义下流转。

## 变更文件
本次确认变更域如下：

- `src/main/java/io/github/specdock/mininetty/buffer/ReferenceCounted.java`：新增引用计数接口，定义 `refCnt`、`retain`、`release` 生命周期契约。
- `src/main/java/io/github/specdock/mininetty/buffer/ByteBuf.java`：引入堆外 `ByteBuffer` 包装、读写索引、切片视图、NIO buffer 暴露与释放校验能力。
- `src/main/java/io/github/specdock/mininetty/buffer/CompositeByteBuf.java`：新增组合缓冲区，支持跨组件读取、跳过、`nioBuffers()` 聚合与级联释放。
- `src/main/java/io/github/specdock/mininetty/buffer/PooledByteBufAllocator.java`：提供堆外缓冲区分配入口。
- `src/main/java/io/github/specdock/mininetty/buffer/ByteBufChain.java`：支持入站链路缓冲区链式累计与帧数据提取。
- `src/main/java/io/github/specdock/mininetty/channel/socket/SocketChannel.java`：补充 NIO 单缓冲区与多缓冲区写出接口。
- `src/main/java/io/github/specdock/mininetty/channel/socket/nio/NioSocketChannel.java`：实现 `ByteBuffer` 与 `ByteBuffer[]` 写出，支撑 gathering write。
- `src/main/java/io/github/specdock/mininetty/channel/ChannelOutboundBuffer.java`：改造出站缓冲为 `ReferenceCounted` 消息队列，写完成或异常时释放资源，并修复空队列时 OP_WRITE 注销逻辑。
- `src/main/java/io/github/specdock/mininetty/channel/DefaultChannelPipeline.java`：适配新的入站/出站消息流转模型。
- `src/main/java/io/github/specdock/mininetty/channel/ChannelInitializer.java`：移除无效导入，修复编译失败问题。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/LengthFieldBasedFrameDecoder.java`：适配 `ByteBuf` / `CompositeByteBuf` 帧解码，并修复 `lengthFieldLength` 1-4 字节兼容性。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/LengthFieldBasedFrameEncoder.java`：适配零拷贝编码输出，并在异常路径释放已分配资源。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/StringDecoder.java`：在字符串解码边界执行必要类型转换。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/StringEncoder.java`：在字符串编码边界执行必要类型转换。
- `src/main/java/io/github/specdock/mininetty/channel/handler/timeout/ClientHeartbeatHandler.java`：适配心跳客户端消息的缓冲区模型。
- `src/main/java/io/github/specdock/mininetty/channel/handler/timeout/ServerHeartbeatHandler.java`：适配心跳服务端消息的缓冲区模型。
- `description/delivery-report-2026-05-17.md`：新增本次交付归档报告。

## 测试结果
- 测试命令：`mvn -q -DskipTests=false test`
- 测试结果：通过。Maven 测试执行完成，无错误输出。

## 审核结论
审核通过。本次实现覆盖澄清后的零拷贝主链路目标，完成引用计数释放语义、堆外缓冲区流转、组合缓冲区 gathering write、出站缓冲生命周期管理、协议编解码适配与心跳链路适配。已修复审核阶段发现的 OP_WRITE 空写注销、`lengthFieldLength` 兼容性、编码异常释放以及 `ChannelInitializer` 无效导入问题。当前无开放阻塞项。

## 后续建议
- 建议后续补充面向引用计数的单元测试，覆盖正常释放、异常释放、重复释放与组合缓冲区级联释放路径。
- 建议补充高水位/低水位或背压相关测试，以验证 `ChannelOutboundBuffer` 在大 payload 与部分写场景下的稳定性。
- 建议在压测环境对比数组拷贝链路与堆外缓冲区链路的吞吐、延迟、GC 与直接内存占用指标。
