/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
        super(RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT);
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
