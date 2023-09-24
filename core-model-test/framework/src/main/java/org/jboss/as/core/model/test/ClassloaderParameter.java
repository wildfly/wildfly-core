/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ClassloaderParameter {

    private volatile ClassLoader classloader;

    void setClassLoader(ClassLoader classloader) {
        this.classloader = classloader;
    }

    ClassLoader getClassLoader() {
        return classloader;
    }
}
