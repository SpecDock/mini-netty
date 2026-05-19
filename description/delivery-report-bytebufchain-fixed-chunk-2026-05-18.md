# 交付报告

## 原始需求

用户要求阅读 `Class_Description.md`，并完成 `TODO.md` 中关于统一固定分片内存池、`ByteBufChain` 出站零多余拷贝、长期迁移并弱化 `CompositeByteBuf` 的完整改造需求。经确认，本次范围为 TODO 范围 D：完整实现 TODO 中的验收项，允许删除 `SimpleByteArray.java`，并将池化 chunk 默认大小调整为 `4 * 1024`。

## 需求提纯

本次交付聚焦于 mini-netty buffer/channel/codec/heartbeat 主链路的数据模型收敛：

- 将常规业务入站/出站容器统一到 `ByteBufChain`，以固定大小池化 direct chunk 作为底层内存单元。
- 出站 `ByteBufChain` 使用 `SocketChannel.write(ByteBuffer[])` gathering write，禁止合并为连续 `byte[]` 或单个 direct buffer。
- `StringEncoder` 直接通过 `CharsetEncoder` 写入 `ByteBufChain` 的 writable NIO view，避免 `String -> byte[] -> direct ByteBuf` 中间路径。
- 裸 `ByteBuffer` 不再作为出站消息兼容入口；业务必须通过 coder/encoder 输出 `ByteBufChain` 或其他框架内 `ReferenceCounted` 类型。
- 移除 `SimpleByteArray` 及其主链路分支，`StringDecoder` 和心跳处理器直接支持 `ByteBufChain`。
- 保留 `CompositeByteBuf` 作为遗留兼容层，但不再作为 frame/header 组合的常规主路径。

## 架构方案

架构方案采用“固定 chunk + 链式聚合 + gathering write”的分层模型：

1. **内存分配层**：`PooledByteBufAllocator` 使用 `4 * 1024` 作为默认 chunk 大小；池化 root `ByteBuf` 在引用计数归零后回交 allocator，由 allocator 决策回池或释放。
2. **基础 chunk 层**：`ByteBuf` 保留为承载 `ByteBuffer`、读写索引、引用计数和 retained slice 生命周期的内部 segment，并新增 writable NIO view 与 writer index 推进能力。
3. **链式容器层**：`ByteBufChain` 维护固定 chunk 链表和 cached readable bytes，提供跨 chunk 读写、`nioBuffers(maxCount)`、`writableNioBuffer()`、`advanceWriterIndex(int)`、append/appendChain 等能力。
4. **出站缓冲层**：`ChannelOutboundBuffer` 识别 `ByteBufChain`、`ByteBuf`、`CompositeByteBuf` 等框架内 `ReferenceCounted` 类型；对 `ByteBufChain`/`CompositeByteBuf` 使用 gathering write，对 `ByteBuf` 使用单 buffer write，并拒绝裸 `ByteBuffer` 出站。
5. **编解码与心跳层**：`StringEncoder`、`LengthFieldBasedFrameEncoder`、`ClientHeartbeatHandler`、`ServerHeartbeatHandler` 均改为通过 `ctx.executor().allocator()` 创建或扩展 `ByteBufChain`；`StringDecoder`、frame decoder 和心跳读路径显式处理 `ByteBufChain`。

## 变更文件

本次流水线记录的代码与测试变更如下：

