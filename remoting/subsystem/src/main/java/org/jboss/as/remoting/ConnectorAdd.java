/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.remoting.logging.RemotingLogger.ROOT_LOGGER;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;

/**
 * Add a connector to a remoting container.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class ConnectorAdd extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));

        launchServices(context, fullModel);
    }

    static void launchServices(OperationContext context, ModelNode fullModel) throws OperationFailedException {
        final String connectorName = context.getCurrentAddressValue();
        OptionMap optionMap = ConnectorUtils.getFullOptions(context, fullModel);

        final ServiceTarget target = context.getServiceTarget();

        final String socketName = ConnectorResource.SOCKET_BINDING.resolveModelAttribute(context, fullModel).asString();
        final ServiceName socketBindingName = context.getCapabilityServiceName(SocketBinding.SERVICE_DESCRIPTOR, socketName);

        ModelNode securityRealmModel = ConnectorResource.SECURITY_REALM.resolveModelAttribute(context, fullModel);
        if (securityRealmModel.isDefined()) {
            throw ROOT_LOGGER.runtimeSecurityRealmUnsupported();
        }

        ModelNode saslAuthenticationFactoryModel = ConnectorResource.SASL_AUTHENTICATION_FACTORY.resolveModelAttribute(context, fullModel);
        final ServiceName saslAuthenticationFactoryName = saslAuthenticationFactoryModel.isDefined()
                ? context.getCapabilityServiceName(SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactoryModel.asString(), SaslAuthenticationFactory.class)
                : null;

        ModelNode sslContextModel = ConnectorResource.SSL_CONTEXT.resolveModelAttribute(context, fullModel);
        final ServiceName sslContextName = sslContextModel.isDefined()
                ? context.getCapabilityServiceName(SSL_CONTEXT_CAPABILITY, sslContextModel.asString(), SSLContext.class) : null;

        final ServiceName sbmName = context.getCapabilityServiceName(SocketBindingManager.SERVICE_DESCRIPTOR);

        RemotingServices.installConnectorServicesForSocketBinding(target, context.getCapabilityServiceName(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getName(), Endpoint.class), connectorName,
                socketBindingName, optionMap, saslAuthenticationFactoryName, sslContextName, sbmName);
    }
}
