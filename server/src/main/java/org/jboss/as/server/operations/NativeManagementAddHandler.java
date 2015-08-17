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

package org.jboss.as.server.operations;

import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.ATTRIBUTE_DEFINITIONS;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.NATIVE_MANAGEMENT_CAPABILITY;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SECURITY_REALM;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SOCKET_BINDING;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SOCKET_BINDING_CAPABILITY_NAME;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.NativeManagementResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * The Add handler for the Native Interface when running a standalone server.
 *
 * @author Kabir Khan
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class NativeManagementAddHandler extends AbstractAddStepHandler {

    public static final NativeManagementAddHandler INSTANCE = new NativeManagementAddHandler();
    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;

    public NativeManagementAddHandler() {
        super(NATIVE_MANAGEMENT_CAPABILITY, ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.getProcessType() != ProcessType.EMBEDDED_SERVER || context.getRunningMode() != RunningMode.ADMIN_ONLY;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        final ServiceTarget serviceTarget = context.getServiceTarget();

        final ServiceName endpointName = ManagementRemotingServices.MANAGEMENT_ENDPOINT;
        final String hostName = WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.NODE_NAME, null);
        NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostName, context.getServiceRegistry(false));
        installNativeManagementConnector(context, model, endpointName, serviceTarget);
    }


    static void installNativeManagementConnector(final OperationContext context, final ModelNode model, final ServiceName endpointName, final ServiceTarget serviceTarget) throws OperationFailedException {

        final String bindingName = SOCKET_BINDING.resolveModelAttribute(context, model).asString();
        ServiceName socketBindingServiceName = context.getCapabilityServiceName(SOCKET_BINDING_CAPABILITY_NAME, bindingName, SocketBinding.class);

        String securityRealm = null;
        final ModelNode realmNode = SECURITY_REALM.resolveModelAttribute(context, model);
        if (realmNode.isDefined()) {
            securityRealm = realmNode.asString();
        } else {
            ServerLogger.ROOT_LOGGER.nativeManagementInterfaceIsUnsecured();
        }

        ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.server.temp.dir");
        RemotingServices.installSecurityServices(serviceTarget, ManagementRemotingServices.MANAGEMENT_CONNECTOR, securityRealm, null, tmpDirPath);
//        final OptionMap options = OptionMap.builder().set(RemotingOptions.HEARTBEAT_INTERVAL, 30000).set(Options.READ_TIMEOUT, 65000).getMap();
        final OptionMap options = createConnectorOptions(context, model);
        ManagementRemotingServices.installConnectorServicesForSocketBinding(serviceTarget, endpointName,
                    ManagementRemotingServices.MANAGEMENT_CONNECTOR,
                    socketBindingServiceName, options);

    }

    private static OptionMap createConnectorOptions(final OperationContext context, final ModelNode model) throws OperationFailedException {
        Builder builder = OptionMap.builder();

        builder.set(RemotingOptions.SASL_PROTOCOL, NativeManagementResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        ModelNode serverName = NativeManagementResourceDefinition.SERVER_NAME.resolveModelAttribute(context, model);
        if (serverName.isDefined()) {
            builder.set(RemotingOptions.SERVER_NAME, serverName.asString());
        }

        return builder.getMap();
    }

}
