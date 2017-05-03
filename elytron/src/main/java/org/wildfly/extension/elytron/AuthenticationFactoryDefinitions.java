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
import static org.wildfly.extension.elytron.AvailableMechanismsRuntimeResource.wrap;
import static org.wildfly.extension.elytron.Capabilities.HTTP_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.HTTP_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PRINCIPAL_TRANSFORMER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.REALM_MAPPER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_SERVER_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.security.sasl.SaslServerFactory;

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
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.MechanismAuthenticationFactory;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.MechanismInformation;
import org.wildfly.security.auth.server.MechanismRealmConfiguration;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.sasl.util.FilterMechanismSaslServerFactory;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.SortedMechanismSaslServerFactory;


/**
 * The {@link ResourceDefinition} instances for the authentication factory definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationFactoryDefinitions {

    static final SimpleAttributeDefinition BASE_SECURITY_DOMAIN_REF = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECURITY_DOMAIN, ModelType.STRING, false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition HTTP_SERVER_MECHANISM_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY, ModelType.STRING, false)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, HTTP_AUTHENTICATION_FACTORY_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition SASL_SERVER_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SASL_SERVER_FACTORY, ModelType.STRING, false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(SASL_SERVER_FACTORY_CAPABILITY, SASL_AUTHENTICATION_FACTORY_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition MECHANISM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MECHANISM_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAttributeGroup(ElytronDescriptionConstants.SELECTION_CRITERIA)
            .build();

    static final SimpleAttributeDefinition HOST_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HOST_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAttributeGroup(ElytronDescriptionConstants.SELECTION_CRITERIA)
            .build();

    static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROTOCOL, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAttributeGroup(ElytronDescriptionConstants.SELECTION_CRITERIA)
            .build();

    static final SimpleAttributeDefinition BASE_CREDENTIAL_SECURITY_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREDENTIAL_SECURITY_FACTORY, ModelType.STRING, true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition BASE_PRE_REALM_PRINCIPAL_TRANSFORMER= new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition BASE_POST_REALM_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition BASE_FINAL_PRINCIPAL_TRANSFORMER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FINAL_PRINCIPAL_TRANSFORMER, ModelType.STRING, true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition BASE_REALM_MAPPER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_MAPPER, ModelType.STRING, true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM_NAME, ModelType.STRING, false)
            .setMinSize(1)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static AttributeDefinition getMechanismConfiguration(String forCapability) {
        SimpleAttributeDefinition preRealmPrincipalTransformerAttribute = new SimpleAttributeDefinitionBuilder(BASE_PRE_REALM_PRINCIPAL_TRANSFORMER)
                .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, forCapability, true)
                .build();
        SimpleAttributeDefinition postRealmPrincipalTransformerAttribute = new SimpleAttributeDefinitionBuilder(BASE_POST_REALM_PRINCIPAL_TRANSFORMER)
                .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, forCapability, true)
                .build();
        SimpleAttributeDefinition finalprincipalTransformerAttribute = new SimpleAttributeDefinitionBuilder(BASE_FINAL_PRINCIPAL_TRANSFORMER)
                .setCapabilityReference(PRINCIPAL_TRANSFORMER_CAPABILITY, forCapability, true)
                .build();
        SimpleAttributeDefinition realmMapperAttribute = new SimpleAttributeDefinitionBuilder(BASE_REALM_MAPPER)
                .setCapabilityReference(REALM_MAPPER_CAPABILITY, forCapability, true)
                .build();

        ObjectTypeAttributeDefinition mechanismRealmConfiguration = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATION, REALM_NAME, preRealmPrincipalTransformerAttribute, postRealmPrincipalTransformerAttribute, finalprincipalTransformerAttribute, realmMapperAttribute)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        ObjectListAttributeDefinition mechanismRealmConfigurations = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS, mechanismRealmConfiguration)
                .setAllowNull(true)
                .setAllowExpression(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        SimpleAttributeDefinition credentialSecurityFactoryAttribute = new SimpleAttributeDefinitionBuilder(BASE_CREDENTIAL_SECURITY_FACTORY)
                .setCapabilityReference(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, forCapability, true)
                .build();

        ObjectTypeAttributeDefinition mechanismConfiguration = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_CONFIGURATION, MECHANISM_NAME, HOST_NAME, PROTOCOL,
                preRealmPrincipalTransformerAttribute, postRealmPrincipalTransformerAttribute, finalprincipalTransformerAttribute, realmMapperAttribute, mechanismRealmConfigurations, credentialSecurityFactoryAttribute)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        return new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS, mechanismConfiguration)
                .setAllowNull(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();
    }

    static Set<String> getConfiguredMechanismNames(AttributeDefinition mechanismConfigurationAttribute, OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode mechanismConfiguration = mechanismConfigurationAttribute.resolveModelAttribute(context, model);
        if ( ! mechanismConfiguration.isDefined()) {
            return Collections.emptySet();
        }
        Set<String> mechanismNames = new HashSet<>();
        for (ModelNode current : mechanismConfiguration.asList()) {
            final String mechanismName = asStringIfDefined(context, MECHANISM_NAME, current);
            if (mechanismName == null) {
                return Collections.emptySet();
            }
            mechanismNames.add(mechanismName);
        }
        return mechanismNames;
    }

    static List<ResolvedMechanismConfiguration> getResolvedMechanismConfiguration(AttributeDefinition mechanismConfigurationAttribute, ServiceBuilder<?> serviceBuilder,
            OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode mechanismConfiguration = mechanismConfigurationAttribute.resolveModelAttribute(context, model);
        if (mechanismConfiguration.isDefined() == false) {
            return Collections.emptyList();
        }
        List<ModelNode> mechanismConfigurations = mechanismConfiguration.asList();
        List<ResolvedMechanismConfiguration> resolvedMechanismConfigurations = new ArrayList<>(mechanismConfigurations.size());
        for (ModelNode currentMechanismConfiguration : mechanismConfigurations) {
            final String mechanismName = asStringIfDefined(context, MECHANISM_NAME, currentMechanismConfiguration);
            final String hostName = asStringIfDefined(context, HOST_NAME, currentMechanismConfiguration);
            final String protocol = asStringIfDefined(context, PROTOCOL, currentMechanismConfiguration);

            Predicate<MechanismInformation> selectionPredicate = null;
            if (mechanismName != null) {
                selectionPredicate = i -> mechanismName.equalsIgnoreCase(i.getMechanismName());
            }
            if (hostName != null) {
                Predicate<MechanismInformation> hostPredicate = i -> hostName.equalsIgnoreCase(i.getHostName());
                selectionPredicate = selectionPredicate != null ? selectionPredicate.and(hostPredicate) : hostPredicate;
            }
            if (protocol != null) {
                Predicate<MechanismInformation> protocolPredicate = i -> protocol.equalsIgnoreCase(i.getProtocol());
                selectionPredicate = selectionPredicate != null ? selectionPredicate.and(protocolPredicate) : protocolPredicate;
            }

            if (selectionPredicate == null) {
                selectionPredicate = i -> true;
            }

            ResolvedMechanismConfiguration resolvedMechanismConfiguration = new ResolvedMechanismConfiguration(selectionPredicate);

            injectPrincipalTransformer(BASE_PRE_REALM_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismConfiguration, resolvedMechanismConfiguration.preRealmPrincipalTranformer);
            injectPrincipalTransformer(BASE_POST_REALM_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismConfiguration, resolvedMechanismConfiguration.postRealmPrincipalTransformer);
            injectPrincipalTransformer(BASE_FINAL_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismConfiguration, resolvedMechanismConfiguration.finalPrincipalTransformer);
            injectRealmMapper(BASE_REALM_MAPPER, serviceBuilder, context, currentMechanismConfiguration, resolvedMechanismConfiguration.realmMapper);
            injectSecurityFactory(BASE_CREDENTIAL_SECURITY_FACTORY, serviceBuilder, context, currentMechanismConfiguration, resolvedMechanismConfiguration.securityFactory);

            if (currentMechanismConfiguration.hasDefined(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS)) {
                for (ModelNode currentMechanismRealm : currentMechanismConfiguration.require(ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS).asList()) {
                    String realmName = REALM_NAME.resolveModelAttribute(context, currentMechanismRealm).asString();
                    ResolvedMechanismRealmConfiguration resolvedMechanismRealmConfiguration = new ResolvedMechanismRealmConfiguration();
                    injectPrincipalTransformer(BASE_PRE_REALM_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismRealm, resolvedMechanismRealmConfiguration.preRealmPrincipalTranformer);
                    injectPrincipalTransformer(BASE_POST_REALM_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismRealm, resolvedMechanismRealmConfiguration.postRealmPrincipalTransformer);
                    injectPrincipalTransformer(BASE_FINAL_PRINCIPAL_TRANSFORMER, serviceBuilder, context, currentMechanismRealm, resolvedMechanismRealmConfiguration.finalPrincipalTransformer);
                    injectRealmMapper(BASE_REALM_MAPPER, serviceBuilder, context, currentMechanismRealm, resolvedMechanismRealmConfiguration.realmMapper);
                    resolvedMechanismConfiguration.mechanismRealms.put(realmName, resolvedMechanismRealmConfiguration);
                }
            }

            resolvedMechanismConfigurations.add(resolvedMechanismConfiguration);
        }

        return resolvedMechanismConfigurations;
    }

    static void buildMechanismConfiguration(List<ResolvedMechanismConfiguration> resolvedMechanismConfigurations, MechanismAuthenticationFactory.Builder factoryBuilder) {
        ArrayList<MechanismConfigurationSelector> mechanismConfigurationSelectors = new ArrayList<>(resolvedMechanismConfigurations.size());
        for (ResolvedMechanismConfiguration resolvedMechanismConfiguration : resolvedMechanismConfigurations) {
            MechanismConfiguration.Builder builder = MechanismConfiguration.builder();

            setPrincipalTransformer(resolvedMechanismConfiguration.preRealmPrincipalTranformer, builder::setPreRealmRewriter);
            setPrincipalTransformer(resolvedMechanismConfiguration.postRealmPrincipalTransformer, builder::setPostRealmRewriter);
            setPrincipalTransformer(resolvedMechanismConfiguration.finalPrincipalTransformer, builder::setFinalRewriter);
            setRealmMapper(resolvedMechanismConfiguration.realmMapper, builder::setRealmMapper);
            setSecurityFactory(resolvedMechanismConfiguration.securityFactory, builder::setServerCredential);

            for (Entry<String, ResolvedMechanismRealmConfiguration> currentMechRealmEntry : resolvedMechanismConfiguration.mechanismRealms.entrySet()) {
                MechanismRealmConfiguration.Builder mechRealmBuilder = MechanismRealmConfiguration.builder();
                mechRealmBuilder.setRealmName(currentMechRealmEntry.getKey());
                ResolvedMechanismRealmConfiguration resolvedMechanismRealmConfiguration = currentMechRealmEntry.getValue();

                setPrincipalTransformer(resolvedMechanismRealmConfiguration.preRealmPrincipalTranformer, mechRealmBuilder::setPreRealmRewriter);
                setPrincipalTransformer(resolvedMechanismRealmConfiguration.postRealmPrincipalTransformer, mechRealmBuilder::setPostRealmRewriter);
                setPrincipalTransformer(resolvedMechanismRealmConfiguration.finalPrincipalTransformer, mechRealmBuilder::setFinalRewriter);
                setRealmMapper(resolvedMechanismRealmConfiguration.realmMapper, mechRealmBuilder::setRealmMapper);

                builder.addMechanismRealm(mechRealmBuilder.build());
            }

            mechanismConfigurationSelectors.add(MechanismConfigurationSelector.predicateSelector(resolvedMechanismConfiguration.selectionPredicate, builder.build()));
        }

        factoryBuilder.setMechanismConfigurationSelector(MechanismConfigurationSelector.aggregate(mechanismConfigurationSelectors.toArray(new MechanismConfigurationSelector[mechanismConfigurationSelectors.size()])));
    }

    private static void setPrincipalTransformer(InjectedValue<PrincipalTransformer> injectedValue, Consumer<Function<Principal, Principal>> principalTransformerConsumer) {
        PrincipalTransformer principalTransformer = injectedValue.getOptionalValue();
        if (principalTransformer != null) {
            principalTransformerConsumer.accept(principalTransformer);
        }
    }

    private static void injectPrincipalTransformer(SimpleAttributeDefinition principalTransformerAttribute, ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model, Injector<PrincipalTransformer> principalTransformer) throws OperationFailedException {
        String principalTransformerName = asStringIfDefined(context, principalTransformerAttribute, model);
        if (principalTransformerName != null) {
            serviceBuilder.addDependency(context.getCapabilityServiceName(
                    buildDynamicCapabilityName(PRINCIPAL_TRANSFORMER_CAPABILITY, principalTransformerName), PrincipalTransformer.class),
                    PrincipalTransformer.class, principalTransformer);
        }
    }

    private static void setSecurityFactory(InjectedValue<SecurityFactory> injectedValue, Consumer<SecurityFactory> securityFactoryConsumer) {
        SecurityFactory securityFactory = injectedValue.getOptionalValue();
        if (securityFactory != null) {
            securityFactoryConsumer.accept(securityFactory);
        }
    }

    private static void injectSecurityFactory(SimpleAttributeDefinition securityFactoryAttribute, ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model, Injector<SecurityFactory> securityFactoryInjector) throws OperationFailedException {
        String securityFactory = asStringIfDefined(context, securityFactoryAttribute, model);
        if (securityFactory != null) {
            serviceBuilder.addDependency(context.getCapabilityServiceName(
                    buildDynamicCapabilityName(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, securityFactory), SecurityFactory.class),
                    SecurityFactory.class, securityFactoryInjector);
        }
    }

    private static void setRealmMapper(InjectedValue<RealmMapper> injectedValue, Consumer<RealmMapper> realmMapperConsumer) {
        RealmMapper realmMapper = injectedValue.getOptionalValue();
        if (realmMapper != null) {
            realmMapperConsumer.accept(realmMapper);
        }
    }

    private static void injectRealmMapper(SimpleAttributeDefinition realmMapperAttribute, ServiceBuilder<?> serviceBuilder, OperationContext context, ModelNode model, Injector<RealmMapper> realmMapperInjector) throws OperationFailedException {
        String realmMapper = asStringIfDefined(context, realmMapperAttribute, model);
        if (realmMapper != null) {
            serviceBuilder.addDependency(context.getCapabilityServiceName(
                    buildDynamicCapabilityName(REALM_MAPPER_CAPABILITY, realmMapper), RealmMapper.class),
                    RealmMapper.class, realmMapperInjector);
        }
    }

    static ResourceDefinition getHttpAuthenticationFactory() {

        SimpleAttributeDefinition securityDomainAttribute = new SimpleAttributeDefinitionBuilder(BASE_SECURITY_DOMAIN_REF)
                .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, HTTP_AUTHENTICATION_FACTORY_CAPABILITY, true)
                .build();

        AttributeDefinition mechanismConfigurationAttribute = getMechanismConfiguration(HTTP_AUTHENTICATION_FACTORY_CAPABILITY);

        AttributeDefinition[] attributes = new AttributeDefinition[] { securityDomainAttribute, HTTP_SERVER_MECHANISM_FACTORY, mechanismConfigurationAttribute };
        AbstractAddStepHandler add = new TrivialAddHandler<HttpAuthenticationFactory>(HttpAuthenticationFactory.class, attributes, HTTP_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<HttpAuthenticationFactory> getValueSupplier(
                    ServiceBuilder<HttpAuthenticationFactory> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                final InjectedValue<SecurityDomain> securityDomainInjector = new InjectedValue<SecurityDomain>();
                final InjectedValue<HttpServerAuthenticationMechanismFactory> mechanismFactoryInjector = new InjectedValue<HttpServerAuthenticationMechanismFactory>();

                String securityDomain = securityDomainAttribute.resolveModelAttribute(context, model).asString();
                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(SECURITY_DOMAIN_CAPABILITY, securityDomain), SecurityDomain.class),
                        SecurityDomain.class, securityDomainInjector);

                String httpServerFactory = HTTP_SERVER_MECHANISM_FACTORY.resolveModelAttribute(context, model).asString();
                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, httpServerFactory), HttpServerAuthenticationMechanismFactory.class),
                        HttpServerAuthenticationMechanismFactory.class, mechanismFactoryInjector);

                final Set<String> supportedMechanisms = getConfiguredMechanismNames(mechanismConfigurationAttribute, context, model);
                final List<ResolvedMechanismConfiguration> resolvedMechanismConfigurations = getResolvedMechanismConfiguration(mechanismConfigurationAttribute, serviceBuilder, context, model);

                return () -> {
                    HttpServerAuthenticationMechanismFactory serverFactory = mechanismFactoryInjector.getValue();

                    // filter non-configured mechanisms out (when we are sure they are not configured)
                    if ( ! supportedMechanisms.isEmpty()) {
                        serverFactory = new FilterServerMechanismFactory(serverFactory, true, supportedMechanisms);
                    }

                    HttpAuthenticationFactory.Builder builder = HttpAuthenticationFactory.builder()
                            .setSecurityDomain(securityDomainInjector.getValue())
                            .setFactory(serverFactory);

                    buildMechanismConfiguration(resolvedMechanismConfigurations, builder);

                    return builder.build();
                };
            }
        };

        return wrap(new TrivialResourceDefinition(ElytronDescriptionConstants.HTTP_AUTHENTICATION_FACTORY,
                add, attributes, HTTP_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY), AuthenticationFactoryDefinitions::getAvailableHttpMechanisms);
    }

    private static String[] getAvailableHttpMechanisms(OperationContext context) {
        RuntimeCapability<Void> runtimeCapability = HTTP_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
        ServiceName securityDomainHttpConfigurationName = runtimeCapability.getCapabilityServiceName(HttpAuthenticationFactory.class);

        ServiceController<HttpAuthenticationFactory> serviceContainer = getRequiredService(context.getServiceRegistry(false), securityDomainHttpConfigurationName, HttpAuthenticationFactory.class);
        if (serviceContainer.getState() != State.UP) {
            return null;
        }

        Collection<String> mechanismNames = serviceContainer.getValue().getMechanismNames();
        return  mechanismNames.toArray(new String[mechanismNames.size()]);
    }

    static ResourceDefinition getSaslAuthenticationFactory() {
        SimpleAttributeDefinition securityDomainAttribute = new SimpleAttributeDefinitionBuilder(BASE_SECURITY_DOMAIN_REF)
                .setCapabilityReference(SECURITY_DOMAIN_CAPABILITY, SASL_AUTHENTICATION_FACTORY_CAPABILITY, true)
                .build();

        AttributeDefinition mechanismConfigurationAttribute = getMechanismConfiguration(SASL_AUTHENTICATION_FACTORY_CAPABILITY);

        AttributeDefinition[] attributes = new AttributeDefinition[] { securityDomainAttribute, SASL_SERVER_FACTORY, mechanismConfigurationAttribute };

        AbstractAddStepHandler add = new TrivialAddHandler<SaslAuthenticationFactory>(SaslAuthenticationFactory.class, attributes, SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SaslAuthenticationFactory> getValueSupplier(
                    ServiceBuilder<SaslAuthenticationFactory> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                String securityDomain = securityDomainAttribute.resolveModelAttribute(context, model).asString();
                String saslServerFactory = SASL_SERVER_FACTORY.resolveModelAttribute(context, model).asString();

                final InjectedValue<SecurityDomain> securityDomainInjector = new InjectedValue<SecurityDomain>();
                final InjectedValue<SaslServerFactory> saslServerFactoryInjector = new InjectedValue<SaslServerFactory>();

                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(SECURITY_DOMAIN_CAPABILITY, securityDomain), SecurityDomain.class),
                        SecurityDomain.class, securityDomainInjector);

                serviceBuilder.addDependency(context.getCapabilityServiceName(
                        buildDynamicCapabilityName(SASL_SERVER_FACTORY_CAPABILITY, saslServerFactory), SaslServerFactory.class),
                        SaslServerFactory.class, saslServerFactoryInjector);

                final Set<String> supportedMechanisms = getConfiguredMechanismNames(mechanismConfigurationAttribute, context, model);
                final List<ResolvedMechanismConfiguration> resolvedMechanismConfigurations = getResolvedMechanismConfiguration(mechanismConfigurationAttribute, serviceBuilder, context, model);

                return () -> {
                    SaslServerFactory serverFactory = saslServerFactoryInjector.getValue();

                    // filter non-configured mechanisms out (when we are sure they are not configured)
                    if ( ! supportedMechanisms.isEmpty()) {
                        serverFactory = new FilterMechanismSaslServerFactory(serverFactory, true, supportedMechanisms);
                    }

                    // sort mechanisms by strength
                    serverFactory = new SortedMechanismSaslServerFactory(serverFactory, AuthenticationFactoryDefinitions::compare);

                    SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder()
                            .setSecurityDomain(securityDomainInjector.getValue())
                            .setFactory(serverFactory);

                    buildMechanismConfiguration(resolvedMechanismConfigurations, builder);

                    return builder.build();
                };
            }
        };

        return wrap(new TrivialResourceDefinition(ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY,
                add, attributes, SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY), AuthenticationFactoryDefinitions::getAvailableSaslMechanisms);
    }

    private static String[] getAvailableSaslMechanisms(OperationContext context) {
        RuntimeCapability<Void> runtimeCapability = SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
        ServiceName securityDomainSaslConfigurationName = runtimeCapability.getCapabilityServiceName(SaslAuthenticationFactory.class);

        ServiceController<SaslAuthenticationFactory> serviceContainer = getRequiredService(context.getServiceRegistry(false), securityDomainSaslConfigurationName, SaslAuthenticationFactory.class);
        if (serviceContainer.getState() != State.UP) {
            return null;
        }

        Collection<String> mechanismNames = serviceContainer.getValue().getMechanismNames();
        return  mechanismNames.toArray(new String[mechanismNames.size()]);
    }

    private static int compare(String nameOne, String nameTwo) {
        return toPriority(nameTwo) - toPriority(nameOne);
    }

    private static int toPriority(String name) {
        switch (name) {
            case SaslMechanismInformation.Names.EXTERNAL:
                return 30;
            case SaslMechanismInformation.Names.GSSAPI:
                return 20;
            case "JBOSS-LOCAL-USER":
                return 10;
            case SaslMechanismInformation.Names.PLAIN:
                return -10;
            case SaslMechanismInformation.Names.ANONYMOUS:
                return -20;
            default:
                return 0;
        }
    }

    private static class ResolvedMechanismRealmConfiguration {
        final InjectedValue<PrincipalTransformer> preRealmPrincipalTranformer = new InjectedValue<>();
        final InjectedValue<PrincipalTransformer> postRealmPrincipalTransformer = new InjectedValue<>();
        final InjectedValue<PrincipalTransformer> finalPrincipalTransformer = new InjectedValue<>();
        final InjectedValue<RealmMapper> realmMapper = new InjectedValue<>();
    }

    private static class ResolvedMechanismConfiguration extends ResolvedMechanismRealmConfiguration {
        final Predicate<MechanismInformation> selectionPredicate;
        final Map<String, ResolvedMechanismRealmConfiguration> mechanismRealms = new HashMap<>();
        final InjectedValue<SecurityFactory> securityFactory = new InjectedValue<>();

        ResolvedMechanismConfiguration(Predicate<MechanismInformation> selectionPredicate) {
            this.selectionPredicate = selectionPredicate;

        }
    }

}
