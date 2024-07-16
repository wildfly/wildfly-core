/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;
import static org.jboss.as.remoting.RemotingServices.REMOTING_BASE;
import static org.jboss.as.remoting.management.ManagementRemotingServices.MANAGEMENT_CONNECTOR;

import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.BaseNativeInterfaceAddStepHandler;
import org.jboss.as.controller.management.NativeInterfaceCommonPolicy;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
import org.jboss.as.host.controller.security.SaslWrappingService;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class NativeManagementAddHandler extends BaseNativeInterfaceAddStepHandler {

    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;

    private final LocalHostControllerInfoImpl hostControllerInfo;

    public NativeManagementAddHandler(final LocalHostControllerInfoImpl hostControllerInfo) {
        this.hostControllerInfo = hostControllerInfo;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return super.requiresRuntime(context)
                && (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected List<ServiceName> installServices(OperationContext context, NativeInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException {
        populateHostControllerInfo(hostControllerInfo, context, model);

        final ServiceTarget serviceTarget = context.getServiceTarget();

        final boolean onDemand = context.isBooting();
        NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostControllerInfo.getLocalHostName(), context.getServiceRegistry(false), onDemand);

        OptionMap options = createConnectorOptions(commonPolicy);

        final ServiceName nativeManagementInterfaceBinding = context.getCapabilityServiceName(NetworkInterfaceBinding.SERVICE_DESCRIPTOR, hostControllerInfo.getNativeManagementInterface());

        final String saslAuthenticationFactory = commonPolicy.getSaslAuthenticationFactory();
        if (saslAuthenticationFactory == null) {
            ROOT_LOGGER.nativeManagementInterfaceIsUnsecured();
        }

        ServiceName saslAuthenticationFactoryName = saslAuthenticationFactory != null ? context.getCapabilityServiceName(
                SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class) : null;
        saslAuthenticationFactoryName = SaslWrappingService.install(serviceTarget, saslAuthenticationFactoryName, NATIVE_INTERFACE);

        String sslContext = commonPolicy.getSSLContext();
        ServiceName sslContextName = sslContext != null ? context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, sslContext, SSLContext.class) : null;

        NativeManagementServices.installManagementWorkerService(serviceTarget, context.getServiceRegistry(false));

        ManagementRemotingServices.installDomainConnectorServices(context, serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                nativeManagementInterfaceBinding, hostControllerInfo.getNativeManagementPort(), options, saslAuthenticationFactoryName, sslContextName);
        return Arrays.asList(REMOTING_BASE.append("server", MANAGEMENT_CONNECTOR), nativeManagementInterfaceBinding);
    }

    private static void populateHostControllerInfo(LocalHostControllerInfoImpl hostControllerInfo, OperationContext context, ModelNode model) throws OperationFailedException {
        hostControllerInfo.setNativeManagementInterface(NativeManagementResourceDefinition.INTERFACE.resolveModelAttribute(context, model).asString());
        final ModelNode portNode = NativeManagementResourceDefinition.NATIVE_PORT.resolveModelAttribute(context, model);
        hostControllerInfo.setNativeManagementPort(portNode.isDefined() ? portNode.asInt() : -1);
    }

    private static OptionMap createConnectorOptions(final NativeInterfaceCommonPolicy commonPolicy) throws OperationFailedException {
        Builder builder = OptionMap.builder();

        builder.addAll(NativeManagementServices.CONNECTION_OPTIONS);
        builder.addAll(commonPolicy.getConnectorOptions());

        return builder.getMap();
    }

}
