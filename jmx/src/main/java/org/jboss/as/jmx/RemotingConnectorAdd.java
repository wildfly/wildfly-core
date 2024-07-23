/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import static org.jboss.as.jmx.JMXSubsystemAdd.getDomainName;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.Endpoint;

/**
 * @author Stuart Douglas
 */
class RemotingConnectorAdd extends AbstractAddStepHandler {

    static final RemotingConnectorAdd INSTANCE = new RemotingConnectorAdd();

    private RemotingConnectorAdd() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
       return super.requiresRuntime(context) && (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {

        boolean useManagementEndpoint = RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.resolveModelAttribute(context, model).asBoolean();

        ServiceName remotingCapability;
        if (!useManagementEndpoint) {
            // Use the remoting capability
            // if (context.getProcessType() == ProcessType.DOMAIN_SERVER) then DomainServerCommunicationServices
            // installed the "remoting subsystem" endpoint and we don't even necessarily *have to* have a remoting
            // subsystem and possibly we could skip adding the requirement for its capability. But really, specifying
            // not to use the management endpoint and then not configuring a remoting subsystem is a misconfiguration,
            // and we should treat it as such. So, we add the requirement no matter what.
            context.requireOptionalCapability(RemotingConnectorResource.REMOTING_CAPABILITY, RemotingConnectorResource.REMOTE_JMX_CAPABILITY.getName(),
                        RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.getName());
            remotingCapability = context.getCapabilityServiceName(RemotingConnectorResource.REMOTING_CAPABILITY, Endpoint.class);
        } else {
            remotingCapability = ManagementRemotingServices.MANAGEMENT_ENDPOINT;
        }
        // Read the model for the JMX subsystem to find the domain name for the resolved/expressions models (if they are exposed).
        PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
        PathAddress parentAddress = address.subAddress(0, address.size() - 1);
        ModelNode jmxSubsystemModel = Resource.Tools.readModel(context.readResourceFromRoot(parentAddress, true));
        String resolvedDomain = getDomainName(context, jmxSubsystemModel, CommonAttributes.RESOLVED);
        String expressionsDomain = getDomainName(context, jmxSubsystemModel, CommonAttributes.EXPRESSION);

        RemotingConnectorService.addService(context.getServiceTarget(), remotingCapability, resolvedDomain, expressionsDomain);
    }
}
