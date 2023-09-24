/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

/**
 *
 * @author Alexey Loubyansky
 */
public enum CliEvent {

    CONNECTED("CONNECTED"),

    DISCONNECTED("DISCONNECTED");

    private String name;

    CliEvent(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
