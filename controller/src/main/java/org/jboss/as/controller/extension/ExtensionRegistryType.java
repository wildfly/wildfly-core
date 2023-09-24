/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.extension;

import org.jboss.as.controller.ExtensionContext;

/**
 * Enum of places an extension registry can be added
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum ExtensionRegistryType {

    /**
     * The extension registry is for a standalone or managed server.
     */
    SERVER(ExtensionContext.ContextType.SERVER),

    /**
     * The extension registry is for the host model part in a host controller.
     */
    HOST(ExtensionContext.ContextType.HOST_CONTROLLER),

    /**
     * The extension registry is for the domain model part in a host controller running as a slave.
     * <em>NB</em> it is not known during bootup of the host.xml part if we are a slave or not. But
     * once we reach the domain part it is known.
     */
    MASTER(ExtensionContext.ContextType.DOMAIN),

    /**
     * The extension registry is for the domain model part in a host controller running as a slave.
     * <em>NB</em> it is not known during bootup of the host.xml part if we are a slave or not. But
     * once we reach the domain part it is known.
     */
    SLAVE(ExtensionContext.ContextType.DOMAIN);

    private final ExtensionContext.ContextType contextType;

    ExtensionRegistryType(ExtensionContext.ContextType contextType) {
        this.contextType = contextType;
    }

    public ExtensionContext.ContextType getContextType() {
        return contextType;
    }
}
