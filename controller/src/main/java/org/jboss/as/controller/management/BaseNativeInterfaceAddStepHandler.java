/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.management;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * The base add handler for the native management interface.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseNativeInterfaceAddStepHandler extends ManagementInterfaceAddStepHandler {

    protected BaseNativeInterfaceAddStepHandler(final AttributeDefinition[] attributeDefinitions) {
        super(attributeDefinitions);
    }

    @Override
    public void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String saslAuthenticationFactory = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SASL_AUTHENTICATION_FACTORY, model);
        final String sslContext = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SSL_CONTEXT, model);
        final String securityRealm = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SECURITY_REALM, model);

        String serverName = asStringIfDefined(context, BaseNativeInterfaceResourceDefinition.SERVER_NAME, model);
        Builder builder = OptionMap.builder();
        builder.set(RemotingOptions.SASL_PROTOCOL, BaseNativeInterfaceResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        if (serverName != null) {
            builder.set(RemotingOptions.SERVER_NAME, serverName);
        }
        final OptionMap options = builder.getMap();

        List<ServiceName> requiredServices = installServices(context, new NativeInterfaceCommonPolicy() {

            @Override
            public String getSaslAuthenticationFactory() {
                return saslAuthenticationFactory;
            }

            @Override
            public String getSSLContext() {
                return sslContext;
            }

            @Override
            public String getSecurityRealm() {
                return securityRealm;
            }

            @Override
            public OptionMap getConnectorOptions() {
                return options;
            }
        }, model);
        addVerifyInstallationStep(context, requiredServices);
    }

    protected abstract List<ServiceName> installServices(OperationContext context, NativeInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException;
}