- `src/main/java/io/github/specdock/mininetty/buffer/ByteBuf.java`：增强 chunk writable NIO view 与 writer index 推进能力。
- `src/main/java/io/github/specdock/mininetty/buffer/ByteBufChain.java`：重构为固定 chunk 链式主容器，维护 cached readable bytes，支持跨 chunk 读写与 gathering write 视图。
- `src/main/java/io/github/specdock/mininetty/buffer/ByteBufferReference.java`：已删除，裸 `ByteBuffer` 不再作为严格出站边界的兼容入口。
- `src/main/java/io/github/specdock/mininetty/buffer/CompositeByteBuf.java`：保留为兼容层并适配现有引用计数/多段视图语义。
- `src/main/java/io/github/specdock/mininetty/buffer/SimpleByteArray.java`：删除历史 `byte[]` 切片包装类型。
- `src/main/java/io/github/specdock/mininetty/channel/EventLoop.java`、`src/main/java/io/github/specdock/mininetty/channel/nio/NioEventLoop.java`：暴露 EventLoop 绑定 allocator。
- `src/main/java/io/github/specdock/mininetty/channel/ChannelOutboundBuffer.java`：支持 `ByteBufChain` gathering write，拒绝裸 `ByteBuffer` 出站，并保留 null 入参防御。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/StringEncoder.java`：改为直接编码到 `ByteBufChain`，并修复 `CharsetEncoder` UNDERFLOW 处理。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/StringDecoder.java`：支持直接消费 `ByteBufChain` 并移除 `SimpleByteArray` 分支。
- `src/main/java/io/github/specdock/mininetty/channel/handler/codec/LengthFieldBasedFrameEncoder.java`、`LengthFieldBasedFrameDecoder.java`：frame/header 链路迁移到 `ByteBufChain` 兼容模型。
- `src/main/java/io/github/specdock/mininetty/channel/handler/timeout/ClientHeartbeatHandler.java`、`ServerHeartbeatHandler.java`：心跳 header 与入站解析支持 `ByteBufChain`。
- `src/test/java/io/github/specdock/mininetty/buffer/ByteBufChainTest.java`：新增 `ByteBufChain` gathering write、跨 chunk 读写/skip 等测试。
- `src/test/java/io/github/specdock/mininetty/buffer/ByteBufferReferenceTest.java`：已删除，兼容包装测试由严格拒绝裸 `ByteBuffer` 的出站边界测试取代。
- `src/test/java/io/github/specdock/mininetty/channel/ChannelOutboundBufferTest.java`：新增裸 `ByteBuffer` 出站拒绝测试。
- `src/test/java/io/github/specdock/mininetty/channel/handler/codec/CodecAndHeartbeatChainTest.java`：新增编解码与心跳链路对 `ByteBufChain` 的集成测试。
- `description/delivery-report-bytebufchain-fixed-chunk-2026-05-18.md`：新增本交付报告。

## 测试结果

测试命令：

```bash
mvn test
```

测试结果：通过。

关键结果：

- Tests run: 14
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

补测过程中曾暴露 `StringEncoder` 在 `CharsetEncoder` 返回 `UNDERFLOW` 时误调用 `throwException()` 导致 `BufferUnderflowException` 的问题，已进行最小修复并重跑全量测试通过。

## 审核结论

审计结果为通过。实现与 TODO 验收项保持一致：

- 默认 chunk 大小已调整为 `4 * 1024`。
- `ByteBufChain` 已维护 readable bytes 缓存，并支持 `nioBuffers`、writable NIO view 与 writer index 推进。
- `ChannelOutboundBuffer` 对 `ByteBufChain` 使用 gathering write，未进行合并拷贝。
- `StringEncoder` 未再调用 `String.getBytes()`，改为直接编码到 direct chunk 链。
- `ByteBuffer` 出站不再走 `ByteBuffer -> byte[] -> direct ByteBuf`，也不再保留裸 `ByteBuffer` 兼容入口；调用方应通过 coder/encoder 输出 `ByteBufChain`。
- `SimpleByteArray` 已删除，主代码不再保留相关特殊分支。
- `StringDecoder` 与客户端/服务端心跳处理器已支持 `ByteBufChain`。
- 池化 chunk release 归零后回流 allocator 的生命周期语义已由测试覆盖。
- 未发现明显超出 TODO 范围的过度实现，`CompositeByteBuf` 保留为兼容层符合迁移策略。

## 后续建议

- 后续可在调用方完全迁移后继续弱化或删除 `CompositeByteBuf`，但不建议在当前交付中扩大改动面。
- 可进一步优化 `StringDecoder`，由当前可接受的边界转换方案演进为基于 `CharsetDecoder` 直接消费多段 `ByteBuffer` 视图。
- 建议持续通过测试或静态检查约束常规路径，避免重新引入非默认容量 direct buffer 或 `String -> byte[] -> direct ByteBuf` 编码路径。
