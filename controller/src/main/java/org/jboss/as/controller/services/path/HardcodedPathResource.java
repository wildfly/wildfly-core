/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import org.jboss.as.controller.registry.PlaceholderResource.PlaceholderResourceEntry;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class HardcodedPathResource extends PlaceholderResourceEntry {

    private volatile PathEntry entry;

    public HardcodedPathResource(String type, PathEntry entry) {
        super(type, entry.getName());
        this.entry = entry;
    }

    @Override
    public ModelNode getModel() {
        return entry.toModel();
    }

    @Override
    public boolean isModelDefined() {
        return true;
    }
}
