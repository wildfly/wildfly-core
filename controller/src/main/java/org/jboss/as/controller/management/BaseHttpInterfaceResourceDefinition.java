/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;
import static org.jboss.as.controller.management.Capabilities.HTTP_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.HTTP_MANAGEMENT_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Base class for the {@link ResourceDefinition} instances to extend from.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseHttpInterfaceResourceDefinition extends SimpleResourceDefinition {

    public static final RuntimeCapability<Void> HTTP_MANAGEMENT_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of(HTTP_MANAGEMENT_CAPABILITY)
        .build();

    protected static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, HTTP_INTERFACE);

    public static final SimpleAttributeDefinition HTTP_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HTTP_AUTHENTICATION_FACTORY, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(HTTP_AUTHENTICATION_FACTORY_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_FACTORY_REF)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(SSL_CONTEXT_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SSL_REF)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition CONSOLE_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONSOLE_ENABLED, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setXmlName(Attribute.CONSOLE_ENABLED.getLocalName())
        .setDefaultValue(ModelNode.TRUE)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition HTTP_UPGRADE_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED, ModelType.BOOLEAN, true)
        .setXmlName(Attribute.HTTP_UPGRADE_ENABLED.getLocalName())
        .setDeprecated(ModelVersion.create(5), true)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ENABLED, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_AUTHENTICATION_FACTORY, ModelType.STRING, true)
        .setMinSize(1)
        .setCapabilityReference(SASL_AUTHENTICATION_FACTORY_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .setRestartAllServices()
        .build();

    public static final ObjectTypeAttributeDefinition HTTP_UPGRADE = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.HTTP_UPGRADE, ENABLED, SASL_AUTHENTICATION_FACTORY)
        .setRestartAllServices()
        .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_FACTORY_REF)
        .build();

    public static final SimpleAttributeDefinition SERVER_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SERVER_NAME, ModelType.STRING, true)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setDeprecated(ModelVersion.create(5))
        .setRestartAllServices()
        .build();

    public static final SimpleAttributeDefinition SASL_PROTOCOL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_PROTOCOL, ModelType.STRING, true)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setDefaultValue(new ModelNode(ModelDescriptionConstants.REMOTE))
        .setDeprecated(ModelVersion.create(5))
        .setRestartAllServices()
        .build();

    public static final StringListAttributeDefinition ALLOWED_ORIGINS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.ALLOWED_ORIGINS)
        .setAllowExpression(true)
        .setRequired(false)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
        .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
        .setRestartAllServices()
        .build();

    private static final Set<String> disallowedValues = new HashSet<>(Arrays.asList(new String[] {ModelDescriptionConstants.CONNECTION, ModelDescriptionConstants.CONTENT_TYPE,
            ModelDescriptionConstants.CONTENT_LENGTH, ModelDescriptionConstants.DATE, ModelDescriptionConstants.TRANSFER_ENCODING}));

    public static final SimpleAttributeDefinition HEADER_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false)
            .setMinSize(1)
            .setValidator(new ParameterValidator() {

                private static final String NAME_PATTERN = "^([\\p{ASCII}&&[^\\(\\)\\<\\>\\@\\,\\;\\:\\\\/\\[\\]\\?\\=\\{\\}\\p{Cntrl}\\x{20}]])+\\z";
                private final Predicate<String> VALID_NAME = Pattern.compile(NAME_PATTERN).asPredicate();

                @Override
                public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
                    String name = value.asString();
                    if (disallowedValues.contains(name.toLowerCase(Locale.ENGLISH))) {
                        throw ROOT_LOGGER.disallowedHeaderName(name);
                    }
                    if (!VALID_NAME.test(name)) {
                        throw ROOT_LOGGER.invalidHeaderName(name);
                    }
                }
            })
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition HEADER_VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.STRING, false)
            .setMinSize(1)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition HEADER_PAIR = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.HEADER_PAIR, HEADER_NAME, HEADER_VALUE)
            .setRestartAllServices()
            .build();

    static final ObjectListAttributeDefinition HEADERS = new ObjectListAttributeDefinition.Builder(ModelDescriptionConstants.HEADERS, HEADER_PAIR)
            .setRequired(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PATH, ModelType.STRING, false)
            .setMinSize(1)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final ObjectTypeAttributeDefinition HEADER_MAPPING = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.HEADER_MAPPING, PATH, HEADERS)
            .setRestartAllServices()
            .build();

    public static final ObjectListAttributeDefinition CONSTANT_HEADERS = new ObjectListAttributeDefinition.Builder(ModelDescriptionConstants.CONSTANT_HEADERS, HEADER_MAPPING)
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition BACKLOG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.BACKLOG, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(50))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setStability(Stability.COMMUNITY)
            .build();

    public static final SimpleAttributeDefinition NO_REQUEST_TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NO_REQUEST_TIMEOUT, ModelType.INT, true)
            .setAllowExpression(true)
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setDefaultValue(new ModelNode(60000))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setStability(Stability.COMMUNITY)
            .build();

    public static final SimpleAttributeDefinition CONNECTION_HIGH_WATER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONNECTION_HIGH_WATER, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(100))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setStability(Stability.COMMUNITY)
            .build();

    public static final SimpleAttributeDefinition CONNECTION_LOW_WATER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONNECTION_LOW_WATER, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(75))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setStability(Stability.COMMUNITY)
            .build();

    protected static final AttributeDefinition[] COMMON_ATTRIBUTES = new AttributeDefinition[] { HTTP_AUTHENTICATION_FACTORY, SSL_CONTEXT, CONSOLE_ENABLED, HTTP_UPGRADE_ENABLED,
                                                                                                     HTTP_UPGRADE, SASL_PROTOCOL, SERVER_NAME, ALLOWED_ORIGINS, CONSTANT_HEADERS,
                                                                                                     BACKLOG, NO_REQUEST_TIMEOUT, CONNECTION_HIGH_WATER, CONNECTION_LOW_WATER };

    /**
     * @param parameters
     */
    protected BaseHttpInterfaceResourceDefinition(Parameters parameters) {
        super(parameters
                .addAccessConstraints(SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES)
                .addCapabilities(HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
                .setDeprecatedSince(ModelVersion.create(1, 7))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AttributeDefinition[] attributeDefinitions = getAttributeDefinitions();
        OperationStepHandler defaultWriteHandler = new ManagementWriteAttributeHandler(getValidationConsumer());
        for (AttributeDefinition attr : attributeDefinitions) {
            if (attr.equals(HTTP_UPGRADE_ENABLED)) {
                HttpUpgradeAttributeHandler handler = new HttpUpgradeAttributeHandler();
                resourceRegistration.registerReadWriteAttribute(attr, handler, handler);
            } else {
                resourceRegistration.registerReadWriteAttribute(attr, null, defaultWriteHandler);
            }
        }
    }

    protected abstract Consumer<OperationContext> getValidationConsumer();
    protected abstract AttributeDefinition[] getAttributeDefinitions();

    protected class HttpUpgradeAttributeHandler implements OperationStepHandler {

        public HttpUpgradeAttributeHandler() {
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String operationName = operation.require(ModelDescriptionConstants.OP).asString();
            assert ModelDescriptionConstants.HTTP_UPGRADE_ENABLED.equals(operation.require(ModelDescriptionConstants.NAME).asString());
            switch (operationName) {
                case ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION: {
                    final ModelNode httpUpgrade = context.readResource(PathAddress.EMPTY_ADDRESS, false)
                            .getModel()
                            .get(ModelDescriptionConstants.HTTP_UPGRADE);

                    context.getResult().set(ENABLED.resolveModelAttribute(context, httpUpgrade));
                    break;
                }
                case ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION:
                    final ModelNode httpUpgrade = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS)
                            .getModel()
                            .get(ModelDescriptionConstants.HTTP_UPGRADE);

                    httpUpgrade.get(ModelDescriptionConstants.ENABLED).set(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
                    context.reloadRequired();
                    context.completeStep(OperationContext.RollbackHandler.REVERT_RELOAD_REQUIRED_ROLLBACK_HANDLER);
                    break;
            }

        }

    }

    protected static AttributeDefinition[] combine(AttributeDefinition[] commonAttributes, AttributeDefinition... additionalAttributes) {
        AttributeDefinition[] combined = new AttributeDefinition[commonAttributes.length + additionalAttributes.length];
        System.arraycopy(commonAttributes, 0, combined, 0, commonAttributes.length);
        System.arraycopy(additionalAttributes, 0, combined, commonAttributes.length, additionalAttributes.length);

        return combined;
    }

}
