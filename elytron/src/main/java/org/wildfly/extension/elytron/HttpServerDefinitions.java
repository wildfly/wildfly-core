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
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronCommonConstants.FILTER;
import static org.wildfly.extension.elytron.ElytronCommonConstants.FILTERS;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;
import static org.wildfly.security.provider.util.ProviderUtil.findProviderService;

import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
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
import org.wildfly.security.http.util.SocketAddressCallbackServerMechanismFactory;

/**
 * Resource definitions for loading and configuring the HTTP server side authentication mechanisms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class HttpServerDefinitions {

    static final SimpleAttributeDefinition HTTP_SERVER_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.HTTP_SERVER_MECHANISM_FACTORY, ModelType.STRING, false)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PROVIDERS, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PATTERN_FILTER = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.PATTERN_FILTER, RegexAttributeDefinitions.PATTERN)
        .setXmlName(ElytronCommonConstants.PATTERN)
        .build();

    static final SimpleAttributeDefinition ENABLING = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ENABLING, ModelType.BOOLEAN)
        .setRequired(false)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.TRUE)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final ObjectTypeAttributeDefinition CONFIGURED_FILTER = new ObjectTypeAttributeDefinition.Builder(FILTER, PATTERN_FILTER, ENABLING)
            .setXmlName(FILTER)
            .build();

    static final ObjectListAttributeDefinition CONFIGURED_FILTERS = new ObjectListAttributeDefinition.Builder(ElytronCommonConstants.FILTERS, CONFIGURED_FILTER)
            .setRequired(false)
            .setRestartAllServices()
            .setXmlName(FILTERS)
            .build();

    private static final ElytronCommonAggregateComponentDefinition<HttpServerAuthenticationMechanismFactory> AGGREGATE_HTTP_SERVER_FACTORY = ElytronCommonAggregateComponentDefinition.create(HttpServerAuthenticationMechanismFactory.class,
            ElytronCommonConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY, ElytronCommonConstants.HTTP_SERVER_MECHANISM_FACTORIES, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY,
            AggregateServerMechanismFactory::new);

    static ElytronCommonAggregateComponentDefinition<HttpServerAuthenticationMechanismFactory> getRawAggregateHttpServerFactoryDefinition() {
        return AGGREGATE_HTTP_SERVER_FACTORY;
    }

    static ResourceDefinition getAggregateHttpServerFactoryDefinition() {
        return wrapFactory(AGGREGATE_HTTP_SERVER_FACTORY);
    }

    static ResourceDefinition getConfigurableHttpServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { HTTP_SERVER_FACTORY, CONFIGURED_FILTERS, PROPERTIES };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

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
                if (model.hasDefined(ElytronCommonConstants.FILTERS)) {
                    Predicate<String> filter = null;
                    List<ModelNode> nodes = model.require(ElytronCommonConstants.FILTERS).asList();
                    for (ModelNode current : nodes) {
                        Predicate<String> currentFilter = (String s) -> true;
                        String patternFilter = PATTERN_FILTER.resolveModelAttribute(context, current).asStringOrNull();
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

                final Map<String, String> propertiesMap = PROPERTIES.unwrap(context, model);
                return () -> {
                    HttpServerAuthenticationMechanismFactory factory = factoryInjector.getValue();
                    factory = new SocketAddressCallbackServerMechanismFactory(factory);
                    factory = new SetMechanismInformationMechanismFactory(factory);
                    factory = finalFilter != null ? new FilterServerMechanismFactory(factory, finalFilter) : factory;
                    factory = propertiesMap != null ? new PropertiesServerMechanismFactory(factory, propertiesMap) : factory;

                    return factory;
                };
            }
        };

        return wrapFactory(new ElytronCommonTrivialResourceDefinition(ElytronCommonConstants.CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY,
                add, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    static ResourceDefinition getProviderHttpServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { PROVIDERS };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {

                String providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
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

                Predicate<Provider.Service> serviceFilter = (Provider.Service s) -> HttpServerAuthenticationMechanismFactory.class.getSimpleName().equals(s.getType());

                return () -> {
                    final Provider[] actualProviders = providerSupplier.get();
                    if ( findProviderService(actualProviders, serviceFilter) == null ) {
                        throw ROOT_LOGGER.noSuitableProvider(HttpServerAuthenticationMechanismFactory.class.getSimpleName());
                    }
                    return new SocketAddressCallbackServerMechanismFactory(new SetMechanismInformationMechanismFactory(new SecurityProviderServerMechanismFactory(actualProviders)));
                };
            }

        };

        return wrapFactory(new ElytronCommonTrivialResourceDefinition(ElytronCommonConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY, add,
                attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    static ResourceDefinition getServiceLoaderServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { MODULE };
        AbstractAddStepHandler add = new ElytronCommonTrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, ServiceController.Mode.ACTIVE, ServiceController.Mode.PASSIVE, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {
                final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();

                return () -> {
                    try {
                        ClassLoader classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));

                        return new SocketAddressCallbackServerMechanismFactory(new SetMechanismInformationMechanismFactory(new ServiceLoaderServerMechanismFactory(classLoader)));
                    } catch (Exception e) {
                        throw new StartException(e);
                    }
                };

            }
        };

        return wrapFactory(new ElytronCommonTrivialResourceDefinition(ElytronCommonConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY,
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
