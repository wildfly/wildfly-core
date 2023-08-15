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

import static org.wildfly.extension.elytron.AvailableMechanismsRuntimeResource.wrap;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_SERVER_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_SERVER_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.common.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.common.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERS;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.common.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.security.sasl.SaslServerFactory;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.common.ElytronReloadRequiredWriteAttributeHandler;
import org.wildfly.extension.elytron.common.TrivialService;
import org.wildfly.extension.elytron.common.TrivialService.ValueSupplier;
import org.wildfly.security.sasl.util.AggregateSaslServerFactory;
import org.wildfly.security.sasl.util.FilterMechanismSaslServerFactory;
import org.wildfly.security.sasl.util.MechanismProviderFilteringSaslServerFactory;
import org.wildfly.security.sasl.util.PropertiesSaslServerFactory;
import org.wildfly.security.sasl.util.ProtocolSaslServerFactory;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.SecurityProviderSaslServerFactory;
import org.wildfly.security.sasl.util.ServerNameSaslServerFactory;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.wildfly.security.sasl.util.SetMechanismInformationSaslServerFactory;

/**
 * The {@link ResourceDefinition} instances for the {@link SaslServerFactory} resources.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SaslServerDefinitions {

    static final SimpleAttributeDefinition SERVER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SERVER_NAME, ModelType.STRING, true)
        .setMinSize(1)
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROTOCOL, ModelType.STRING, true)
        .setMinSize(1)
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, false)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SASL_AUTHENTICATION_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition SASL_SERVER_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SASL_SERVER_FACTORY, ModelType.STRING, false)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(SASL_SERVER_FACTORY_CAPABILITY, SASL_SERVER_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, SASL_SERVER_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition ENABLING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENABLING, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.TRUE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition MECHANISM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MECHANISM_NAME, ModelType.STRING, true)
        .setAllowExpression(true)
        .build();

    static final SimpleAttributeDefinition PROVIDER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_NAME, ModelType.STRING, false)
        .setAllowExpression(true)
        .build();

    static final SimpleAttributeDefinition PROVIDER_VERSION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_VERSION, ModelType.DOUBLE, true)
        .setAllowExpression(true)
        .build();

    static final SimpleAttributeDefinition VERSION_COMPARISON = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERSION_COMPARISON, ModelType.STRING, false)
        .setRequired(false)
        .setAllowExpression(true)
        .setDefaultValue(new ModelNode(ElytronDescriptionConstants.LESS_THAN))
        .setRequires(ElytronDescriptionConstants.PROVIDER_VERSION)
        .setAllowedValues(ElytronDescriptionConstants.LESS_THAN, ElytronDescriptionConstants.GREATER_THAN)
        .setValidator(EnumValidator.create(Comparison.class))
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final ObjectTypeAttributeDefinition MECH_PROVIDER_FILTER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.FILTER, MECHANISM_NAME, PROVIDER_NAME, PROVIDER_VERSION, VERSION_COMPARISON)
        .setRequired(false)
        .setXmlName(FILTER)
        .build();

    static final ObjectListAttributeDefinition MECH_PROVIDER_FILTERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.FILTERS, MECH_PROVIDER_FILTER)
        .setMinSize(1)
        .setRequired(false)
        .setRestartAllServices()
        .setXmlName(FILTERS)
        .build();

    static final SimpleAttributeDefinition PREDEFINED_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PREDEFINED_FILTER, ModelType.STRING, false)
        .setAllowExpression(true)
        .setXmlName("predefined")
        .setAllowedValues(NamePredicate.names())
        .setValidator(EnumValidator.create(NamePredicate.class))
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setAlternatives(ElytronDescriptionConstants.PATTERN_FILTER)
        .build();

    static final SimpleAttributeDefinition PATTERN_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATTERN_FILTER, RegexAttributeDefinitions.PATTERN)
        .setAlternatives(ElytronDescriptionConstants.PREDEFINED_FILTER)
        .setXmlName(ElytronDescriptionConstants.PATTERN)
        .build();

    static final ObjectTypeAttributeDefinition CONFIGURED_FILTER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.FILTER, PREDEFINED_FILTER, PATTERN_FILTER, ENABLING)
            .setXmlName(FILTER)
            .build();

    static final ObjectListAttributeDefinition CONFIGURED_FILTERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.FILTERS, CONFIGURED_FILTER)
        .setRequired(false)
        .setValidator(new FiltersValidator())
        .setRestartAllServices()
        .build();

    private static class FiltersValidator extends ObjectTypeValidator {

        private FiltersValidator() {
            super(true, PREDEFINED_FILTER, PATTERN_FILTER, ENABLING);
        }

        @Override
        public void validateParameter(final String parameterName, final ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            if (value.hasDefined(ElytronDescriptionConstants.PREDEFINED_FILTER)
                    && value.hasDefined(ElytronDescriptionConstants.PATTERN_FILTER)) {
                throw ROOT_LOGGER.invalidDefinition(ElytronDescriptionConstants.FILTERS, ElytronDescriptionConstants.PREDEFINED_FILTER, ElytronDescriptionConstants.PATTERN_FILTER);
            }
        }
    }

    private static final AggregateComponentDefinition<SaslServerFactory> AGGREGATE_SASL_SERVER_FACTORY = AggregateComponentDefinition.create(SaslServerFactory.class,
            ElytronDescriptionConstants.AGGREGATE_SASL_SERVER_FACTORY, ElytronDescriptionConstants.SASL_SERVER_FACTORIES, SASL_SERVER_FACTORY_RUNTIME_CAPABILITY,
            AggregateSaslServerFactory::new);



    static AggregateComponentDefinition<SaslServerFactory> getRawAggregateSaslServerFactoryDefinition() {
        return AGGREGATE_SASL_SERVER_FACTORY;
    }

    static ResourceDefinition getAggregateSaslServerFactoryDefinition() {
        return wrap(AGGREGATE_SASL_SERVER_FACTORY, SaslServerDefinitions::getSaslServerAvailableMechanisms);
    }

    static ResourceDefinition getConfigurableSaslServerFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { SASL_SERVER_FACTORY, SERVER_NAME, PROTOCOL, PROPERTIES, CONFIGURED_FILTERS };
        AbstractAddStepHandler add = new SaslServerAddHandler(attributes) {

            @Override
            protected ServiceBuilder<SaslServerFactory> installService(OperationContext context,
                    ServiceName saslServerFactoryName, ModelNode model) throws OperationFailedException {

                final String saslServerFactory = SASL_SERVER_FACTORY.resolveModelAttribute(context, model).asString();
                final String protocol = PROTOCOL.resolveModelAttribute(context, model).asStringOrNull();
                final String serverName = SERVER_NAME.resolveModelAttribute(context, model).asStringOrNull();

                final Map<String, String> propertiesMap;
                ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
                if (properties.isDefined()) {
                    propertiesMap = new HashMap<String, String>();
                    for (String s : properties.keys()) {
                        propertiesMap.put(s, properties.require(s).asString());
                    }
                } else {
                    propertiesMap = null;
                }

                final Predicate<String> finalFilter;
                if (model.hasDefined(ElytronDescriptionConstants.FILTERS)) {
                    Predicate<String> filter = null;
                    List<ModelNode> nodes = model.require(ElytronDescriptionConstants.FILTERS).asList();
                    for (ModelNode current : nodes) {
                        Predicate<String> currentFilter = (String s) -> true;
                        String predefinedFilter = PREDEFINED_FILTER.resolveModelAttribute(context, current).asStringOrNull();
                        if (predefinedFilter != null) {
                            currentFilter = NamePredicate.valueOf(predefinedFilter).predicate;
                        } else {
                            String patternFilter = PATTERN_FILTER.resolveModelAttribute(context, current).asStringOrNull();
                            if (patternFilter != null) {
                                final Pattern pattern = Pattern.compile(patternFilter);
                                currentFilter = (String s) ->  pattern.matcher(s).find();
                            }
                        }

                        currentFilter = ENABLING.resolveModelAttribute(context, current).asBoolean() ? currentFilter : currentFilter.negate();
                        filter = filter == null ? currentFilter : filter.or(currentFilter);
                    }
                    finalFilter = filter;
                } else {
                    finalFilter = null;
                }


                final InjectedValue<SaslServerFactory> saslServerFactoryInjector = new InjectedValue<SaslServerFactory>();

                TrivialService<SaslServerFactory> saslServiceFactoryService = new TrivialService<SaslServerFactory>(() -> {
                    SaslServerFactory theFactory = saslServerFactoryInjector.getValue();
                    theFactory = new SetMechanismInformationSaslServerFactory(theFactory);
                    theFactory = protocol != null ? new ProtocolSaslServerFactory(theFactory, protocol) : theFactory;
                    theFactory = serverName != null ? new ServerNameSaslServerFactory(theFactory, serverName) : theFactory;
                    theFactory = propertiesMap != null ? new PropertiesSaslServerFactory(theFactory, propertiesMap) : theFactory;
                    theFactory = finalFilter != null ? new FilterMechanismSaslServerFactory(theFactory, finalFilter) : theFactory;
                    return theFactory;
                });

                ServiceTarget serviceTarget = context.getServiceTarget();

                ServiceBuilder<SaslServerFactory> serviceBuilder = serviceTarget.addService(saslServerFactoryName, saslServiceFactoryService);

                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        RuntimeCapability.buildDynamicCapabilityName(SASL_SERVER_FACTORY_CAPABILITY, saslServerFactory),
                        SaslServerFactory.class), SaslServerFactory.class, saslServerFactoryInjector);

                return serviceBuilder;
            }

        };

        return wrap(new SaslServerResourceDefinition(ElytronDescriptionConstants.CONFIGURABLE_SASL_SERVER_FACTORY, add, attributes), SaslServerDefinitions::getSaslServerAvailableMechanisms);
    }

    static ResourceDefinition getProviderSaslServerFactoryDefinition() {
        AbstractAddStepHandler add = new SaslServerAddHandler(PROVIDERS) {

            @Override
            protected ServiceBuilder<SaslServerFactory> installService(OperationContext context,
                    ServiceName saslServerFactoryName, ModelNode model) throws OperationFailedException {

                String providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();

                final InjectedValue<Provider[]> providerInjector = new InjectedValue<Provider[]>();
                final Supplier<Provider[]> providerSupplier = providers != null ? (providerInjector::getValue) : (Security::getProviders);

                TrivialService<SaslServerFactory> saslServiceFactoryService = new TrivialService<SaslServerFactory>(() -> new SecurityProviderSaslServerFactory(providerSupplier));

                ServiceTarget serviceTarget = context.getServiceTarget();

                ServiceBuilder<SaslServerFactory> serviceBuilder = serviceTarget.addService(saslServerFactoryName, saslServiceFactoryService);

                if (providers != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(RuntimeCapability.buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providers),
                            Provider[].class), Provider[].class, providerInjector);
                }

                return serviceBuilder;
            }
        };

        return wrap(new SaslServerResourceDefinition(ElytronDescriptionConstants.PROVIDER_SASL_SERVER_FACTORY, add, PROVIDERS), SaslServerDefinitions::getSaslServerAvailableMechanisms);
    }

    static ResourceDefinition getServiceLoaderSaslServerFactoryDefinition() {
        AbstractAddStepHandler add = new SaslServerAddHandler(MODULE) {

            @Override
            protected ValueSupplier<SaslServerFactory> getValueSupplier(OperationContext context, ModelNode model)
                    throws OperationFailedException {

                final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();

                return () -> getSaslServerFactory(module);
            }

            private SaslServerFactory getSaslServerFactory(final String module) throws StartException {
                try {
                    ClassLoader classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));

                    return new ServiceLoaderSaslServerFactory(classLoader);
                } catch (Exception e) {
                    throw new StartException(e);
                }
            }
        };

        return wrap(new SaslServerResourceDefinition(ElytronDescriptionConstants.SERVICE_LOADER_SASL_SERVER_FACTORY, add, MODULE), SaslServerDefinitions::getSaslServerAvailableMechanisms);
    }

    static ResourceDefinition getMechanismProviderFilteringSaslServerFactory() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { SASL_SERVER_FACTORY, ENABLING, MECH_PROVIDER_FILTERS };
        AbstractAddStepHandler add = new SaslServerAddHandler(attributes) {

            @Override
            protected ServiceBuilder<SaslServerFactory> installService(OperationContext context,
                    ServiceName saslServerFactoryName, ModelNode model) throws OperationFailedException {

                final String saslServerFactory = SASL_SERVER_FACTORY.resolveModelAttribute(context, model).asString();

                BiPredicate<String, Provider> predicate = null;

                if (model.hasDefined(ElytronDescriptionConstants.FILTERS)) {
                    List<ModelNode> nodes = model.require(ElytronDescriptionConstants.FILTERS).asList();
                    for (ModelNode current : nodes) {
                        final String mechanismName = MECHANISM_NAME.resolveModelAttribute(context, current).asStringOrNull();
                        final String providerName = PROVIDER_NAME.resolveModelAttribute(context, current).asString();
                        final Double providerVersion = PROVIDER_VERSION.resolveModelAttribute(context, current).asDoubleOrNull();

                        final Predicate<Double> versionPredicate;
                        if (providerVersion != null) {
                            final Comparison comparison = Comparison
                                    .getComparison(VERSION_COMPARISON.resolveModelAttribute(context, current).asString());

                            versionPredicate = (Double d) -> comparison.getPredicate().test(d, providerVersion);
                        } else {
                            versionPredicate = null;
                        }

                        BiPredicate<String, Provider> thisPredicate = (String s, Provider p) -> {
                            return (mechanismName == null || mechanismName.equals(s)) && providerName.equals(p.getName())
                                    && (providerVersion == null || versionPredicate.test(p.getVersion()));
                        };

                        predicate = predicate == null ? thisPredicate : predicate.or(thisPredicate);
                    }

                    boolean enabling = ENABLING.resolveModelAttribute(context, model).asBoolean();
                    if (enabling == false) {
                        predicate = predicate != null ? predicate.negate() : null;
                    }
                }

                final BiPredicate<String, Provider> finalPredicate = predicate;
                final InjectedValue<SaslServerFactory> saslServerFactoryInjector = new InjectedValue<SaslServerFactory>();

                TrivialService<SaslServerFactory> saslServiceFactoryService = new TrivialService<SaslServerFactory>(() -> {
                    SaslServerFactory theFactory = saslServerFactoryInjector.getValue();
                    theFactory = finalPredicate != null ? new MechanismProviderFilteringSaslServerFactory(theFactory, finalPredicate) : theFactory;

                    return theFactory;
                });

                ServiceTarget serviceTarget = context.getServiceTarget();

                ServiceBuilder<SaslServerFactory> serviceBuilder = serviceTarget.addService(saslServerFactoryName, saslServiceFactoryService);

                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        RuntimeCapability.buildDynamicCapabilityName(SASL_SERVER_FACTORY_CAPABILITY, saslServerFactory),
                        SaslServerFactory.class), SaslServerFactory.class, saslServerFactoryInjector);

                return serviceBuilder;
            }

        };

        return wrap(new SaslServerResourceDefinition(ElytronDescriptionConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY, add, attributes), SaslServerDefinitions::getSaslServerAvailableMechanisms);
    }

    private static String[] getSaslServerAvailableMechanisms(OperationContext context) {
        RuntimeCapability<Void> runtimeCapability = SASL_SERVER_FACTORY_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
        ServiceName saslServerFactoryName = runtimeCapability.getCapabilityServiceName(SaslServerFactory.class);

        ServiceController<SaslServerFactory> serviceContainer = getRequiredService(context.getServiceRegistry(false), saslServerFactoryName, SaslServerFactory.class);
        if (serviceContainer.getState() != State.UP) {
            return null;
        }
        return serviceContainer.getValue().getMechanismNames(Collections.emptyMap());
    }

    private static class SaslServerResourceDefinition extends SimpleResourceDefinition {

        private final String pathKey;
        private final AttributeDefinition[] attributes;

        SaslServerResourceDefinition(String pathKey, AbstractAddStepHandler add, AttributeDefinition ... attributes) {
            super(new Parameters(PathElement.pathElement(pathKey),
                    ElytronExtension.getResourceDescriptionResolver(pathKey))
                .setAddHandler(add)
                .setRemoveHandler(new TrivialCapabilityServiceRemoveHandler(add, SASL_SERVER_FACTORY_RUNTIME_CAPABILITY))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(SASL_SERVER_FACTORY_RUNTIME_CAPABILITY));
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

    private static class SaslServerAddHandler extends BaseAddHandler {

        private SaslServerAddHandler(AttributeDefinition ... attributes) {
            super(SASL_SERVER_FACTORY_RUNTIME_CAPABILITY, attributes);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            RuntimeCapability<Void> runtimeCapability = SASL_SERVER_FACTORY_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName saslServerFactoryName = runtimeCapability.getCapabilityServiceName(SaslServerFactory.class);

            commonDependencies(installService(context, saslServerFactoryName, model))
                .setInitialMode(Mode.ACTIVE)
                .install();
        }

        protected ServiceBuilder<SaslServerFactory> installService(OperationContext context, ServiceName saslServerFactoryName, ModelNode model) throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            TrivialService<SaslServerFactory> saslServerFactoryService = new TrivialService<SaslServerFactory>(getValueSupplier(context, model));

            return serviceTarget.addService(saslServerFactoryName, saslServerFactoryService);
        }

        protected ValueSupplier<SaslServerFactory> getValueSupplier(OperationContext context, ModelNode model) throws OperationFailedException {
            return () -> null;
        }

    }

    private enum NamePredicate {

        HASH_MD5(SaslMechanismInformation.HASH_MD5),
        HASH_SHA(SaslMechanismInformation.HASH_SHA),
        HASH_SHA_256(SaslMechanismInformation.HASH_SHA_256),
        HASH_SHA_384(SaslMechanismInformation.HASH_SHA_384),
        HASH_SHA_512(SaslMechanismInformation.HASH_SHA_512),
        GS2(SaslMechanismInformation.GS2),
        SCRAM(SaslMechanismInformation.SCRAM),
        DIGEST(SaslMechanismInformation.DIGEST),
        IEC_ISO_9798(SaslMechanismInformation.IEC_ISO_9798),
        EAP(SaslMechanismInformation.EAP),
        MUTUAL(SaslMechanismInformation.MUTUAL),
        BINDING(SaslMechanismInformation.BINDING),
        RECOMMENDED(SaslMechanismInformation.RECOMMENDED);

        private final Predicate<String> predicate;

        NamePredicate(Predicate<String> predicate) {
            this.predicate = predicate;
        }

        static String[] names() {
            NamePredicate[] namePredicates = NamePredicate.values();
            String[] names = new String[namePredicates.length];
            for (int i = 0; i < namePredicates.length; i++) {
                names[i] = namePredicates[i].toString();
            }

            return names;
        }
    }

    private enum Comparison {

        LESS_THAN(ElytronDescriptionConstants.LESS_THAN, (Double left, Double right) ->  left < right),

        GREATER_THAN(ElytronDescriptionConstants.GREATER_THAN, (Double left, Double right) ->  left > right);

        private final String name;

        private final BiPredicate<Double, Double> predicate;

        Comparison(final String name, final BiPredicate<Double, Double> predicate) {
            this.name = name;
            this.predicate = predicate;
        }

        BiPredicate<Double, Double> getPredicate() {
            return predicate;
        }

        @Override
        public String toString() {
            return name;
        }


        static Comparison getComparison(String value) {
            switch (value) {
                case ElytronDescriptionConstants.LESS_THAN:
                    return LESS_THAN;
                case ElytronDescriptionConstants.GREATER_THAN:
                    return GREATER_THAN;
            }

            throw new IllegalArgumentException("Invalid value");
        }
    }
}
