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

package org.jboss.as.logging.deployments.resources;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * Describes a handler used on a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class HandlerResourceDefinition extends SimpleResourceDefinition {

    private static final String NAME = "handler";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final SimpleAttributeDefinition CLASS_NAME = SimpleAttributeDefinitionBuilder.create("class-name", ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition MODULE = SimpleAttributeDefinitionBuilder.create("module", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition ENCODING = SimpleAttributeDefinitionBuilder.create("encoding", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleListAttributeDefinition HANDLERS = SimpleListAttributeDefinition.Builder.of("handlers", HANDLER)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FORMATTER = SimpleAttributeDefinitionBuilder.create("formatter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FILTER = SimpleAttributeDefinitionBuilder.create("filter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition ERROR_MANAGER = SimpleAttributeDefinitionBuilder.create("error-manager", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    HandlerResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver("deployment", NAME)).setRuntime());
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(CLASS_NAME, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getClassName());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(MODULE, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getModuleName());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(ENCODING, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getEncoding());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(LEVEL, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getLevel());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FORMATTER, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getFormatterName());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FILTER, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getFilter());
            }
        });
        resourceRegistration.registerReadOnlyAttribute(HANDLERS, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                final ModelNode handlers = model.setEmptyList();
                for (String s : configuration.getHandlerNames()) {
                    handlers.add(s);
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(PROPERTIES, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                addProperties(configuration, model);
            }
        });
        resourceRegistration.registerReadOnlyAttribute(ERROR_MANAGER, new HandlerConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final HandlerConfiguration configuration, final ModelNode model) {
                setModelValue(model, configuration.getErrorManagerName());
            }
        });
    }

    abstract static class HandlerConfigurationReadStepHandler extends LoggingConfigurationReadStepHandler {
        @Override
        protected void updateModel(final LogContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
            final HandlerConfiguration handlerConfiguration = logContextConfiguration.getHandlerConfiguration(name);
            updateModel(handlerConfiguration, model);

        }

        protected abstract void updateModel(HandlerConfiguration configuration, ModelNode model);
    }

}
