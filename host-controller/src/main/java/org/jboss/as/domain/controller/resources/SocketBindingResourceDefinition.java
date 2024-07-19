/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.resources;

import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.resource.AbstractSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.SocketBindingAddHandler;
import org.jboss.as.server.services.net.SocketBindingRemoveHandler;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a domain-level socket binding resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SocketBindingResourceDefinition extends AbstractSocketBindingResourceDefinition {

    public static final SocketBindingResourceDefinition INSTANCE = new SocketBindingResourceDefinition();

    private static final OperationStepHandler SHARED_HANDLER = ModelOnlyWriteAttributeHandler.INSTANCE;

    private SocketBindingResourceDefinition() {
        super(SocketBindingAddHandler.INSTANCE, SocketBindingRemoveHandler.INSTANCE, org.jboss.as.server.services.net.SocketBindingResourceDefinition.SOCKET_BINDING_CAPABILITY);
    }

    @Override
    protected OperationStepHandler getInterfaceWriteAttributeHandler() {
        return SHARED_HANDLER;
    }

    @Override
    protected OperationStepHandler getPortWriteAttributeHandler() {
        return SHARED_HANDLER;
    }

    @Override
    protected OperationStepHandler getFixedPortWriteAttributeHandler() {
        return SHARED_HANDLER;
    }

    @Override
    protected OperationStepHandler getMulticastAddressWriteAttributeHandler() {
        return SHARED_HANDLER;
    }

    @Override
    protected OperationStepHandler getMulticastPortWriteAttributeHandler() {
        return SHARED_HANDLER;
    }

    @Override
    protected OperationStepHandler getClientMappingsWriteAttributeHandler() {
        return SHARED_HANDLER;
    }
}
