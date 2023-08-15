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

import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.RegexAttributeDefinitions.PATTERN_CAPTURE_GROUP;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.common.ElytronReloadRequiredWriteAttributeHandler;
import org.wildfly.extension.elytron.common.TrivialService;
import org.wildfly.extension.elytron.common.TrivialService.ValueSupplier;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.util.MappedRegexRealmMapper;
import org.wildfly.security.auth.util.SimpleRegexRealmMapper;


/**
 * Holder class for the {@link RealmMapper} definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RealmMapperDefinitions {

    static final SimpleAttributeDefinition DELEGATE_REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DELEGATE_REALM_MAPPER, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(REALM_MAPPER_CAPABILITY, REALM_MAPPER_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_NAME, ModelType.STRING, false)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final PropertiesAttributeDefinition REALM_REALM_MAP = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.REALM_MAP, false)
        .setMinSize(1)
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();


    static ResourceDefinition getSimpleRegexRealmMapperDefinition() {
        return new SimpleRegexRealmMapperDefinition();
    }

    static ResourceDefinition getMappedRegexRealmMapper() {
        return new MappedRegexRealmMapperDefinition();
    }

    static final AttributeDefinition[] CONSTANT_REALM_MAPPER_ATTRIBUTES = new AttributeDefinition[] { REALM_NAME };

    static ResourceDefinition getConstantRealmMapper() {
        AbstractAddStepHandler add = new TrivialAddHandler<RealmMapper>(RealmMapper.class, CONSTANT_REALM_MAPPER_ATTRIBUTES, REALM_MAPPER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<RealmMapper> getValueSupplier(ServiceBuilder<RealmMapper> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {
                final String realmName = REALM_NAME.resolveModelAttribute(context, model).asString();

                return () -> RealmMapper.single(realmName);
            }
        };

        return TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.CONSTANT_REALM_MAPPER)
                .setAddHandler(add)
                .setAttributes(CONSTANT_REALM_MAPPER_ATTRIBUTES)
                .setRuntimeCapabilities(REALM_MAPPER_RUNTIME_CAPABILITY).build();
    }

    private static class SimpleRegexRealmMapperDefinition extends SimpleResourceDefinition {

        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PATTERN_CAPTURE_GROUP, DELEGATE_REALM_MAPPER };

        private static final AbstractAddStepHandler ADD = new SimpleRegexRealmMapperAddHandler(ATTRIBUTES);
        private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, REALM_MAPPER_RUNTIME_CAPABILITY);

        private SimpleRegexRealmMapperDefinition() {
            super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_REGEX_REALM_MAPPER), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SIMPLE_REGEX_REALM_MAPPER))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(REALM_MAPPER_RUNTIME_CAPABILITY));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
            for (AttributeDefinition current : ATTRIBUTES) {
                resourceRegistration.registerReadWriteAttribute(current, null, write);
            }
        }

    }

    private static class SimpleRegexRealmMapperAddHandler extends BaseAddHandler {

        private SimpleRegexRealmMapperAddHandler(final AttributeDefinition[] attributes) {
            super(REALM_MAPPER_RUNTIME_CAPABILITY, attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = REALM_MAPPER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmMapperName = runtimeCapability.getCapabilityServiceName(RealmMapper.class);

            final String pattern = PATTERN_CAPTURE_GROUP.resolveModelAttribute(context, model).asString();
            String delegateRealmMapper = DELEGATE_REALM_MAPPER.resolveModelAttribute(context, model).asStringOrNull();

            final InjectedValue<RealmMapper> delegateRealmMapperInjector = new InjectedValue<RealmMapper>();

            TrivialService<RealmMapper> realmMapperService = new TrivialService<RealmMapper>(() -> {
                RealmMapper delegate = delegateRealmMapperInjector.getOptionalValue();
                Pattern compiledPattern = Pattern.compile(pattern);
                if (delegate == null) {
                    return new SimpleRegexRealmMapper(compiledPattern);
                } else {
                    return new SimpleRegexRealmMapper(compiledPattern, delegate);
                }
            });

            ServiceBuilder<RealmMapper> realmMapperBuilder = serviceTarget.addService(realmMapperName, realmMapperService);

            if (delegateRealmMapper != null) {
                String delegateCapabilityName = RuntimeCapability.buildDynamicCapabilityName(REALM_MAPPER_CAPABILITY, delegateRealmMapper);
                ServiceName delegateServiceName = context.getCapabilityServiceName(delegateCapabilityName, RealmMapper.class);

                realmMapperBuilder.addDependency(delegateServiceName, RealmMapper.class, delegateRealmMapperInjector);
            }

            commonDependencies(realmMapperBuilder)
                .setInitialMode(Mode.LAZY)
                .install();
        }

    }

    private static class MappedRegexRealmMapperDefinition extends SimpleResourceDefinition {

        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { PATTERN_CAPTURE_GROUP, REALM_REALM_MAP, DELEGATE_REALM_MAPPER };

        private static final AbstractAddStepHandler ADD = new MappedRegexRealmMapperAddHandler(ATTRIBUTES);
        private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, REALM_MAPPER_RUNTIME_CAPABILITY);

        private MappedRegexRealmMapperDefinition() {
            super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(REALM_MAPPER_RUNTIME_CAPABILITY));
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
            for (AttributeDefinition current : ATTRIBUTES) {
                resourceRegistration.registerReadWriteAttribute(current, null, write);
            }
        }

    }

    private static class MappedRegexRealmMapperAddHandler extends BaseAddHandler {

        private MappedRegexRealmMapperAddHandler(final AttributeDefinition[] attributes) {
            super(REALM_MAPPER_RUNTIME_CAPABILITY, attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = REALM_MAPPER_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmMapperName = runtimeCapability.getCapabilityServiceName(RealmMapper.class);

            final String pattern = PATTERN_CAPTURE_GROUP.resolveModelAttribute(context, model).asString();

            ModelNode realmMapList = REALM_REALM_MAP.resolveModelAttribute(context, model);
            Set<String> names = realmMapList.keys();
            final Map<String, String> realmRealmMap = new HashMap<String, String>(names.size());
            for (String s : names) {
                realmRealmMap.put(s, realmMapList.require(s).asString());
            }

            String delegateRealmMapper = DELEGATE_REALM_MAPPER.resolveModelAttribute(context, model).asStringOrNull();

            final InjectedValue<RealmMapper> delegateRealmMapperInjector = new InjectedValue<RealmMapper>();

            TrivialService<RealmMapper> realmMapperService = new TrivialService<RealmMapper>(() -> {
                RealmMapper delegate = delegateRealmMapperInjector.getOptionalValue();
                Pattern compiledPattern = Pattern.compile(pattern);
                if (delegate == null) {
                    return new MappedRegexRealmMapper(compiledPattern, realmRealmMap);
                } else {
                    return new MappedRegexRealmMapper(compiledPattern, delegate, realmRealmMap);
                }
            });

            ServiceBuilder<RealmMapper> realmMapperBuilder = serviceTarget.addService(realmMapperName, realmMapperService);

            if (delegateRealmMapper != null) {
                String delegateCapabilityName = RuntimeCapability.buildDynamicCapabilityName(REALM_MAPPER_CAPABILITY, delegateRealmMapper);
                ServiceName delegateServiceName = context.getCapabilityServiceName(delegateCapabilityName, RealmMapper.class);

                realmMapperBuilder.addDependency(delegateServiceName, RealmMapper.class, delegateRealmMapperInjector);
            }

            commonDependencies(realmMapperBuilder)
                .setInitialMode(Mode.LAZY)
                .install();
        }

    }

}
