/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments.resources;

import java.util.Collection;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.as.logging.deployments.LoggingConfigurationService;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.ObjectConfigurable;
import org.jboss.logmanager.config.PropertyConfigurable;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingDeploymentResources {

    public static final SimpleResourceDefinition CONFIGURATION = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("configuration"), LoggingExtension.getResourceDescriptionResolver("deployment")).setRuntime());

    public static final SimpleResourceDefinition HANDLER = new HandlerResourceDefinition();

    public static final SimpleResourceDefinition LOGGER = new LoggerResourceDefinition();

    public static final SimpleResourceDefinition FORMATTER = new PropertiesResourceDefinition("formatter") {
        @Override
        protected PropertyConfigurable getPropertyConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getFormatterConfiguration(name);
        }

        @Override
        protected ObjectConfigurable getObjectConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getFormatterConfiguration(name);
        }
    };

    public static final SimpleResourceDefinition FILTER = new PropertiesResourceDefinition("filter") {
        @Override
        protected PropertyConfigurable getPropertyConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getFilterConfiguration(name);
        }

        @Override
        protected ObjectConfigurable getObjectConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getFilterConfiguration(name);
        }
    };

    public static final SimpleResourceDefinition POJO = new PropertiesResourceDefinition("pojo") {
        @Override
        protected PropertyConfigurable getPropertyConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getPojoConfiguration(name);
        }

        @Override
        protected ObjectConfigurable getObjectConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getPojoConfiguration(name);
        }
    };

    public static final SimpleResourceDefinition ERROR_MANAGER = new PropertiesResourceDefinition("error-manager") {
        @Override
        protected PropertyConfigurable getPropertyConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getErrorManagerConfiguration(name);
        }

        @Override
        protected ObjectConfigurable getObjectConfigurable(final LogContextConfiguration logContextConfiguration, final String name) {
            return logContextConfiguration.getErrorManagerConfiguration(name);
        }
    };

    /**
     * Registers the deployment resources needed.
     *
     * @param deploymentResourceSupport the deployment resource support
     * @param service                   the service, which may be {@code null}, used to find the resource names that need to be registered
     */
    public static void registerDeploymentResource(final DeploymentResourceSupport deploymentResourceSupport, final LoggingConfigurationService service) {
        final PathElement base = PathElement.pathElement("configuration", service.getConfiguration());
        deploymentResourceSupport.getDeploymentSubModel(LoggingExtension.SUBSYSTEM_NAME, base);
        final LogContextConfiguration configuration = service.getValue();
        // Register the child resources if the configuration is not null in cases where a log4j configuration was used
        if (configuration != null) {
            registerDeploymentResource(deploymentResourceSupport, base, HANDLER, configuration.getHandlerNames());
            registerDeploymentResource(deploymentResourceSupport, base, LOGGER, configuration.getLoggerNames());
            registerDeploymentResource(deploymentResourceSupport, base, FORMATTER, configuration.getFormatterNames());
            registerDeploymentResource(deploymentResourceSupport, base, FILTER, configuration.getFilterNames());
            registerDeploymentResource(deploymentResourceSupport, base, POJO, configuration.getPojoNames());
            registerDeploymentResource(deploymentResourceSupport, base, ERROR_MANAGER, configuration.getErrorManagerNames());
        }
    }

    private static void registerDeploymentResource(final DeploymentResourceSupport deploymentResourceSupport, final PathElement base, final ResourceDefinition def, final Collection<String> names) {
        for (String name : names) {
            // Replace any blank values with the default root-logger name; this should only happen on loggers
            final String resourceName = name.isEmpty() ? RootLoggerResourceDefinition.RESOURCE_NAME : name;
            final PathAddress address = PathAddress.pathAddress(base, PathElement.pathElement(def.getPathElement().getKey(), resourceName));
            deploymentResourceSupport.getDeploymentSubModel(LoggingExtension.SUBSYSTEM_NAME, address);
        }
    }

    private abstract static class PropertiesResourceDefinition extends SimpleResourceDefinition {

        static final SimpleAttributeDefinition CLASS_NAME = SimpleAttributeDefinitionBuilder.create("class-name", ModelType.STRING)
                .setStorageRuntime()
                .build();

        static final SimpleAttributeDefinition MODULE = SimpleAttributeDefinitionBuilder.create("module", ModelType.STRING, true)
                .setStorageRuntime()
                .build();

        static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
                .setStorageRuntime()
                .build();

        PropertiesResourceDefinition(final String name) {
            super(new Parameters(PathElement.pathElement(name), LoggingExtension.getResourceDescriptionResolver("deployment", name)).setRuntime());
        }

        @Override
        public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadOnlyAttribute(CLASS_NAME, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final LogContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
                    final ObjectConfigurable configuration = getObjectConfigurable(logContextConfiguration, name);
                    setModelValue(model, configuration.getClassName());
                }
            });
            resourceRegistration.registerReadOnlyAttribute(MODULE, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final LogContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
                    final ObjectConfigurable configuration = getObjectConfigurable(logContextConfiguration, name);
                    setModelValue(model, configuration.getModuleName());
                }
            });
            resourceRegistration.registerReadOnlyAttribute(PROPERTIES, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final LogContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
                    final PropertyConfigurable configuration = getPropertyConfigurable(logContextConfiguration, name);
                    addProperties(configuration, model);
                }
            });
        }

        protected abstract PropertyConfigurable getPropertyConfigurable(LogContextConfiguration logContextConfiguration, String name);

        protected abstract ObjectConfigurable getObjectConfigurable(LogContextConfiguration logContextConfiguration, String name);
    }
}
