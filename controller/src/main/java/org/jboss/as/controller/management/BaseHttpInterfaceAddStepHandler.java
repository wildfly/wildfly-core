/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import static org.jboss.as.controller.logging.ControllerLogger.DEPRECATED_LOGGER;
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
import org.jboss.as.controller.registry.Resource;
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

    private static final String PROPERTY_BASE = "org.wildfly.management.";
    private static final String BACKLOG_PROPERTY = PROPERTY_BASE + "backlog";
    private static final String CONNECTION_HIGH_WATER_PROPERTY = PROPERTY_BASE + "connection-high-water";
    private static final String CONNECTION_LOW_WATER_PROPERTY = PROPERTY_BASE + "connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_PROPERTY = PROPERTY_BASE + "no-request-timeout";

    // 1GB default upload limit
    public static final long DEFAULT_UPLOAD_LIMIT = 1073741824L;
    public static final String DEFAULT_UPLOAD_LIMIT_PROPERTY = PROPERTY_BASE + "upload.limit";

    protected static final String HTTP_AUTHENTICATION_FACTORY_CAPABILITY = "org.wildfly.security.http-authentication-factory";
    protected static final String SASL_AUTHENTICATION_FACTORY_CAPABILITY = "org.wildfly.security.sasl-authentication-factory";
    protected static final String SSL_CONTEXT_CAPABILITY = "org.wildfly.security.ssl-context";

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
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

        super.populateModel(context, operation, resource);
    }

    @Override
    public void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final String httpAuthenticationFactory = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.HTTP_AUTHENTICATION_FACTORY, model);
        final String sslContext = asStringIfDefined(context, BaseHttpInterfaceResourceDefinition.SSL_CONTEXT, model);
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

        final int backlog = resolveIntProperty(BACKLOG_PROPERTY, BaseHttpInterfaceResourceDefinition.BACKLOG, context, model);
        final int noRequestTimeout = resolveIntProperty(NO_REQUEST_TIMEOUT_PROPERTY, BaseHttpInterfaceResourceDefinition.NO_REQUEST_TIMEOUT, context, model);
        final int connectionHighWater = resolveIntProperty(CONNECTION_HIGH_WATER_PROPERTY, BaseHttpInterfaceResourceDefinition.CONNECTION_HIGH_WATER, context, model);
        final int connectionLowWater = resolveIntProperty(CONNECTION_LOW_WATER_PROPERTY, BaseHttpInterfaceResourceDefinition.CONNECTION_LOW_WATER, context, model);
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

            @Override
            public int getBacklog() {
                return backlog;
            }

            @Override
            public int getNoRequestTimeoutMs() {
                return noRequestTimeout;
            }

            @Override
            public int getConnectionHighWater() {
                return connectionHighWater;
            }

            @Override
            public int getConnectionLowWater() {
                return connectionLowWater;
            }




        }, model);
        addVerifyInstallationStep(context, requiredServices);
    }

    protected abstract List<ServiceName> installServices(OperationContext context, HttpInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException;

    private static int resolveIntProperty(final String systemProperty, final AttributeDefinition attributeDefinition,
                                    final OperationContext context, final ModelNode model) throws OperationFailedException {
        if (System.getProperty(systemProperty) != null) {
            if (context.enables(attributeDefinition)) {
                // Log a warning if the system property is used and the attribute is enabled at the current stability level.
                DEPRECATED_LOGGER.systemPropertyDeprecated(systemProperty, attributeDefinition.getName(), context.getCurrentAddress().toCLIStyleString());
            }
            return Integer.parseInt(System.getProperty(systemProperty));
        }

        // This is fine if not enabled as we still get the default returned.
        return attributeDefinition.resolveModelAttribute(context, model).asInt();
    }
}
