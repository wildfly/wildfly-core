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

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.HttpInterfaceCommonPolicy.Header;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * The base add handler for the HTTP Management Interface.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseHttpInterfaceAddStepHandler extends ManagementInterfaceAddStepHandler {

    protected static final String SSL_CONTEXT_CAPABILITY = "org.wildfly.security.ssl-context";

    protected BaseHttpInterfaceAddStepHandler(final AttributeDefinition[] attributeDefinitions) {
        super(attributeDefinitions);
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        if (operation.hasDefined(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED)) {
            boolean httpUpgradeEnabled = operation.remove(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED).asBoolean();
            ModelNode httpUpgrade = operation.get(ModelDescriptionConstants.HTTP_UPGRADE);
            if (httpUpgrade.hasDefined(ModelDescriptionConstants.ENABLED)) {
                boolean httpUpgradeDotEnabled = httpUpgrade.require(ModelDescriptionConstants.ENABLED).asBoolean();
                if (httpUpgradeEnabled != httpUpgradeDotEnabled) {
                    throw ROOT_LOGGER.deprecatedAndCurrentParameterMismatch(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED, ModelDescriptionConstants.ENABLED);
                }
            } else {
                httpUpgrade.set(ModelDescriptionConstants.ENABLED, httpUpgradeEnabled);
            }
        }

        super.populateModel(operation, model);
    }

    @Override
    public void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String httpAuthenticationFactory = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.HTTP_AUTHENTICATION_FACTORY, model);
        final String sslContext = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.SSL_CONTEXT, model);
        final String securityRealm = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.SECURITY_REALM, model);
        final boolean consoleEnabled = BaseHttpInterfaceResourceDefinition.CONSOLE_ENABLED.resolveModelAttribute(context, model).asBoolean();
        final boolean httpUpgradeEnabled;
        final String saslAuthenticationFactory;
        if (model.hasDefined(ModelDescriptionConstants.HTTP_UPGRADE)) {
            ModelNode httpUpgrade = model.require(ModelDescriptionConstants.HTTP_UPGRADE);
            httpUpgradeEnabled = BaseHttpInterfaceResourceDefinition.ENABLED.resolveModelAttribute(context, httpUpgrade).asBoolean();
            saslAuthenticationFactory =  asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.SASL_AUTHENTICATION_FACTORY, httpUpgrade);
        } else {
            httpUpgradeEnabled = false;
            saslAuthenticationFactory = null;
        }
        final List<String> allowedOrigins = BaseHttpInterfaceResourceDefinition.ALLOWED_ORIGINS.unwrap(context, model);

        String serverName = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.SERVER_NAME, model);
        Builder builder = OptionMap.builder();
        builder.set(RemotingOptions.SASL_PROTOCOL, BaseHttpInterfaceResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        if (serverName != null) {
            builder.set(RemotingOptions.SERVER_NAME, serverName);
        }
        final OptionMap options = builder.getMap();

        Map<String, List<Header>> constantHeaders = model.hasDefined(ModelDescriptionConstants.CONSTANT_HEADERS) ? new LinkedHashMap<>() : null;
        if (constantHeaders != null) {
            for (ModelNode headerMapping : model.require(ModelDescriptionConstants.CONSTANT_HEADERS).asList()) {
                String path = BaseHttpInterfaceResourceDefinition.PATH.resolveModelAttribute(context, headerMapping).asString();
                List<Header> headers = new ArrayList<>();
                for (ModelNode header : headerMapping.require(ModelDescriptionConstants.HEADERS).asList()) {
                    headers.add(new Header(
                            BaseHttpInterfaceResourceDefinition.HEADER_NAME.resolveModelAttribute(context, header).asString(),
                            BaseHttpInterfaceResourceDefinition.HEADER_VALUE.resolveModelAttribute(context, header).asString()));
                }

                if (constantHeaders.containsKey(path)) {
                    constantHeaders.get(path).addAll(headers);
                } else {
                    constantHeaders.put(path, headers);
                }
            }
        }

        List<ServiceName> requiredServices = installServices(context, new HttpInterfaceCommonPolicy() {

            @Override
            public String getHttpAuthenticationFactory() {
                return httpAuthenticationFactory;
            }

            @Override
            public String getSSLContext() {
                return sslContext;
            }

            @Override
            public String getSaslAuthenticationFactory() {
                return saslAuthenticationFactory;
            }

            @Override
            public boolean isHttpUpgradeEnabled() {
                return httpUpgradeEnabled;
            }

            @Override
            public boolean isConsoleEnabled() {
                return consoleEnabled;
            }

            @Override
            public String getSecurityRealm() {
                return securityRealm;
            }

            @Override
            public OptionMap getConnectorOptions() {
                return options;
            }

            @Override
            public List<String> getAllowedOrigins() {
                return allowedOrigins;
            }

            @Override
            public Map<String, List<Header>> getConstantHeaders() {
                return constantHeaders;
            }


        }, model);
        addVerifyInstallationStep(context, requiredServices);
    }

    protected abstract List<ServiceName> installServices(OperationContext context, HttpInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException;

}
