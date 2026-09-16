/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * The layer type.
 *
 * @author Emanuel Muckenhuber
 */
public enum LayerType {

    Layer("layer"),
    AddOn("add-on"),
    // Maybe add identity, since we could have also changes that affect the identity itself
    ;

    private final String name;
    LayerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
