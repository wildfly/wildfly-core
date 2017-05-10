/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.Capabilities.PERMISSION_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PERMISSION_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;

import java.security.Permissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.SimplePermissionMapper;
import org.wildfly.security.permission.InvalidPermissionClassException;
import org.wildfly.security.permission.PermissionUtil;
import org.wildfly.security.permission.PermissionVerifier;

/**
 * Definitions for resources describing {@link PermissionMapper} instances.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PermissionMapperDefinitions {

    static final SimpleAttributeDefinition LEFT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LEFT, ModelType.STRING, false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, PERMISSION_MAPPER_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition RIGHT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RIGHT, ModelType.STRING, false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, PERMISSION_MAPPER_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition LOGICAL_OPERATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOGICAL_OPERATION, ModelType.STRING, false)
            .setAllowExpression(true)
            .setAllowedValues(ElytronDescriptionConstants.AND, ElytronDescriptionConstants.OR, ElytronDescriptionConstants.XOR, ElytronDescriptionConstants.UNLESS)
            .setValidator(EnumValidator.create(LogicalMapperOperation.class, false, true))
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition MAPPING_MODE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAPPING_MODE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(ElytronDescriptionConstants.FIRST))
            .setAllowedValues(ElytronDescriptionConstants.AND, ElytronDescriptionConstants.OR, ElytronDescriptionConstants.XOR, ElytronDescriptionConstants.UNLESS, ElytronDescriptionConstants.FIRST)
            .setValidator(EnumValidator.create(MappingMode.class, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final StringListAttributeDefinition PRINCIPALS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.PRINCIPALS)
            .setAllowExpression(true)
            .setRequired(false)
            .setMinSize(1)
            .build();

    static final StringListAttributeDefinition ROLES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ROLES)
            .setAllowExpression(true)
            .setRequired(false)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition TARGET_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TARGET_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition ACTION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ACTION, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final ObjectTypeAttributeDefinition PERMISSION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION, CLASS_NAME, MODULE, TARGET_NAME, ACTION)
            .build();

    static final ObjectListAttributeDefinition PERMISSIONS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSIONS, PERMISSION)
            .setRequired(false)
            .build();

    static final ObjectTypeAttributeDefinition PERMISSION_MAPPING = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPING, PRINCIPALS, ROLES, PERMISSIONS)
            .build();

    static final ObjectListAttributeDefinition PERMISSION_MAPPINGS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPINGS, PERMISSION_MAPPING)
            .setRequired(false)
            .build();

    static ResourceDefinition getLogicalPermissionMapper() {
        AttributeDefinition[] attributes = new AttributeDefinition[] {LOGICAL_OPERATION, LEFT, RIGHT};
        TrivialAddHandler<PermissionMapper> add = new TrivialAddHandler<PermissionMapper>(PermissionMapper.class, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PermissionMapper> getValueSupplier(ServiceBuilder<PermissionMapper> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final InjectedValue<PermissionMapper> leftPermissionMapperInjector = new InjectedValue<>();
                final InjectedValue<PermissionMapper> rightPermissionMapperInjector = new InjectedValue<>();

                LogicalMapperOperation operation = LogicalMapperOperation.valueOf(LogicalMapperOperation.class, LOGICAL_OPERATION.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH));

                serviceBuilder.addDependency(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(PERMISSION_MAPPER_CAPABILITY, LEFT.resolveModelAttribute(context, model).asString()),
                        PermissionMapper.class), PermissionMapper.class, leftPermissionMapperInjector);

                serviceBuilder.addDependency(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(PERMISSION_MAPPER_CAPABILITY, RIGHT.resolveModelAttribute(context, model).asString()),
                        PermissionMapper.class), PermissionMapper.class, rightPermissionMapperInjector);

                return () -> operation.create(leftPermissionMapperInjector.getValue(), rightPermissionMapperInjector.getValue());
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.LOGICAL_PERMISSION_MAPPER, add, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getSimplePermissionMapper() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { MAPPING_MODE, PERMISSION_MAPPINGS };
        TrivialAddHandler<PermissionMapper>  add = new TrivialAddHandler<PermissionMapper>(PermissionMapper.class, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PermissionMapper> getValueSupplier(ServiceBuilder<PermissionMapper> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final MappingMode mappingMode = MappingMode.valueOf(MappingMode.class, MAPPING_MODE.resolveModelAttribute(context, model).asString().toUpperCase(Locale.ENGLISH));

                final List<Mapping> permissionMappings = new ArrayList<>();
                if (model.hasDefined(ElytronDescriptionConstants.PERMISSION_MAPPINGS)) {
                    for (ModelNode permissionMapping : model.get(ElytronDescriptionConstants.PERMISSION_MAPPINGS).asList()) {
                        Set<String> principals = new HashSet<>(PRINCIPALS.unwrap(context, permissionMapping));
                        Set<String> roles = new HashSet<>(ROLES.unwrap(context, permissionMapping));

                        List<Permission> permissions = new ArrayList<>();
                        if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSIONS)) {
                            for (ModelNode permission : permissionMapping.require(ElytronDescriptionConstants.PERMISSIONS).asList()) {
                                permissions.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                        asStringIfDefined(context, MODULE, permission),
                                        asStringIfDefined(context, TARGET_NAME, permission),
                                        asStringIfDefined(context, ACTION, permission)));
                            }
                        }

                        permissionMappings.add(new Mapping(principals, roles, permissions));
                    }
                }

                return () -> createSimplePermissionMapper(mappingMode, permissionMappings);
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER, add, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY);
    }

    private static PermissionMapper createSimplePermissionMapper(MappingMode mappingMode, List<Mapping> mappings) throws StartException {
        SimplePermissionMapper.Builder builder = SimplePermissionMapper.builder();
        builder.setMappingMode(mappingMode.convert());
        for (Mapping current : mappings) {

            Permissions permissions = new Permissions();
            for (Permission permission : current.getPermissions()) {
                final java.security.Permission realPerm = createPermission(permission);
                if (realPerm != null) permissions.add(realPerm);
            }

            builder.addMapping(current.getPrincipals(), current.getRoles(), PermissionVerifier.from(permissions));
        }

        return builder.build();

    }

    static ResourceDefinition getConstantPermissionMapper() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PERMISSIONS };
        TrivialAddHandler<PermissionMapper>  add = new TrivialAddHandler<PermissionMapper>(PermissionMapper.class, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PermissionMapper> getValueSupplier(ServiceBuilder<PermissionMapper> serviceBuilder,
                                                                       OperationContext context, ModelNode model) throws OperationFailedException {

                List<Permission> permissions = new ArrayList<>();
                if (model.hasDefined(ElytronDescriptionConstants.PERMISSIONS)) {
                    for (ModelNode permission : model.require(ElytronDescriptionConstants.PERMISSIONS).asList()) {
                        permissions.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                asStringIfDefined(context, MODULE, permission),
                                asStringIfDefined(context, TARGET_NAME, permission),
                                asStringIfDefined(context, ACTION, permission)));
                    }
                }

                return () -> createConstantPermissionMapper(permissions);
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER, add, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY);
    }

    private static PermissionMapper createConstantPermissionMapper(List<Permission> permissionsList) throws StartException {
        Permissions permissions = new Permissions();
        for (Permission permission : permissionsList) {
            final java.security.Permission realPerm = createPermission(permission);
            if (realPerm != null) permissions.add(realPerm);
        }
        return PermissionMapper.createConstant(PermissionVerifier.from(permissions));
    }

    private static java.security.Permission createPermission(Permission permission) throws StartException {
        Module currentModule = Module.getCallerModule();
        if (permission.getModule() != null && currentModule != null) {
            ModuleIdentifier mi = ModuleIdentifier.fromString(permission.getModule());
            try {
                currentModule = currentModule.getModule(mi);
            } catch (ModuleLoadException e) {
                // If we cannot load it, it can never be checked.
                return null;
            }
        }

        ClassLoader classLoader = currentModule != null ? currentModule.getClassLoader() : PermissionMapperDefinitions.class.getClassLoader();
        try {
            return PermissionUtil.createPermission(classLoader, permission.getClassName(), permission.getTargetName(), permission.getAction());
        } catch (InvalidPermissionClassException e) {
            // If we cannot load it, it can never be checked.
            return null;
        } catch (Throwable e) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.exceptionWhileCreatingPermission(permission.getClassName(), e);
        }
    }


    static class Permission {
        private final String className;
        private final String module;
        private final String targetName;
        private final String action;

        Permission(final String className, final String module, final String targetName, final String action) {
            this.className = className;
            this.module = module;
            this.targetName = targetName;
            this.action = action;
        }

        public String getClassName() {
            return className;
        }

        public String getModule() {
            return module;
        }

        public String getTargetName() {
            return targetName;
        }

        public String getAction() {
            return action;
        }
    }

    static class Mapping {
        private final Set<String> principals;
        private final Set<String> roles;
        private final List<Permission> permissions;

        Mapping(Set<String> principals, Set<String> roles, List<Permission> permissions) {
            this.principals = principals;
            this.roles = roles;
            this.permissions = permissions;
        }

        public Set<String> getPrincipals() {
            return principals;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public List<Permission> getPermissions() {
            return permissions;
        }

    }


    private enum MappingMode {

        AND,

        OR,

        XOR,

        UNLESS,

        FIRST;

        SimplePermissionMapper.MappingMode convert() {
            switch (this) {
                case AND:
                    return SimplePermissionMapper.MappingMode.AND;
                case OR:
                    return SimplePermissionMapper.MappingMode.OR;
                case XOR:
                    return SimplePermissionMapper.MappingMode.XOR;
                case UNLESS:
                    return SimplePermissionMapper.MappingMode.UNLESS;
                default:
                    return SimplePermissionMapper.MappingMode.FIRST_MATCH;
            }
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.US);
        }
    }

    private enum LogicalMapperOperation {

        AND((l,r) -> l.and(r)),

        OR((l,r) -> l.or(r)),

        XOR((l,r) -> l.xor(r)),

        UNLESS((l,r) -> l.unless(r));

        private final BinaryOperator<PermissionMapper> operator;

        LogicalMapperOperation(BinaryOperator<PermissionMapper> operator) {
            this.operator = operator;
        }

        PermissionMapper create (PermissionMapper left, PermissionMapper right) {
            return operator.apply(left, right);
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.US);
        }
    }
}
