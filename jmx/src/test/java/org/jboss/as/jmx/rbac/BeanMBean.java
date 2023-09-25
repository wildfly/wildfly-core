/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.rbac;

public interface BeanMBean {
    int getAttr();
    void setAttr(int i);
    void method();
}