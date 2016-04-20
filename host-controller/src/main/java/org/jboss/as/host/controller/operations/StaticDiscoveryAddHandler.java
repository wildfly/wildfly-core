/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATIC_DISCOVERY;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.discovery.StaticDiscovery;
import org.jboss.as.host.controller.discovery.StaticDiscoveryResourceDefinition;
import org.jboss.as.network.NetworkUtils;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the static discovery option resource's add operation.
 *
 * @author Farah Juma
 */
public class StaticDiscoveryAddHandler extends AbstractDiscoveryOptionAddHandler {

    private final LocalHostControllerInfoImpl hostControllerInfo;

    /**
     * Create the StaticDiscoveryAddHandler.
     *
     * @param hostControllerInfo the host controller info
     */
    public StaticDiscoveryAddHandler(final LocalHostControllerInfoImpl hostControllerInfo) {
        this.hostControllerInfo = hostControllerInfo;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        if (context.isBooting()) {
            populateHostControllerInfo(hostControllerInfo, context, operation);
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation,
            final Resource resource) throws  OperationFailedException {
        updateOptionsAttribute(context, operation, STATIC_DISCOVERY);
    }

    protected void populateHostControllerInfo(LocalHostControllerInfoImpl hostControllerInfo, OperationContext context,
            ModelNode model) throws OperationFailedException {
        ModelNode hostNode = StaticDiscoveryResourceDefinition.HOST.resolveModelAttribute(context, model);
        ModelNode portNode = StaticDiscoveryResourceDefinition.PORT.resolveModelAttribute(context, model);
        ModelNode protocolNode = DomainControllerWriteAttributeHandler.PROTOCOL.resolveModelAttribute(context, model);
        String remoteDcHost = (!hostNode.isDefined()) ? null : NetworkUtils.formatPossibleIpv6Address(hostNode.asString());
        int remoteDcPort = (!portNode.isDefined()) ? -1 : portNode.asInt();
        String remoteDcProtocol = protocolNode.asString();
        StaticDiscovery staticDiscoveryOption = new StaticDiscovery(remoteDcProtocol, remoteDcHost, remoteDcPort);
        hostControllerInfo.addRemoteDomainControllerDiscoveryOption(staticDiscoveryOption);
    }
}
