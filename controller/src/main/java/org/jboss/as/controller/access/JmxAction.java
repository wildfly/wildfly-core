/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;


/**
 * Encapsulates authorization information about an MBean call.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JmxAction {

    private static final Set<Action.ActionEffect> WRITES = Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS,
            Action.ActionEffect.READ_RUNTIME, Action.ActionEffect.WRITE_RUNTIME));
    private static final Set<Action.ActionEffect> READS = Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS,
            Action.ActionEffect.READ_RUNTIME));

    private final String methodName;
    private final String attribute;
    private final Impact impact;

    public JmxAction(String methodName, Impact impact) {
        this(methodName, impact, null);
    }

    public JmxAction(String methodName, Impact impact, String attribute) {
        this.methodName = methodName;
        this.impact = impact;
        this.attribute = attribute;
    }

    /**
     * Gets the impact of the call
     *
     * @return the impact
     */
    public Impact getImpact() {
        return impact;
    }

    /**
     * Gets the {@link javax.management.MBeanServer} method name that was called
     * @return the method name. Will not be {@code null}
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the name of the affected MBean attribute, if {@link #getMethodName() method name} is for
     * a method that reads or writes attributes (i.e. {@code getAttribute}, {@code setAttribute}, {@code setAttributes}).
     * @return the attribute name, or {@code null} if the method being invoked is not related to attributes
     */
    public String getAttributeName() {
        return attribute;
    }

    /**
     * Gets the effects of this action.
     *
     * @return the effects. Will not be {@code null}
     */
    public Set<Action.ActionEffect> getActionEffects() {
        switch(getImpact()) {
            case CLASSLOADING:
            case WRITE:
                return WRITES;
            case READ_ONLY:
                return READS;
            default:
                throw new IllegalStateException();
        }

    }

    /**
     * The impact of the call
     */
    public enum Impact {
        /** The call is read-only */
        READ_ONLY,
        /** The call writes data */
        WRITE,
        /** The call involves the MBeanServer methods that involve accessing classloaders and or using them
         * to deserialize bytes or instantiate objects. In the standard RBAC
         * implementation these will only work for a (@link org.jboss.as.controller.access.rbac.StandardRole#SUPERUSER} or a (@link org.jboss.as.controller.access.rbac.StandardRole#ADMINISTRATOR} */
        CLASSLOADING
    }
}
