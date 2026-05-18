# 交付报告

## 原始需求

扫描 `Class_Description.md` 与 `TODO.md`，实现 TODO 中关于 `ByteBuf` 池化回收的需求：由 `PooledByteBufAllocator` 分配的 `ByteBuf` 在使用完毕后不应绕过分配器直接释放 native/direct memory，而应在引用计数归零时回交 allocator，由 allocator 统一判断回池复用或释放底层内存。

## 需求提纯

- 池化 root `ByteBuf` 的 `release()` 在引用计数归零时必须回调所属 `PooledByteBufAllocator`。
- allocator 作为唯一回收决策点：默认容量且池未满时回池，非默认容量或池满时释放底层内存。
- 非默认容量对象仍不进入固定容量池，但释放决策必须经过 allocator。
- 水位线不新增配置，沿用既有 `MAX_POOL_SIZE`/池满语义。
- 回池后的对象在再次被 `allocate()` 返回前，对旧 root 句柄调用 `writeByte` 等访问方法必须抛出 `IllegalStateException`，且不能破坏后续正常复用。

## 架构方案

本次变更采用最小侵入式生命周期委托方案：`ByteBuf` root 持有 allocator 引用，并在最后一个引用释放时将 root 交回 allocator；`PooledByteBufAllocator.recycle(ByteBuf)` 统一执行容量、池容量和引用状态校验，决定回池或释放。为隔离对象复用前后的生命周期，`ByteBuf` 增加 generation 标识；回池时推进 generation 并保持 `refCnt=0`，从池中重新分配时再恢复引用计数和快照，从而阻断旧句柄对空闲池对象或新生命周期对象的误访问。

## 变更文件

- `src/main/java/io/github/specdock/mininetty/buffer/ByteBuf.java`
  - 增加 allocator 归属记录与 generation 生命周期校验。
  - `release()` 引用归零后，池化 root 委托 allocator 回收，非池化 root 继续释放底层 direct memory。
  - 增加 allocator 内部回收/复用辅助方法，支持回池标记、复用重置、容量与 root 校验。
- `src/main/java/io/github/specdock/mininetty/buffer/PooledByteBufAllocator.java`
  - 将池容器调整为 `ConcurrentLinkedQueue`，降低并发访问风险。
  - `allocate()` 从池取对象时调用 `resetForReuse()` 激活新生命周期。
  - `recycle()` 统一处理默认容量回池、非默认容量释放、池满释放及 slice 误传防护。
- `pom.xml`
  - 增加 JUnit 测试依赖，用于回收行为单元测试。
- `src/test/java/io/github/specdock/mininetty/buffer/PooledByteBufAllocatorRecycleTest.java`
  - 覆盖回池后旧句柄不可访问、默认容量复用、非默认容量不进入固定池、retained slice 延迟回收等场景。
- `TODO.md`
  - 将 ByteBuf 池化回收 TODO 标记为已完成，并补充实现说明。
- `Class_Description.md`
  - 同步更新 `ByteBuf` 与 `PooledByteBufAllocator` 职责描述及新增测试类说明。
- `description/delivery-report-bytebuf-pool-recycle.md`
  - 新增本交付报告。

## 测试结果

- 测试命令：`mvn test`
- 测试状态：通过
- 测试摘要：Maven test suite passed. Tests run: 4, Failures: 0, Errors: 0, Skipped: 0.

## 审核结论

审核通过。实现已覆盖 TODO 与用户澄清要求：池化 `ByteBuf` 在引用计数归零后先回交 allocator；allocator 按默认容量和池容量状态统一判断回池或释放；非默认容量对象不进入固定池；回池后的旧 root 句柄在重新分配前不可访问；retained slice 场景不会提前回池。未引入新池类型、水位线配置、后台清理线程，也未修改 `ReferenceCounted` 接口。

## 后续建议

- 如后续需要更精细的内存治理，可在独立需求中引入可配置高低水位线与指标监控。
- 可在并发压力测试中进一步验证 allocator 队列在多线程 allocate/release 场景下的稳定性。
