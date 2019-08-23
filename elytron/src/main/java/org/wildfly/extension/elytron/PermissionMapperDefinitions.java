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
import static org.wildfly.extension.elytron.Capabilities.PERMISSION_SET_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;

import java.security.Permissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
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
            .setRestartAllServices()
            .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, PERMISSION_MAPPER_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition RIGHT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RIGHT, ModelType.STRING, false)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(PERMISSION_MAPPER_CAPABILITY, PERMISSION_MAPPER_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition LOGICAL_OPERATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOGICAL_OPERATION, ModelType.STRING, false)
            .setAllowExpression(true)
            .setAllowedValues(ElytronDescriptionConstants.AND, ElytronDescriptionConstants.OR, ElytronDescriptionConstants.XOR, ElytronDescriptionConstants.UNLESS)
            .setValidator(EnumValidator.create(LogicalMapperOperation.class, false, true))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MAPPING_MODE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAPPING_MODE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(ElytronDescriptionConstants.FIRST))
            .setAllowedValues(ElytronDescriptionConstants.AND, ElytronDescriptionConstants.OR, ElytronDescriptionConstants.XOR, ElytronDescriptionConstants.UNLESS, ElytronDescriptionConstants.FIRST)
            .setValidator(EnumValidator.create(MappingMode.class, EnumSet.allOf(MappingMode.class)))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MATCH_ALL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MATCH_ALL, ModelType.BOOLEAN, true)
            .setCorrector(new ParameterCorrector() {
                public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
                    if (newValue.isDefined() && "false".equals(newValue.asString())) {
                        newValue.clear();
                    }
                    return newValue;
                }
            })
            .setAllowExpression(false) // Only one value possible if present
            .setAlternatives(ElytronDescriptionConstants.PRINCIPALS, ElytronDescriptionConstants.ROLES)
            .setRestartAllServices()
            .build();


    static final StringListAttributeDefinition PRINCIPALS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.PRINCIPALS)
            .setAllowExpression(true)
            .setRequired(false)
            .setAlternatives(ElytronDescriptionConstants.MATCH_ALL)
            .setMinSize(1)
            .setXmlName(ElytronDescriptionConstants.PRINCIPAL)
            .setAttributeParser(AttributeParsers.STRING_LIST_NAMED_ELEMENT)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST_NAMED_ELEMENT)
            .build();

    static final StringListAttributeDefinition ROLES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ROLES)
            .setAllowExpression(true)
            .setRequired(false)
            .setAlternatives(ElytronDescriptionConstants.MATCH_ALL)
            .setMinSize(1)
            .setXmlName("role")
            .setAttributeParser(AttributeParsers.STRING_LIST_NAMED_ELEMENT)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST_NAMED_ELEMENT)
            .build();

    static final SimpleAttributeDefinition TARGET_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TARGET_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(0)
            .build();

    static final SimpleAttributeDefinition ACTION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ACTION, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(0)
            .build();

    static final ObjectTypeAttributeDefinition PERMISSION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION, CLASS_NAME, MODULE, TARGET_NAME, ACTION)
            .build();

    static final ObjectListAttributeDefinition PERMISSIONS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSIONS, PERMISSION)
            .setRequired(false)
            .setAlternatives(ElytronDescriptionConstants.PERMISSION_SETS)
            .setRestartAllServices()
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setDeprecated(ModelVersion.create(3, 0))
            .build();

    static final SimpleAttributeDefinition PERMISSION_SET_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PERMISSION_SET, ModelType.STRING, false)
            .setXmlName(ElytronDescriptionConstants.NAME)
            .setMinSize(1)
            .setCapabilityReference(PERMISSION_SET_CAPABILITY, PERMISSION_MAPPER_CAPABILITY, true)
            .build();

    static final ObjectTypeAttributeDefinition PERMISSION_SET = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_SET, PERMISSION_SET_NAME)
            .build();

    static final ObjectListAttributeDefinition PERMISSION_SETS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_SETS, PERMISSION_SET)
            .setRequired(false)
            .setAlternatives(ElytronDescriptionConstants.PERMISSIONS)
            .setRestartAllServices()
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .build();


    static class MatchAllCorrector implements ParameterCorrector {
        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            // Remove any 'undefined' match-all field. An alternative would be to ensure
            // the field is always present (defined or not). We need to do one
            // or the other to allow SubsystemParsingTestCase to pass, otherwise models
            // from xml with this set to 'false' will have an undefined match-all, while
            // models read from the persisted form of the first kind of model will not
            // have the field at all, leading to comparison failures.
            // Before WFCORE-3255, this corrector would remove the field if the op had
            // set it to false, so to be consistent with previous behavior we go down
            // the path of removing the field.
            String name = MATCH_ALL.getName();
            if (newValue.isDefined() && !newValue.hasDefined(name) && newValue.has(name)) {
                newValue.remove(name);
            }
            return newValue;
        }
    }

    static class PermissionMappingCorrector implements ParameterCorrector {
        // HACK until corrections chain into nested attributes

        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            if (!newValue.isDefined() || newValue.getType() != ModelType.LIST) {
                return newValue;
            }
            for (ModelNode newNode : newValue.asList()) {
                PERMISSION_MAPPING.getCorrector().correct(newNode, new ModelNode());
            }

            return newValue;
        }
    }


    static final ObjectTypeAttributeDefinition PERMISSION_MAPPING = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPING, MATCH_ALL, PRINCIPALS, ROLES, PERMISSIONS, PERMISSION_SETS)
            .setCorrector(new MatchAllCorrector())
            .build();

    static final ObjectListAttributeDefinition PERMISSION_MAPPINGS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPINGS, PERMISSION_MAPPING)
            .setRequired(false)
            .setCorrector(new PermissionMappingCorrector())
            .setRestartAllServices()
            .setAttributeMarshaller(AttributeMarshallers.OBJECT_LIST_UNWRAPPED)
            .setAttributeParser(AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER)
            .build()
            ;

    //the _1_0 versions only exist to support legacy format of persistence
    static final StringListAttributeDefinition ROLES_1_0 = new StringListAttributeDefinition.Builder(ROLES)
            .setXmlName(ElytronDescriptionConstants.ROLES)
            .setAttributeParser(AttributeParsers.STRING_LIST)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST)
            .build();

    static final StringListAttributeDefinition PRINCIPALS_1_0 = new StringListAttributeDefinition.Builder(PRINCIPALS)
            .setXmlName(ElytronDescriptionConstants.PRINCIPALS)
            .setAttributeParser(AttributeParsers.STRING_LIST)
            .setAttributeMarshaller(AttributeMarshallers.STRING_LIST)
            .build();


    private static final ObjectTypeAttributeDefinition PERMISSION_MAPPING_1_0 = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPING, MATCH_ALL, PRINCIPALS_1_0, ROLES_1_0, PERMISSIONS)
            .setCorrector(new MatchAllCorrector())
            .build();

    static final ObjectListAttributeDefinition PERMISSION_MAPPINGS_1_0 = new ObjectListAttributeDefinition.Builder(PERMISSION_MAPPINGS)
            .setValueType(PERMISSION_MAPPING_1_0)
            .build();

    private static final ObjectTypeAttributeDefinition PERMISSION_MAPPING_1_1 = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PERMISSION_MAPPING, MATCH_ALL, PRINCIPALS, ROLES, PERMISSIONS)
            .setCorrector(new MatchAllCorrector())
            .build();

    static final ObjectListAttributeDefinition PERMISSION_MAPPINGS_1_1 = new ObjectListAttributeDefinition.Builder(PERMISSION_MAPPINGS)
            .setValueType(PERMISSION_MAPPING_1_1)
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
                        boolean matchAll = MATCH_ALL.resolveModelAttribute(context, permissionMapping).asBoolean(false);
                        Set<String> principals = !matchAll ? new HashSet<>(PRINCIPALS.unwrap(context, permissionMapping)) : Collections.emptySet();
                        Set<String> roles = !matchAll ? new HashSet<>(ROLES.unwrap(context, permissionMapping)) : Collections.emptySet();

                        List<Permission> permissions = new ArrayList<>();
                        if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSIONS)) {
                            for (ModelNode permission : permissionMapping.require(ElytronDescriptionConstants.PERMISSIONS).asList()) {
                                permissions.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                        MODULE.resolveModelAttribute(context, permission).asStringOrNull(),
                                        TARGET_NAME.resolveModelAttribute(context, permission).asStringOrNull(),
                                        ACTION.resolveModelAttribute(context, permission).asStringOrNull()));
                            }
                        }

                        List<InjectedValue<Permissions>> permissionSetInjectors = new ArrayList<>();
                        if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSION_SETS)) {
                            for (ModelNode permissionSet : permissionMapping.require(ElytronDescriptionConstants.PERMISSION_SETS).asList()) {
                                InjectedValue<Permissions> permissionSetInjector = new InjectedValue<>();
                                String permissionSetName = PERMISSION_SET_NAME.resolveModelAttribute(context, permissionSet).asString();
                                String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(PERMISSION_SET_CAPABILITY, permissionSetName);
                                ServiceName permissionSetServiceName = context.getCapabilityServiceName(runtimeCapability, Permissions.class);
                                serviceBuilder.addDependency(permissionSetServiceName, Permissions.class, permissionSetInjector);
                                permissionSetInjectors.add(permissionSetInjector);
                            }
                        }

                        permissionMappings.add(new Mapping(principals, roles, permissions, permissionSetInjectors, matchAll));
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

            Permissions permissions = createPermissions(current.getPermissions(), current.getPermissionSetInjectors());

            if (current.matchAll()) {
                builder.addMatchAllPrincipals(PermissionVerifier.from(permissions));
            } else {
                builder.addMapping(current.getPrincipals(), current.getRoles(), PermissionVerifier.from(permissions));
            }
        }

        return builder.build();

    }

    static ResourceDefinition getConstantPermissionMapper() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PERMISSIONS, PERMISSION_SETS };
        TrivialAddHandler<PermissionMapper>  add = new TrivialAddHandler<PermissionMapper>(PermissionMapper.class, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<PermissionMapper> getValueSupplier(ServiceBuilder<PermissionMapper> serviceBuilder,
                                                                       OperationContext context, ModelNode model) throws OperationFailedException {

                List<Permission> permissions = new ArrayList<>();
                if (model.hasDefined(ElytronDescriptionConstants.PERMISSIONS)) {
                    for (ModelNode permission : model.require(ElytronDescriptionConstants.PERMISSIONS).asList()) {
                        permissions.add(new Permission(CLASS_NAME.resolveModelAttribute(context, permission).asString(),
                                MODULE.resolveModelAttribute(context, permission).asStringOrNull(),
                                TARGET_NAME.resolveModelAttribute(context, permission).asStringOrNull(),
                                ACTION.resolveModelAttribute(context, permission).asStringOrNull()));
                    }
                }

                List<InjectedValue<Permissions>> permissionSetInjectors = new ArrayList<>();
                if (model.hasDefined(ElytronDescriptionConstants.PERMISSION_SETS)) {
                    for (ModelNode permissionSet : model.require(ElytronDescriptionConstants.PERMISSION_SETS).asList()) {
                        InjectedValue<Permissions> permissionSetInjector = new InjectedValue<>();
                        String permissionSetName = PERMISSION_SET_NAME.resolveModelAttribute(context, permissionSet).asString();
                        String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(PERMISSION_SET_CAPABILITY, permissionSetName);
                        ServiceName permissionSetServiceName = context.getCapabilityServiceName(runtimeCapability, Permissions.class);
                        serviceBuilder.addDependency(permissionSetServiceName, Permissions.class, permissionSetInjector);
                        permissionSetInjectors.add(permissionSetInjector);
                    }
                }

                return () -> createConstantPermissionMapper(permissions, permissionSetInjectors);
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER, add, attributes, PERMISSION_MAPPER_RUNTIME_CAPABILITY);
    }

    private static PermissionMapper createConstantPermissionMapper(List<Permission> permissionsList, List<InjectedValue<Permissions>> permissionSetInjectors) throws StartException {
        Permissions allPermissions = createPermissions(permissionsList, permissionSetInjectors);
        return PermissionMapper.createConstant(PermissionVerifier.from(allPermissions));
    }

    private static Permissions createPermissions(List<Permission> permissionsList, List<InjectedValue<Permissions>> permissionSetInjectors) throws StartException {
        Permissions allPermissions = createPermissions(permissionsList);
        for (InjectedValue<Permissions> permissionSetInjector : permissionSetInjectors) {
            Permissions permissionSet = permissionSetInjector.getValue();
            Enumeration<java.security.Permission> permissions = permissionSet.elements();
            while (permissions.hasMoreElements()) {
                allPermissions.add(permissions.nextElement());
            }
        }
        return allPermissions;
    }

    static Permissions createPermissions(List<Permission> permissionsList) throws StartException {
        Permissions permissions = new Permissions();
        for (Permission permission : permissionsList) {
            final java.security.Permission realPerm = createPermission(permission);
            permissions.add(realPerm);
        }
        return permissions;
    }

    private static java.security.Permission createPermission(Permission permission) throws StartException {
        Module currentModule = Module.getCallerModule();
        if (permission.getModule() != null && currentModule != null) {
            ModuleIdentifier mi = ModuleIdentifier.fromString(permission.getModule());
            try {
                currentModule = currentModule.getModule(mi);
            } catch (ModuleLoadException e) {
                // If we cannot load it, it can never be checked.
                throw ElytronSubsystemMessages.ROOT_LOGGER.invalidPermissionModule(permission.getModule(), e);
            }
        }

        ClassLoader classLoader = currentModule != null ? currentModule.getClassLoader() : PermissionMapperDefinitions.class.getClassLoader();
        try {
            return PermissionUtil.createPermission(classLoader, permission.getClassName(), permission.getTargetName(), permission.getAction());
        } catch (InvalidPermissionClassException e) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.invalidPermissionClass(permission.getClassName());
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
        private boolean matchAll;
        private final Set<String> principals;
        private final Set<String> roles;
        private final List<Permission> permissions;
        private final List<InjectedValue<Permissions>> permissionSetInjectors;

        Mapping(Set<String> principals, Set<String> roles, List<Permission> permissions, List<InjectedValue<Permissions>> permissionSetInjectors, boolean matchAll) {
            this.principals = principals;
            this.roles = roles;
            this.permissions = permissions;
            this.permissionSetInjectors = permissionSetInjectors;
            this.matchAll = matchAll;
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

        public List<InjectedValue<Permissions>> getPermissionSetInjectors() {
            return permissionSetInjectors;
        }

        public boolean matchAll() {
            return matchAll;
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
