/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.wildfly.extension.elytron.Capabilities.HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.CommonAttributes.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERS;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;
import static org.wildfly.security.provider.util.ProviderUtil.findProviderService;

import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import org.wildfly.security.http.HttpServerRequest;
import org.wildfly.security.http.util.AggregateServerMechanismFactory;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.http.util.PropertiesServerMechanismFactory;
import org.wildfly.security.http.util.SecurityProviderServerMechanismFactory;
import org.wildfly.security.http.util.ServiceLoaderServerMechanismFactory;
import org.wildfly.security.http.util.SetMechanismInformationMechanismFactory;
import org.wildfly.security.http.util.SetRequestInformationCallbackMechanismFactory;
import org.wildfly.security.http.util.SocketAddressCallbackServerMechanismFactory;

/**
 * Resource definitions for loading and configuring the HTTP server side authentication mechanisms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class HttpServerDefinitions {

    static final SimpleAttributeDefinition HTTP_SERVER_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY, ModelType.STRING, false)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY)
        .build();

    static final SimpleAttributeDefinition PATTERN_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATTERN_FILTER, RegexAttributeDefinitions.PATTERN)
        .setXmlName(ElytronDescriptionConstants.PATTERN)
        .build();

    static final SimpleAttributeDefinition ENABLING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENABLING, ModelType.BOOLEAN)
        .setRequired(false)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.TRUE)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final ObjectTypeAttributeDefinition CONFIGURED_FILTER = new ObjectTypeAttributeDefinition.Builder(FILTER, PATTERN_FILTER, ENABLING)
            .setXmlName(FILTER)
            .build();

    static final ObjectListAttributeDefinition CONFIGURED_FILTERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.FILTERS, CONFIGURED_FILTER)
            .setRequired(false)
            .setRestartAllServices()
            .setXmlName(FILTERS)
            .build();

    private static final AggregateComponentDefinition<HttpServerAuthenticationMechanismFactory> AGGREGATE_HTTP_SERVER_FACTORY = AggregateComponentDefinition.create(HttpServerAuthenticationMechanismFactory.class,
            ElytronDescriptionConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY, ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORIES, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY,
            AggregateServerMechanismFactory::new);

    /**
     * URI of the HTTP authentication request
     */
    private static final String REQUEST_URI = "Request-URI";

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
                    factory = new SetRequestInformationCallbackMechanismFactory(factory, getRequestInformationHashMap());
                    factory = new SocketAddressCallbackServerMechanismFactory(factory);
                    factory = new SetMechanismInformationMechanismFactory(factory);
                    factory = finalFilter != null ? new FilterServerMechanismFactory(factory, finalFilter) : factory;
                    factory = propertiesMap != null ? new PropertiesServerMechanismFactory(factory, propertiesMap) : factory;

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
                    return new SetRequestInformationCallbackMechanismFactory(new SocketAddressCallbackServerMechanismFactory(new SetMechanismInformationMechanismFactory(new SecurityProviderServerMechanismFactory(actualProviders))), getRequestInformationHashMap());
                };
            }

        };

        return wrapFactory(new TrivialResourceDefinition(ElytronDescriptionConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY, add,
                attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    static ResourceDefinition getServiceLoaderServerMechanismFactoryDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { MODULE };
        AbstractAddStepHandler add = new TrivialAddHandler<HttpServerAuthenticationMechanismFactory>(HttpServerAuthenticationMechanismFactory.class, ServiceController.Mode.ACTIVE, ServiceController.Mode.PASSIVE, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpServerAuthenticationMechanismFactory> getValueSupplier(
                    ServiceBuilder<HttpServerAuthenticationMechanismFactory> serviceBuilder, OperationContext context,
                    ModelNode model) throws OperationFailedException {
                final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();

                return () -> {
                    try {
                        ClassLoader classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));

                        return new SetRequestInformationCallbackMechanismFactory(new SocketAddressCallbackServerMechanismFactory(new SetMechanismInformationMechanismFactory(new ServiceLoaderServerMechanismFactory(classLoader))), getRequestInformationHashMap());
                    } catch (Exception e) {
                        throw new StartException(e);
                    }
                };

            }
        };

        return wrapFactory(new TrivialResourceDefinition(ElytronDescriptionConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY,
                add, attributes, HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY));
    }

    private static HashMap<String, Function<HttpServerRequest, String>> getRequestInformationHashMap() {
        HashMap<String, Function<HttpServerRequest, String>> requestInformation = new HashMap<>();
        requestInformation.put(REQUEST_URI, (HttpServerRequest httpServerRequest) -> httpServerRequest.getRequestURI().toString());
        return requestInformation;
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
