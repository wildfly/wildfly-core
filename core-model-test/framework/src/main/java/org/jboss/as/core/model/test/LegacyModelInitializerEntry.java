/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.Serializable;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class LegacyModelInitializerEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PathAddress parentAddress;
    private final PathElement relativeResourceAddress;
    private final ModelNode model;
    private final String[] capabilities;

    public LegacyModelInitializerEntry(PathAddress parentAddress, PathElement relativeResourceAddress, ModelNode model, String... capabilities) {
        this.parentAddress = parentAddress;
        this.relativeResourceAddress = checkNotNullParam("relativeResourceAddress", relativeResourceAddress);
        this.model = model;
        this.capabilities = capabilities == null || capabilities.length == 0 ? null : capabilities;
    }

    public PathAddress getParentAddress() {
        return parentAddress;
    }

    public PathElement getRelativeResourceAddress() {
        return relativeResourceAddress;
    }

    public ModelNode getModel() {
        return model;
    }

    public String[] getCapabilities() {
        return capabilities;
    }

}
