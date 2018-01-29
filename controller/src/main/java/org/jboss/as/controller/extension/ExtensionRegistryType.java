/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
