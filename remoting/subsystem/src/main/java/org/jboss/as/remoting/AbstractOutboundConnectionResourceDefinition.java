/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Jaikiran Pai
 */
abstract class AbstractOutboundConnectionResourceDefinition extends SimpleResourceDefinition {

    static final String OUTBOUND_CONNECTION_CAPABILITY_NAME = "org.wildfly.remoting.outbound-connection";
    static final String OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.outbound-socket-binding";
    static final RuntimeCapability<Void> OUTBOUND_CONNECTION_CAPABILITY =
            RuntimeCapability.Builder.of(OUTBOUND_CONNECTION_CAPABILITY_NAME, true, AbstractOutboundConnectionService.class)
                    .build();


    protected AbstractOutboundConnectionResourceDefinition(final Parameters parameters) {
        super(parameters.addCapabilities(OUTBOUND_CONNECTION_CAPABILITY));
    }

    public abstract void registerChildren(ManagementResourceRegistration resourceRegistration);

    /**
     * Returns the write attribute handler for the <code>attribute</code>
     * @param attribute The attribute for which the write operation handler is being queried
     * @return the handler
     */
    protected abstract OperationStepHandler getWriteAttributeHandler(final AttributeDefinition attribute);

}
