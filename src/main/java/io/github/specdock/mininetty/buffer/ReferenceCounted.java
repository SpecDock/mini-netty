package io.github.specdock.mininetty.buffer;

/**
 * 引用计数对象的最小契约。
 *
 * <p>堆外内存不能只依赖 Java GC 释放，pipeline 中的 ByteBuf、切片和组合 buffer
 * 会共享同一块物理内存，因此需要通过 retain/release 明确表达所有权转移。</p>
 */
public interface ReferenceCounted {
    /** 当前仍持有该对象的引用数量。 */
    int refCnt();

    /** 增加一次持有关系，通常在切片或跨组件传递时调用。 */
    ReferenceCounted retain();

    /**
     * 释放一次持有关系。
     *
     * @return true 表示引用归零，底层资源已经被真正释放或级联释放
     */
    boolean release();
}
