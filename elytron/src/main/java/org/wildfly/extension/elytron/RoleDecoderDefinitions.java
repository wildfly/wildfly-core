/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.ROLE_DECODER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.authz.SourceAddressRoleDecoder;

/**
 * Container class for the {@link RoleDecoder} definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RoleDecoderDefinitions {

    static final SimpleAttributeDefinition ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ATTRIBUTE, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition SOURCE_ADDRESS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SOURCE_ADDRESS, ModelType.STRING)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.PATTERN)
            .build();

    static final SimpleAttributeDefinition PATTERN = new SimpleAttributeDefinitionBuilder(RegexAttributeDefinitions.PATTERN)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.SOURCE_ADDRESS)
            .build();

    static final StringListAttributeDefinition ROLES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ROLES)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    private static final AggregateComponentDefinition<RoleDecoder> AGGREGATE_ROLE_DECODER = AggregateComponentDefinition.create(RoleDecoder.class,
            ElytronDescriptionConstants.AGGREGATE_ROLE_DECODER, ElytronDescriptionConstants.ROLE_DECODERS, ROLE_DECODER_RUNTIME_CAPABILITY,
            (RoleDecoder[] r) -> RoleDecoder.aggregate(r));

    static ResourceDefinition getSimpleRoleDecoderDefinition() {
        return new SimpleRoleDecoderDefinition();
    }

    static ResourceDefinition getSourceAddressRoleDecoderDefinition() {
        return new SourceAddressRoleDecoderDefinition();
    }

    static AggregateComponentDefinition<RoleDecoder> getAggregateRoleDecoderDefinition() {
        return AGGREGATE_ROLE_DECODER;
    }

    private static class SimpleRoleDecoderDefinition extends SimpleResourceDefinition {

        private static final AbstractAddStepHandler ADD = new SimpleRoleDecoderAddHandler();
        private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, ROLE_DECODER_RUNTIME_CAPABILITY);

        SimpleRoleDecoderDefinition() {
            super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_ROLE_DECODER), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SIMPLE_ROLE_DECODER))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(ROLE_DECODER_RUNTIME_CAPABILITY));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTRIBUTE, null,
                    new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTE));
        }

    }

    private static class SimpleRoleDecoderAddHandler extends BaseAddHandler {

        private SimpleRoleDecoderAddHandler() {
            super(ROLE_DECODER_RUNTIME_CAPABILITY, ATTRIBUTE);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = ROLE_DECODER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName roleDecoderName = runtimeCapability.getCapabilityServiceName(RoleDecoder.class);

            final String attribute = ATTRIBUTE.resolveModelAttribute(context, model).asString();
            TrivialService<RoleDecoder> roleDecoderService = new TrivialService<RoleDecoder>(() -> RoleDecoder.simple(attribute));

            ServiceBuilder<RoleDecoder> roleDecoderBuilderBuilder = serviceTarget.addService(roleDecoderName, roleDecoderService);

            commonDependencies(roleDecoderBuilderBuilder)
                .setInitialMode(Mode.LAZY)
                .install();
        }

    }

    private static class SourceAddressRoleDecoderDefinition extends SimpleResourceDefinition {

        private static final AbstractAddStepHandler ADD = new SourceAddressRoleDecoderAddHandler();
        private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, ROLE_DECODER_RUNTIME_CAPABILITY);
        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{SOURCE_ADDRESS, PATTERN, ROLES};

        SourceAddressRoleDecoderDefinition() {
            super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER))
                    .setAddHandler(ADD)
                    .setRemoveHandler(REMOVE)
                    .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                    .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                    .setCapabilities(ROLE_DECODER_RUNTIME_CAPABILITY));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            for (AttributeDefinition attributeDefinition : ATTRIBUTES) {
                resourceRegistration.registerReadWriteAttribute(attributeDefinition, null,
                        new ElytronReloadRequiredWriteAttributeHandler(attributeDefinition));
            }
        }
    }

    private static class SourceAddressRoleDecoderAddHandler extends BaseAddHandler {

        private SourceAddressRoleDecoderAddHandler() {
            super(ROLE_DECODER_RUNTIME_CAPABILITY, SOURCE_ADDRESS, PATTERN, ROLES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = ROLE_DECODER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName roleDecoderName = runtimeCapability.getCapabilityServiceName(RoleDecoder.class);
            final String sourceAddress = SOURCE_ADDRESS.resolveModelAttribute(context, model).asStringOrNull();
            final String pattern = PATTERN.resolveModelAttribute(context, model).asStringOrNull();
            final List<String> roles = ROLES.unwrap(context, model);

            TrivialService<RoleDecoder> roleDecoderService;
            // one of 'source-address' or 'pattern' must be specified
            if (sourceAddress != null) {
                roleDecoderService = new TrivialService<>(() -> new SourceAddressRoleDecoder(sourceAddress, Roles.fromSet(new HashSet<>(roles))));
            } else {
                roleDecoderService = new TrivialService<>(() -> new SourceAddressRoleDecoder(Pattern.compile(pattern), Roles.fromSet(new HashSet<>(roles))));
            }

            ServiceBuilder<RoleDecoder> roleDecoderBuilderBuilder = serviceTarget.addService(roleDecoderName, roleDecoderService);

            commonDependencies(roleDecoderBuilderBuilder)
                    .setInitialMode(Mode.LAZY)
                    .install();
        }

    }

}
