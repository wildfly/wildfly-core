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

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.wildfly.extension.elytron.Capabilities.HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ProviderUtil.isServiceTypeProvided;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.util.AggregateServerMechanismFactory;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.http.util.PropertiesServerMechanismFactory;
import org.wildfly.security.http.util.SecurityProviderServerMechanismFactory;
import org.wildfly.security.http.util.ServiceLoaderServerMechanismFactory;
import org.wildfly.security.http.util.SetMechanismInformationMechanismFactory;

/**
 * Resource definitions for loading and configuring the HTTP server side authentication mechanisms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class HttpServerDefinitions {

    static final SimpleAttributeDefinition HTTP_SERVER_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY, ModelType.STRING, false)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(PROVIDERS_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, true)
        .build();

    static final SimpleAttributeDefinition PATTERN_FILTER = new SimpleAttributeDefinitionBuilder(RegexAttributeDefinitions.PATTERN)
        .setXmlName(VALUE)
        .setName(ElytronDescriptionConstants.PATTERN_FILTER)
        .build();

    static final SimpleAttributeDefinition ENABLING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENABLING, ModelType.BOOLEAN)
        .setRequired(false)
        .setAllowExpression(true)
        .setDefaultValue(new ModelNode(true))
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final ObjectTypeAttributeDefinition CONFIGURED_FILTER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.FILTER, PATTERN_FILTER, ENABLING)
        .build();

    static final ObjectListAttributeDefinition CONFIGURED_FILTERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.FILTERS, CONFIGURED_FILTER)
        .setRequired(false)
        .build();

    private static final AggregateComponentDefinition<HttpServerAuthenticationMechanismFactory> AGGREGATE_HTTP_SERVER_FACTORY = AggregateComponentDefinition.create(HttpServerAuthenticationMechanismFactory.class,
            ElytronDescriptionConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY, ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORIES, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY,
            (HttpServerAuthenticationMechanismFactory[] n) -> new AggregateServerMechanismFactory(n));

    static AggregateComponentDefinition<HttpServerAuthenticationMechanismFactory> getRawAggregateHttpServerFactoryDefinition() {
        return AGGREGATE_HTTP_SERVER_FACTORY;
    }

    static ResourceDefinition getAggregateHttpServerFactoryDefinition() {
        return wrapFactory(AGGREGATE_HTTP_SERVER_FACTORY);
    }

    static ResourceDefinition getConfigurableHttpServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { HTTP_SERVER_FACTORY, CONFIGURED_FILTERS, PROPERTIES };
        AbstractAddStepHandler add = new TrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {

                final InjectedValue<HttpServerAuthenticationMechanismFactory> factoryInjector = new InjectedValue<HttpServerAuthenticationMechanismFactory>();

                String httpServerFactory = HTTP_SERVER_FACTORY.resolveModelAttribute(context, model).asString();
                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, httpServerFactory), HttpServerAuthenticationMechanismFactory.class),
                        HttpServerAuthenticationMechanismFactory.class, factoryInjector);

                final Predicate<String> finalFilter;
                if (model.hasDefined(ElytronDescriptionConstants.FILTERS)) {
                    Predicate<String> filter = null;
                    List<ModelNode> nodes = model.require(ElytronDescriptionConstants.FILTERS).asList();
                    for (ModelNode current : nodes) {
                        Predicate<String> currentFilter = (String s) -> true;
                        String patternFilter = asStringIfDefined(context, PATTERN_FILTER, current);
                        if (patternFilter != null) {
                            final Pattern pattern = Pattern.compile(patternFilter);
                            currentFilter = (String s) -> pattern.matcher(s).find();
                        }

                        currentFilter = ENABLING.resolveModelAttribute(context, current).asBoolean() ? currentFilter : currentFilter.negate();
                        filter = filter == null ? currentFilter : filter.or(currentFilter);
                    }
                    finalFilter = filter;
                } else {
                    finalFilter = null;
                }

                final Map<String, String> propertiesMap;
                final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
                if (properties.isDefined()) {
                    propertiesMap = new HashMap<String, String>();
                    properties.keys().forEach((String s) -> propertiesMap.put(s, properties.require(s).asString()));
                } else {
                    propertiesMap = null;
                }

                return () -> {
                    HttpServerAuthenticationMechanismFactory factory = factoryInjector.getValue();
                    factory = new SetMechanismInformationMechanismFactory(factory);
                    factory = finalFilter != null ? new FilterServerMechanismFactory(factory, finalFilter) : factory;
                    factory = propertiesMap != null ? new PropertiesServerMechanismFactory(factoryInjector.getValue(), propertiesMap) : factory;

                    return factory;
                };
            }
        };

        return wrapFactory(new TrivialResourceDefinition(ElytronDescriptionConstants.CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY,
                add, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    static ResourceDefinition getProviderHttpServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { PROVIDERS };
        AbstractAddStepHandler add = new TrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {

                String providers = asStringIfDefined(context, PROVIDERS, model);
                final Supplier<Provider[]> providerSupplier;
                if (providers != null) {
                    final InjectedValue<Provider[]> providersInjector = new InjectedValue<Provider[]>();
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providers), Provider[].class),
                            Provider[].class, providersInjector);
                    providerSupplier = providersInjector::getValue;
                } else {
                    providerSupplier = Security::getProviders;
                }

                return () -> {
                    if ( ! isServiceTypeProvided(providerSupplier.get(), HttpServerAuthenticationMechanismFactory.class)) {
                        throw ROOT_LOGGER.noSuitableProvider(HttpServerAuthenticationMechanismFactory.class.getSimpleName());
                    }
                    return new SetMechanismInformationMechanismFactory(new SecurityProviderServerMechanismFactory(providerSupplier));
                };
            }

        };

        return wrapFactory(new TrivialResourceDefinition(ElytronDescriptionConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY, add,
                attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    static ResourceDefinition getServiceLoaderServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { MODULE };
        AbstractAddStepHandler add = new TrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {
                final String module = asStringIfDefined(context, MODULE, model);

                return () -> {
                    try {
                        ClassLoader classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));

                        return new SetMechanismInformationMechanismFactory(new ServiceLoaderServerMechanismFactory(classLoader));
                    } catch (Exception e) {
                        throw new StartException(e);
                    }
                };

            }
        };

        return wrapFactory(new TrivialResourceDefinition(ElytronDescriptionConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY,
                add, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    private static ResourceDefinition wrapFactory(ResourceDefinition resourceDefinition) {
        return AvailableMechanismsRuntimeResource.wrap(
                resourceDefinition,
                (context) -> {
                    RuntimeCapability<Void> runtimeCapability = HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
                    ServiceName httpServerFactoryName = runtimeCapability.getCapabilityServiceName(HttpServerAuthenticationMechanismFactory.class);

                    ServiceRegistry registry = context.getServiceRegistry(false);
                    ServiceController<HttpServerAuthenticationMechanismFactory> serviceContainer = getRequiredService(registry, httpServerFactoryName, HttpServerAuthenticationMechanismFactory.class);
                    if (serviceContainer.getState() != State.UP) {
                        return null;
                    }
                    return serviceContainer.getValue().getMechanismNames(Collections.emptyMap());
                });
    }

}
