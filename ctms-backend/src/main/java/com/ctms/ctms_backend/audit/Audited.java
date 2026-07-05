package com.ctms.ctms_backend.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion should produce an {@link AuditLog} entry
 * automatically, with the method's arguments serialized as the after-value. For cases needing a
 * real before/after diff (e.g. field-level changes), call {@link AuditService} directly instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    String entityName();

    /** SpEL expression evaluated against the method arguments to resolve the entity id, e.g. "#id" or "#study.id". */
    String entityId();

    String action();
}
