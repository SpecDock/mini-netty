package io.github.specdock.mininetty.channel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author specdock
 * @Date 2026/3/11
 * @Time 16:56
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FrameEncoder {
}
