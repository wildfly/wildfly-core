/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.model.jvm;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum JvmType {
    ORACLE,
    IBM,
    OTHER,
    @Deprecated
    SUN
}
