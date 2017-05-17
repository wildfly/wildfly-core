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

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;
import static org.jboss.as.host.controller.resources.NativeManagementResourceDefinition.ATTRIBUTE_DEFINITIONS;
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
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
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
        super(ATTRIBUTE_DEFINITIONS);
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

        final ServiceName nativeManagementInterfaceBinding = context.getCapabilityServiceName("org.wildfly.network.interface",
                hostControllerInfo.getNativeManagementInterface(), NetworkInterfaceBinding.class);

        final String securityRealm = commonPolicy.getSecurityRealm();
        final String saslAuthenticationFactory = commonPolicy.getSaslAuthenticationFactory();
        if (saslAuthenticationFactory == null && securityRealm == null) {
            ROOT_LOGGER.nativeManagementInterfaceIsUnsecured();
        }

        ServiceName securityRealmName = securityRealm != null ? SecurityRealm.ServiceUtil.createServiceName(securityRealm) : null;
        ServiceName saslAuthenticationFactoryName = saslAuthenticationFactory != null ? context.getCapabilityServiceName(
                SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class) : null;
        String sslContext = commonPolicy.getSSLContext();
        ServiceName sslContextName = sslContext != null ? context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, sslContext, SSLContext.class) : null;

        NativeManagementServices.installManagementWorkerService(serviceTarget, context.getServiceRegistry(false));
        ManagementRemotingServices.installDomainConnectorServices(context, serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                nativeManagementInterfaceBinding, hostControllerInfo.getNativeManagementPort(), options, securityRealmName, saslAuthenticationFactoryName, sslContextName);
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
