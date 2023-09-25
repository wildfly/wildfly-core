/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
