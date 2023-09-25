/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

/**
 * @author Emanuel Muckenhuber
 */
public class TestClass {

    private final String property;

    public TestClass(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }

}
