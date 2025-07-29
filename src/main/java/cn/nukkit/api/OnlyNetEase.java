package cn.nukkit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 使用此注解的类、方法、字段等仅适用于网易客户端。
 * <p>
 * use this annotation to mark the class, method, field, etc. only for netease client.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.CONSTRUCTOR,
        ElementType.LOCAL_VARIABLE,
        ElementType.ANNOTATION_TYPE,
        ElementType.PACKAGE,
        ElementType.TYPE_PARAMETER,
        ElementType.TYPE_USE,
        ElementType.MODULE,
        ElementType.RECORD_COMPONENT})
public @interface OnlyNetEase {
}
