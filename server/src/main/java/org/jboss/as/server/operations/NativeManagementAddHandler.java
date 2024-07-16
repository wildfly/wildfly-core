/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.remoting.RemotingServices.REMOTING_BASE;
import static org.jboss.as.remoting.management.ManagementRemotingServices.MANAGEMENT_CONNECTOR;
import static org.jboss.as.server.mgmt.NativeManagementResourceDefinition.SOCKET_BINDING;

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
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
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

    private NativeManagementAddHandler() {
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
        ServiceName socketBindingServiceName = context.getCapabilityServiceName(SocketBinding.SERVICE_DESCRIPTOR, bindingName);

        String saslAuthenticationFactory = commonPolicy.getSaslAuthenticationFactory();
        if (saslAuthenticationFactory == null) {
            ServerLogger.ROOT_LOGGER.nativeManagementInterfaceIsUnsecured();
        }

        ServiceName saslAuthenticationFactoryName = saslAuthenticationFactory != null ? context.getCapabilityServiceName(
                SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class) : null;
        String sslContext = commonPolicy.getSSLContext();
        ServiceName sslContextName = sslContext != null ? context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, sslContext, SSLContext.class) : null;

        final ServiceName sbmName = context.getCapabilityServiceName(SocketBindingManager.SERVICE_DESCRIPTOR);

        ManagementRemotingServices.installConnectorServicesForSocketBinding(serviceTarget, endpointName,
                    ManagementRemotingServices.MANAGEMENT_CONNECTOR,
                    socketBindingServiceName, commonPolicy.getConnectorOptions(),
                    saslAuthenticationFactoryName, sslContextName, sbmName);
        return Arrays.asList(REMOTING_BASE.append("server", MANAGEMENT_CONNECTOR), socketBindingServiceName);
    }

}
