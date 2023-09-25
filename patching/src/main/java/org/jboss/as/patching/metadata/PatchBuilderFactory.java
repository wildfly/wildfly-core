/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.metadata;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchBuilderFactory {

    private PatchBuilder builder;

    public void setBuilder(PatchBuilder builder) {
        this.builder = builder;
    }

    public PatchBuilder getBuilder() {
        return builder;
    }
}
