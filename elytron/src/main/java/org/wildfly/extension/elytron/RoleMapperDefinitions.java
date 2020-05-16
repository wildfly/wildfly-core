/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.ROLE_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.ROLE_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.authz.MappedRoleMapper;
import org.wildfly.security.authz.RegexRoleMapper;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.authz.Roles;

/**
 * Container class for the RoleMapping definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RoleMapperDefinitions {

    static final SimpleAttributeDefinition SUFFIX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SUFFIX, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PREFIX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PREFIX, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PATTERN = new SimpleAttributeDefinitionBuilder(RegexAttributeDefinitions.PATTERN).build();

    static final SimpleAttributeDefinition REPLACEMENT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REPLACEMENT, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition LEFT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LEFT, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(ROLE_MAPPER_CAPABILITY, ROLE_MAPPER_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition RIGHT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RIGHT, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(ROLE_MAPPER_CAPABILITY, ROLE_MAPPER_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition LOGICAL_OPERATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOGICAL_OPERATION, ModelType.STRING, false)
        .setAllowExpression(true)
        .setAllowedValues(ElytronDescriptionConstants.AND, ElytronDescriptionConstants.MINUS, ElytronDescriptionConstants.OR, ElytronDescriptionConstants.XOR)
        .setValidator(EnumValidator.create(LogicalOperation.class, false, true))
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleMapAttributeDefinition ROLE_MAPPING_MAP = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.ROLE_MAP, ModelType.LIST, false)
            .setMinSize(1)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEEP_MAPPED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEEP_MAPPED, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition KEEP_NON_MAPPED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEEP_NON_MAPPED, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REPLACE_ALL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REPLACE_ALL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition ROLES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ROLES)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setXmlName(ModelDescriptionConstants.ROLE)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST_NAMED_ELEMENT)
            .setAttributeParser(AttributeParsers.STRING_LIST_NAMED_ELEMENT)
            .build();

    private static final AggregateComponentDefinition<RoleMapper> AGGREGATE_ROLE_MAPPER = AggregateComponentDefinition.create(RoleMapper.class,
            ElytronDescriptionConstants.AGGREGATE_ROLE_MAPPER, ElytronDescriptionConstants.ROLE_MAPPERS, ROLE_MAPPER_RUNTIME_CAPABILITY,
            (RoleMapper[] r) -> RoleMapper.aggregate(r));

    static ResourceDefinition getMappedRoleMapperDefinition() {
        AbstractAddStepHandler add = new RoleMapperAddHandler(ROLE_MAPPING_MAP, KEEP_MAPPED, KEEP_NON_MAPPED) {

            @Override
            protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
                ModelNode roleMappingMapNode = ROLE_MAPPING_MAP.resolveModelAttribute(context, model);
                boolean keepMapped = KEEP_MAPPED.resolveModelAttribute(context, model).asBoolean();
                boolean keepNonMapped = KEEP_NON_MAPPED.resolveModelAttribute(context, model).asBoolean();
                Set<String> keys = roleMappingMapNode.keys();
                final Map<String, Set<String>> roleMappingMap = new LinkedHashMap<>(keys.size());
                for (String s : keys) {
                    List<ModelNode> currentList = roleMappingMapNode.require(s).asList();
                    Set<String> set = new LinkedHashSet<>();
                    for (ModelNode m : currentList) {
                        set.add(m.asString());
                    }
                    roleMappingMap.put(s, set);
                }

                final MappedRoleMapper roleMapper = MappedRoleMapper.builder()
                        .setRoleMap(roleMappingMap)
                        .build();

                final Roles keyRoles = Roles.fromSet(roleMappingMap.keySet());

                if (keepMapped && keepNonMapped) {
                    return () -> roleMapper.or(RoleMapper.IDENTITY_ROLE_MAPPER);
                }
                if (keepMapped) {
                    return () -> roleMapper.or(delegate -> delegate.and(keyRoles));
                }
                if (keepNonMapped) {
                    return () -> roleMapper.or(delegate -> delegate.minus(keyRoles));
                }
                return () -> roleMapper;

            }
        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER, add, ROLE_MAPPING_MAP, KEEP_MAPPED, KEEP_NON_MAPPED);
    }

    static ResourceDefinition getRegexRoleMapperDefinition() {
        AbstractAddStepHandler add = new RoleMapperAddHandler(PATTERN, REPLACEMENT, KEEP_NON_MAPPED, REPLACE_ALL) {

            @Override
            protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
                final String regex = PATTERN.resolveModelAttribute(context, model).asString();
                final String replacement = REPLACEMENT.resolveModelAttribute(context, model).asString();
                final Boolean keepNonMapped = KEEP_NON_MAPPED.resolveModelAttribute(context, model).asBoolean();
                final Boolean replaceAll = REPLACE_ALL.resolveModelAttribute(context, model).asBoolean();

                final RegexRoleMapper roleMapper = new RegexRoleMapper.Builder()
                        .setPattern(regex)
                        .setReplacement(replacement)
                        .setKeepNonMapped(keepNonMapped)
                        .setReplaceAll(replaceAll)
                        .build();

                return () -> roleMapper;

            }
        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.REGEX_ROLE_MAPPER, add, PATTERN, REPLACEMENT, KEEP_NON_MAPPED, REPLACE_ALL);
    }

    static AggregateComponentDefinition<RoleMapper> getAggregateRoleMapperDefinition() {
        return AGGREGATE_ROLE_MAPPER;
    }

    static ResourceDefinition getAddSuffixRoleMapperDefinition() {
        AbstractAddStepHandler add = new RoleMapperAddHandler(SUFFIX) {

            @Override
            protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
                final String suffix = SUFFIX.resolveModelAttribute(context, model).asString();

                return () -> (Roles r) -> r.addSuffix(suffix);
            }

        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.ADD_SUFFIX_ROLE_MAPPER, add, SUFFIX);
    }

    static ResourceDefinition getAddPrefixRoleMapperDefinition() {
        AbstractAddStepHandler add = new RoleMapperAddHandler(PREFIX) {

            @Override
            protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
                final String prefix = PREFIX.resolveModelAttribute(context, model).asString();

                return () -> (Roles r) -> r.addPrefix(prefix);
            }

        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.ADD_PREFIX_ROLE_MAPPER, add, PREFIX);
    }

    static ResourceDefinition getLogicalRoleMapperDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { LOGICAL_OPERATION, LEFT, RIGHT };
        AbstractAddStepHandler add = new RoleMapperAddHandler(attributes) {

            /* (non-Javadoc)
             * @see org.wildfly.extension.elytron.RoleMapperDefinitions.RoleMapperAddHandler#installService(org.jboss.as.controller.OperationContext, org.jboss.msc.service.ServiceName, org.jboss.dmr.ModelNode)
             */
            @Override
            protected ServiceBuilder<RoleMapper> installService(OperationContext context, ServiceName roleMapperName,
                    ModelNode model) throws OperationFailedException {
                final InjectedValue<RoleMapper> leftRoleMapperInjector = new InjectedValue<RoleMapper>();
                final InjectedValue<RoleMapper> rightRoleMapperInjector = new InjectedValue<RoleMapper>();

                LogicalOperation operation = LogicalOperation.valueOf(LogicalOperation.class, LOGICAL_OPERATION.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH));

                TrivialService<RoleMapper> roleMapperService = new TrivialService<RoleMapper>(() -> operation.create(leftRoleMapperInjector.getValue(), rightRoleMapperInjector.getValue()));

                ServiceTarget serviceTarget = context.getServiceTarget();

                ServiceBuilder<RoleMapper> serviceBuilder = serviceTarget.addService(roleMapperName, roleMapperService);

                String leftName = LEFT.resolveModelAttribute(context, model).asStringOrNull();
                if (leftName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            RuntimeCapability.buildDynamicCapabilityName(ROLE_MAPPER_CAPABILITY, leftName), RoleMapper.class),
                            RoleMapper.class, leftRoleMapperInjector);
                } else {
                    leftRoleMapperInjector.inject(RoleMapper.IDENTITY_ROLE_MAPPER);
                }

                String rightName = RIGHT.resolveModelAttribute(context, model).asStringOrNull();
                if (rightName != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            RuntimeCapability.buildDynamicCapabilityName(ROLE_MAPPER_CAPABILITY, rightName), RoleMapper.class),
                            RoleMapper.class, rightRoleMapperInjector);
                } else {
                    rightRoleMapperInjector.inject(RoleMapper.IDENTITY_ROLE_MAPPER);
                }

                return serviceBuilder;
            }

        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.LOGICAL_ROLE_MAPPER, add, attributes);
    }

    static ResourceDefinition getConstantRoleMapperDefinition() {
        AbstractAddStepHandler add = new RoleMapperAddHandler(ROLES) {

            @Override
            protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
                List<String> rolesList = ROLES.unwrap(context, model);
                final Roles roles = Roles.fromSet(new HashSet<>(rolesList));

                return () -> (Roles r) -> roles;
            }
        };

        return new RoleMapperResourceDefinition(ElytronDescriptionConstants.CONSTANT_ROLE_MAPPER, add, ROLES);
    }

    private static class RoleMapperResourceDefinition extends SimpleResourceDefinition {

        private final String pathKey;
        private final AttributeDefinition[] attributes;

        RoleMapperResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition ... attributes) {
            super(new Parameters(PathElement.pathElement(pathKey),
                    ElytronExtension.getResourceDescriptionResolver(pathKey))
                .setAddHandler(add)
                .setRemoveHandler(new TrivialCapabilityServiceRemoveHandler(add, ROLE_MAPPER_RUNTIME_CAPABILITY))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(ROLE_MAPPER_RUNTIME_CAPABILITY));
            this.pathKey = pathKey;
            this.attributes = attributes;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
             if (attributes != null && attributes.length > 0) {
                 AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(attributes);
                 for (AttributeDefinition current : attributes) {
                     resourceRegistration.registerReadWriteAttribute(current, null, write);
                 }
             }
        }

    }

    private static class RoleMapperAddHandler extends BaseAddHandler {


        private RoleMapperAddHandler(AttributeDefinition ... attributes) {
            super(ROLE_MAPPER_RUNTIME_CAPABILITY, attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            RuntimeCapability<Void> runtimeCapability = ROLE_MAPPER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName roleMapperName = runtimeCapability.getCapabilityServiceName(RoleMapper.class);

            commonDependencies(installService(context, roleMapperName, model))
                .setInitialMode(Mode.LAZY)
                .install();
        }

        protected ServiceBuilder<RoleMapper> installService(OperationContext context, ServiceName roleMapperName, ModelNode model) throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            TrivialService<RoleMapper> roleMapperService = new TrivialService<RoleMapper>(getValueSupplier(context, model));

            return serviceTarget.addService(roleMapperName, roleMapperService);
        }

        protected ValueSupplier<RoleMapper> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
            return () -> null;
        }

    }

    private enum LogicalOperation {

        AND((RoleMapper left, RoleMapper right) -> left.and(right)),

        MINUS((RoleMapper left, RoleMapper right) -> left.minus(right)),

        OR((RoleMapper left, RoleMapper right) -> left.or(right)),

        XOR((RoleMapper left, RoleMapper right) -> left.xor(right));

        private final BinaryOperator<RoleMapper> operation;

        LogicalOperation(BinaryOperator<RoleMapper> operation) {
            this.operation = operation;
        }

        RoleMapper create(RoleMapper left, RoleMapper right) {
            return operation.apply(left, right);
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.US);
        }
    }

}
