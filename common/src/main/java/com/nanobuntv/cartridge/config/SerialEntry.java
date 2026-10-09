package com.nanobuntv.cartridge.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A settings field kept in its file ({@link SettingsFile}), with the comment written above it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SerialEntry {
    String comment() default "";
}
