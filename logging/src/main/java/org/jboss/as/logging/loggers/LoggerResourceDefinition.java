/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.loggers;

import static org.jboss.as.logging.CommonAttributes.ADD_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.REMOVE_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.Logging.join;
import static org.jboss.as.logging.loggers.LoggerAttributes.FILTER_SPEC;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLERS;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations.ReadFilterOperationStepHandler;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggerResourceDefinition extends SimpleResourceDefinition {
    public static final String NAME = "logger";

    private static final String CHANGE_LEVEL_OPERATION_NAME = "change-log-level";
    private static final String LEGACY_ADD_HANDLER_OPERATION_NAME = "assign-handler";
    private static final String LEGACY_REMOVE_HANDLER_OPERATION_NAME = "unassign-handler";
    private static final PathElement LOGGER_PATH = PathElement.pathElement(NAME);

    private static final ResourceDescriptionResolver LOGGER_RESOLVER = LoggingExtension.getResourceDescriptionResolver(NAME);

    public static final OperationDefinition CHANGE_LEVEL_OPERATION = new SimpleOperationDefinitionBuilder(CHANGE_LEVEL_OPERATION_NAME, LOGGER_RESOLVER)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .setParameters(CommonAttributes.LEVEL)
            .build();

    public static final OperationDefinition LEGACY_ADD_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(LEGACY_ADD_HANDLER_OPERATION_NAME, LOGGER_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .build();

    public static final OperationDefinition LEGACY_REMOVE_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(LEGACY_REMOVE_HANDLER_OPERATION_NAME, LOGGER_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .setDeprecated(ModelVersion.create(1, 2, 0))
            .build();

    public static final OperationDefinition ADD_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(ADD_HANDLER_OPERATION_NAME, LOGGER_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    public static final OperationDefinition REMOVE_HANDLER_OPERATION = new SimpleOperationDefinitionBuilder(REMOVE_HANDLER_OPERATION_NAME, LOGGER_RESOLVER)
            .setParameters(CommonAttributes.HANDLER_NAME)
            .build();

    public static final PropertyAttributeDefinition USE_PARENT_HANDLERS = PropertyAttributeDefinition.Builder.of("use-parent-handlers", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setPropertyName("useParentHandlers")
            .build();

    // Be careful with this attribute. It needs to show up in the "add" operation param list so ops from legacy
    // scripts will validate. It does because it's registered as an attribute but is not setResourceOnly(true)
    // so DefaultResourceAddDescriptionProvider adds it to the param list
    public static final SimpleAttributeDefinition CATEGORY = SimpleAttributeDefinitionBuilder.create("category", ModelType.STRING, true).build();

    private static final AttributeDefinition[] WRITABLE_ATTRIBUTES = {
            FILTER_SPEC,
            LEVEL,
            HANDLERS,
            USE_PARENT_HANDLERS
    };

    private static final AttributeDefinition[] LEGACY_ATTRIBUTES = {
            FILTER,
    };

    private final AttributeDefinition[] writableAttributes;
    private final OperationStepHandler writeHandler;

    public LoggerResourceDefinition(final boolean includeLegacy) {
        super(new Parameters(LOGGER_PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(includeLegacy ? new LoggerOperations.LoggerAddOperationStepHandler(join(WRITABLE_ATTRIBUTES, LEGACY_ATTRIBUTES))
                        : new LoggerOperations.LoggerAddOperationStepHandler(WRITABLE_ATTRIBUTES))
                .setRemoveHandler(LoggerOperations.REMOVE_LOGGER)
                .setAccessConstraints(new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig(LoggingExtension.SUBSYSTEM_NAME, NAME)))
                .setCapabilities(Capabilities.LOGGER_CAPABILITY));
        writableAttributes = (includeLegacy ? join(WRITABLE_ATTRIBUTES, LEGACY_ATTRIBUTES) : WRITABLE_ATTRIBUTES);
        this.writeHandler = new LoggerOperations.LoggerWriteAttributeHandler(writableAttributes);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : writableAttributes) {
            // Filter requires a special reader
            if (def.getName().equals(FILTER.getName())) {
                resourceRegistration.registerReadWriteAttribute(def, ReadFilterOperationStepHandler.INSTANCE, writeHandler);
            } else {
                resourceRegistration.registerReadWriteAttribute(def, null, writeHandler);
            }
        }
        resourceRegistration.registerReadOnlyAttribute(CATEGORY, ReadResourceNameOperationStepHandler.INSTANCE);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registration) {
        super.registerOperations(registration);

        registration.registerOperationHandler(CHANGE_LEVEL_OPERATION, LoggerOperations.CHANGE_LEVEL);
        registration.registerOperationHandler(ADD_HANDLER_OPERATION, LoggerOperations.ADD_HANDLER);
        registration.registerOperationHandler(REMOVE_HANDLER_OPERATION, LoggerOperations.REMOVE_HANDLER);
        registration.registerOperationHandler(LEGACY_ADD_HANDLER_OPERATION, LoggerOperations.ADD_HANDLER);
        registration.registerOperationHandler(LEGACY_REMOVE_HANDLER_OPERATION, LoggerOperations.REMOVE_HANDLER);
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(LOGGER_PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion,
                                         final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                         final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }
}
