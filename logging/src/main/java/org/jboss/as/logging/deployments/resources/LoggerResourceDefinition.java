/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments.resources;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;

/**
 * Describes a logger used on a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggerResourceDefinition extends SimpleResourceDefinition {

    private static final String NAME = "logger";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleListAttributeDefinition HANDLERS = SimpleListAttributeDefinition.Builder.of("handlers", HANDLER)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FILTER = SimpleAttributeDefinitionBuilder.create("filter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition USE_PARENT_HANDLERS = SimpleAttributeDefinitionBuilder.create("use-parent-handlers", ModelType.BOOLEAN, true)
            .setStorageRuntime()
            .build();

    public LoggerResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver("deployment", NAME)).setRuntime());
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(LEVEL, new LoggerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final LoggerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getLevel());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FILTER, new LoggerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final LoggerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getFilter());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(HANDLERS, new LoggerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final LoggerConfiguration configuration, final ModelNode model) {
                final ModelNode handlers = model.setEmptyList();
                for (String s : configuration.getHandlerNames()) {
                    handlers.add(s);
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(USE_PARENT_HANDLERS, new LoggerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final LoggerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getUseParentHandlers());
            }
        });
    }

    abstract static class LoggerConfigurationReadStepHandler extends LoggingConfigurationReadStepHandler {
        @Override
        protected void updateModel(final LogContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
            final LoggerConfiguration configuration = logContextConfiguration.getLoggerConfiguration(RootLoggerResourceDefinition.RESOURCE_NAME.equals(name) ? "" : name);
            updateModel(configuration, model);

        }

        protected abstract void updateModel(LoggerConfiguration configuration, ModelNode model);
    }

}
