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


import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.remoting.RemotingServices.REMOTING_BASE;
import static org.jboss.as.remoting.management.ManagementRemotingServices.MANAGEMENT_CONNECTOR;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.ATTRIBUTE_DEFINITIONS;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SOCKET_BINDING;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SOCKET_BINDING_CAPABILITY_NAME;

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.BaseNativeInterfaceAddStepHandler;
import org.jboss.as.controller.management.NativeInterfaceCommonPolicy;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.server.sasl.SaslAuthenticationFactory;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The Add handler for the Native Interface when running a standalone server.
 *
 * @author Kabir Khan
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class NativeManagementAddHandler extends BaseNativeInterfaceAddStepHandler {

    public static final NativeManagementAddHandler INSTANCE = new NativeManagementAddHandler();
    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;

    public NativeManagementAddHandler() {
        super(ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return super.requiresRuntime(context)
                && (context.getProcessType() != ProcessType.EMBEDDED_SERVER || context.getRunningMode() != RunningMode.ADMIN_ONLY);
    }

    @Override
    protected List<ServiceName> installServices(OperationContext context, NativeInterfaceCommonPolicy commonPolicy, ModelNode model)
            throws OperationFailedException {
        final ServiceTarget serviceTarget = context.getServiceTarget();

        final ServiceName endpointName = ManagementRemotingServices.MANAGEMENT_ENDPOINT;
        final String hostName = WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.NODE_NAME, null);

        NativeManagementServices.installManagementWorkerService(serviceTarget, context.getServiceRegistry(false));
        NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostName, context.getServiceRegistry(false));

        final String bindingName = SOCKET_BINDING.resolveModelAttribute(context, model).asString();
        ServiceName socketBindingServiceName = context.getCapabilityServiceName(SOCKET_BINDING_CAPABILITY_NAME, bindingName, SocketBinding.class);

        String saslAuthenticationFactory = commonPolicy.getSaslAuthenticationFactory();
        if (saslAuthenticationFactory == null) {
            ServerLogger.ROOT_LOGGER.nativeManagementInterfaceIsUnsecured();
        }

        ServiceName saslAuthenticationFactoryName = saslAuthenticationFactory != null ? context.getCapabilityServiceName(
                SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class) : null;
        String sslContext = commonPolicy.getSSLContext();
        ServiceName sslContextName = sslContext != null ? context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, sslContext, SSLContext.class) : null;

        final ServiceName sbmName = context.getCapabilityServiceName("org.wildfly.management.socket-binding-manager", SocketBindingManager.class);

        ManagementRemotingServices.installConnectorServicesForSocketBinding(serviceTarget, endpointName,
                    ManagementRemotingServices.MANAGEMENT_CONNECTOR,
                    socketBindingServiceName, commonPolicy.getConnectorOptions(),
                    saslAuthenticationFactoryName, sslContextName, sbmName);
        return Arrays.asList(REMOTING_BASE.append("server", MANAGEMENT_CONNECTOR), socketBindingServiceName);
    }

}
