/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.jboss.as.patching.Constants.ADD_ONS;
import static org.jboss.as.patching.Constants.LAYERS;

/**
 * The layer type.
 *
 * @author Emanuel Muckenhuber
 */
public enum LayerType {

    Layer("layer", LAYERS),
    AddOn("add-on", ADD_ONS),
    // Maybe add identity, since we could have also changes that affect the identity itself
    ;

    private final String name;
    private final String dirName;
    LayerType(String name, String dirName) {
        this.name = name;
        this.dirName = dirName;
    }

    public String getName() {
        return name;
    }

    public String getDirName() {
        return dirName;
    }
}
