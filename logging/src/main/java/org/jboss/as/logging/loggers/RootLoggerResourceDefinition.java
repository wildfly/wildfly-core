/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.loggers;

import static org.jboss.as.logging.CommonAttributes.ADD_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.REMOVE_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.loggers.LoggerAttributes.FILTER_SPEC;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLERS;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations.ReadFilterOperationStepHandler;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class RootLoggerResourceDefinition extends SimpleResourceDefinition {
    public static final String ROOT_LOGGER_ADD_OPERATION_NAME = "set-root-logger";
    public static final String NAME = "root-logger";
    public static final String RESOURCE_NAME = "ROOT";
    public static final PathElement ROOT_LOGGER_PATH = PathElement.pathElement(NAME, RESOURCE_NAME);

    private static final String ROOT_LOGGER_CHANGE_LEVEL_OPERATION_NAME = "change-root-log-level";
    private static final String ROOT_LOGGER_REMOVE_OPERATION_NAME = "remove-root-logger";
    private static final String ROOT_LOGGER_ADD_HANDLER_OPERATION_NAME = "root-logger-assign-handler";
    private static final String ROOT_LOGGER_REMOVE_HANDLER_OPERATION_NAME = "root-logger-unassign-handler";
    private static final ResourceDescriptionResolver ROOT_RESOLVER = LoggingExtension.getResourceDescriptionResolver(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = {
            FILTER_SPEC,
            LEVEL,
            HANDLERS
    };

    public static final SimpleOperationDefinition ROOT_LOGGER_REMOVE_OPERATION = new SimpleOperationDefinitionBuilder(ROOT_LOGGER_REMOVE_OPERATION_NAME, ROOT_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .build();
    public static final OperationDefinition ADD_ROOT_LOGGER_DEFINITION = new SimpleOperationDefinitionBuilder(ROOT_LOGGER_ADD_OPERATION_NAME, ROOT_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setParameters(ATTRIBUTES)
            .build();
    public static final OperationDefinition CHANGE_LEVEL_OPERATION = new SimpleOperationDefinitionBuilder(ROOT_LOGGER_CHANGE_LEVEL_OPERATION_NAME, ROOT_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setParameters(CommonAttributes.LEVEL)
            .build();

    public static final OperationDefinition LEGACY_ADD_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(ROOT_LOGGER_ADD_HANDLER_OPERATION_NAME, ROOT_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    public static final OperationDefinition LEGACY_REMOVE_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(ROOT_LOGGER_REMOVE_HANDLER_OPERATION_NAME, ROOT_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    public static final OperationDefinition ADD_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(ADD_HANDLER_OPERATION_NAME, ROOT_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    public static final OperationDefinition REMOVE_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(REMOVE_HANDLER_OPERATION_NAME, ROOT_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    private final boolean includeLegacy;

    public RootLoggerResourceDefinition(final boolean includeLegacy) {
        super(new Parameters(ROOT_LOGGER_PATH, ROOT_RESOLVER)
                .setAddHandler(LoggerOperations.LoggerAddOperationStepHandler.INSTANCE)
                .setRemoveHandler(LoggerOperations.REMOVE_LOGGER)
                .setCapabilities(Capabilities.LOGGER_CAPABILITY));
        this.includeLegacy = includeLegacy;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, LoggerOperations.LoggerWriteAttributeHandler.INSTANCE);
        }
        if (this.includeLegacy) {
            resourceRegistration.registerReadWriteAttribute(FILTER, ReadFilterOperationStepHandler.INSTANCE, LoggerOperations.LoggerWriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registration) {
        super.registerOperations(registration);

        registration.registerOperationHandler(ADD_ROOT_LOGGER_DEFINITION, LoggerOperations.LoggerAddOperationStepHandler.INSTANCE);
        registration.registerOperationHandler(ROOT_LOGGER_REMOVE_OPERATION, LoggerOperations.REMOVE_LOGGER);
        registration.registerOperationHandler(CHANGE_LEVEL_OPERATION, LoggerOperations.CHANGE_LEVEL);
        registration.registerOperationHandler(ADD_HANDLER_OPERATION, LoggerOperations.ADD_HANDLER);
        registration.registerOperationHandler(REMOVE_HANDLER_OPERATION, LoggerOperations.REMOVE_HANDLER);
        registration.registerOperationHandler(LEGACY_ADD_HANDLER_OPERATION, LoggerOperations.ADD_HANDLER);
        registration.registerOperationHandler(LEGACY_REMOVE_HANDLER_OPERATION, LoggerOperations.REMOVE_HANDLER);
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(ROOT_LOGGER_PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            //do nothing
        }
    }
}
