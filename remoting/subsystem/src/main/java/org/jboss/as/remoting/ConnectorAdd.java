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

package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.Capabilities.SOCKET_BINDING_MANAGER_CAPABILTIY;
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

    static final ConnectorAdd INSTANCE = new ConnectorAdd();

    private ConnectorAdd() {
        super(ConnectorResource.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));

        launchServices(context, fullModel);
    }

    void launchServices(OperationContext context, ModelNode fullModel) throws OperationFailedException {
        final String connectorName = context.getCurrentAddressValue();
        OptionMap optionMap = ConnectorUtils.getFullOptions(context, fullModel);

        final ServiceTarget target = context.getServiceTarget();

        final String socketName = ConnectorResource.SOCKET_BINDING.resolveModelAttribute(context, fullModel).asString();
        final ServiceName socketBindingName = context.getCapabilityServiceName(ConnectorResource.SOCKET_CAPABILITY_NAME, socketName, SocketBinding.class);

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

        final ServiceName sbmName = context.getCapabilityServiceName(SOCKET_BINDING_MANAGER_CAPABILTIY, SocketBindingManager.class);

        RemotingServices.installConnectorServicesForSocketBinding(target, context.getCapabilityServiceName(RemotingSubsystemRootResource.REMOTING_ENDPOINT_CAPABILITY.getName(), Endpoint.class), connectorName,
                socketBindingName, optionMap, saslAuthenticationFactoryName, sslContextName, sbmName);
    }
}
