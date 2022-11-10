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

import static org.wildfly.extension.elytron.Capabilities.JACC_POLICY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.JACC_POLICY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.POLICY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;

import java.security.AccessController;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyContext;
import jakarta.security.jacc.PolicyContextException;
import jakarta.security.jacc.PolicyContextHandler;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.authz.jacc.DelegatingPolicyContextHandler;
import org.wildfly.security.authz.jacc.ElytronPolicyConfigurationFactory;
import org.wildfly.security.authz.jacc.JaccDelegatingPolicy;
import org.wildfly.security.authz.jacc.SecurityIdentityHandler;
import org.wildfly.security.authz.jacc.SubjectPolicyContextHandler;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A {@link ResourceDefinition} for configuring a {@link Policy}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PolicyDefinitions {

    // providers

    static final SimpleAttributeDefinition RESOURCE_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition DEFAULT_POLICY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_POLICY, ModelType.STRING)
            .setRequired(false)
            .setCorrector(new ParameterCorrector() {
                @Override
                public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
                    // Just discard the value as it's unused and we don't want to fool people
                    // by storing it
                    return new ModelNode();
                }
            })
            .setDeprecated(ElytronExtension.ELYTRON_1_2_0)
            .build();

    static class JaccPolicyDefinition {
        static final SimpleAttributeDefinition NAME = RESOURCE_NAME; // TODO Remove this once PolicyParser is deleted
        static final SimpleAttributeDefinition POLICY_PROVIDER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POLICY, ModelType.STRING, true)
                .setDefaultValue(new ModelNode(JaccDelegatingPolicy.class.getName()))
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition CONFIGURATION_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONFIGURATION_FACTORY, ModelType.STRING, true)
                .setDefaultValue(new ModelNode(ElytronPolicyConfigurationFactory.class.getName()))
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition MODULE = ClassLoadingAttributeDefinitions.MODULE;
        static final ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(JACC_POLICY, POLICY_PROVIDER, CONFIGURATION_FACTORY, MODULE)
                .setRequired(true)
                .setRestartJVM()
                .setAlternatives(CUSTOM_POLICY)
                .setCorrector(ListToObjectCorrector.INSTANCE)
                .build();
    }

    static class CustomPolicyDefinition {
        static final SimpleAttributeDefinition NAME = RESOURCE_NAME; // TODO Remove this once PolicyParser is deleted
        static final SimpleAttributeDefinition CLASS_NAME = ClassLoadingAttributeDefinitions.CLASS_NAME;
        static final SimpleAttributeDefinition MODULE = ClassLoadingAttributeDefinitions.MODULE;
        static final ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CUSTOM_POLICY, CLASS_NAME, MODULE)
                .setRequired(true)
                .setAlternatives(JACC_POLICY)
                .setCorrector(ListToObjectCorrector.INSTANCE)
                .build();
    }

    static ResourceDefinition getPolicy() {
        AttributeDefinition[] attributes = new AttributeDefinition[] {DEFAULT_POLICY, JaccPolicyDefinition.POLICY, CustomPolicyDefinition.POLICY};
        AbstractAddStepHandler add = new BaseAddHandler(POLICY_RUNTIME_CAPABILITY, attributes) {

            @Override
            protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
                super.populateModel(context, operation, resource);
                // default-policy is legacy cruft. We support setting it so legacy scripts don't fail,
                // but discard the value so we don't report garbage in read-resource etc
                resource.getModel().get(DEFAULT_POLICY.getName()).clear();
            }

            @Override
            protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
                super.recordCapabilitiesAndRequirements(context, operation, resource);
                if (resource.getModel().hasDefined(JACC_POLICY)) {
                    context.registerCapability(JACC_POLICY_RUNTIME_CAPABILITY);
                }
            }

            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

                CapabilityServiceSupport capabilitySupport = context.getCapabilityServiceSupport();
                final boolean legacyJacc = capabilitySupport.hasCapability("org.wildfly.legacy-security.jacc");
                final boolean legacyJaccTombstone = capabilitySupport.hasCapability("org.wildfly.legacy-security.jacc.tombstone");
                if (legacyJacc) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.unableToEnableJaccSupport();
                }
                if (legacyJaccTombstone) {
                    context.restartRequired();
                } else {
                    ServiceName serviceName = POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName(Policy.class);
                    ServiceTarget serviceTarget = context.getServiceTarget();
                    Consumer<Consumer<Policy>> policyConsumer = getPolicyProvider(context, model);
                    ServiceBuilder<Policy> serviceBuilder = serviceTarget.addService(serviceName, createPolicyService(policyConsumer));
                    if (model.get(JACC_POLICY).isDefined()) {
                        serviceBuilder.addAliases(JACC_POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName());
                    }

                    serviceBuilder.setInitialMode(Mode.ACTIVE).install();

                    if (!context.isBooting()) {
                        context.reloadRequired();
                    }
                }
            }

            private Service<Policy> createPolicyService(Consumer<Consumer<Policy>> policyProvider) {
                return new Service<Policy>() {
                    volatile Policy original;

                    @Override
                    public void start(StartContext context) throws StartException {
                        original = getPolicy();

                        try {
                            policyProvider.accept(this::setPolicy);
                        } catch (Exception cause) {
                            setPolicy(original);
                            throw new StartException(cause);
                        }
                    }

                    @Override
                    public void stop(StopContext context) {
                        setPolicy(original);
                    }

                    @Override
                    public Policy getValue() throws IllegalStateException, IllegalArgumentException {
                        return getPolicy();
                    }

                    private void setPolicy(Policy policy) {
                        policy.refresh();
                        try {
                            if (WildFlySecurityManager.isChecking()) {
                                AccessController.doPrivileged(setPolicyAction(policy));
                            } else {
                                setPolicyAction(policy).run();
                            }
                        } catch (Exception e) {
                            throw ElytronSubsystemMessages.ROOT_LOGGER.failedToSetPolicy(policy, e);
                        }
                    }

                    private PrivilegedAction<Void> setPolicyAction(Policy policy) {
                        return () -> {
                            Policy.setPolicy(policy);
                            return null;
                        };
                    }

                    private Policy getPolicy() {
                        if (WildFlySecurityManager.isChecking()) {
                            return AccessController.doPrivileged(getPolicyAction());
                        } else {
                            return getPolicyAction().run();
                        }
                    }

                    private PrivilegedAction<Policy> getPolicyAction() {
                        return Policy::getPolicy;
                    }
                };
            }
        };

        return new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement(POLICY),
                ElytronExtension.getResourceDescriptionResolver(POLICY))
                .setAddHandler(add)
                .setRemoveHandler(new AbstractRemoveStepHandler() {
                    @Override
                    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
                        super.performRuntime(context, operation, model);
                        // JACC settings require a restart to take place
                        if (model.get(JACC_POLICY).isDefined()) {
                            context.restartRequired();
                        } else {
                            context.reloadRequired();
                        }
                    }

                    @Override
                    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
                        super.recordCapabilitiesAndRequirements(context, operation, resource);
                        context.deregisterCapability(JACC_POLICY_CAPABILITY); // even if it wasn't registered, deregistering is ok
                    }

                    @Override
                    protected void recoverServices(OperationContext context,
                                                   ModelNode operation,
                                                   ModelNode model) throws OperationFailedException {
                        if (model.get(JACC_POLICY).isDefined()) {
                            context.revertRestartRequired();
                        } else {
                            context.revertReloadRequired();
                        }
                    }
                })
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(POLICY_RUNTIME_CAPABILITY)
                .setMaxOccurs(1)) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                OperationStepHandler write = new ReloadRequiredWriteAttributeHandler(attributes) {
                    @Override
                    protected void recordCapabilitiesAndRequirements(OperationContext context, AttributeDefinition attributeDefinition, ModelNode newValue, ModelNode oldValue) {
                        super.recordCapabilitiesAndRequirements(context, attributeDefinition, newValue, oldValue);
                        if (JACC_POLICY.equals(attributeDefinition.getName())) {
                            if (!newValue.isDefined()) {
                                context.deregisterCapability(JACC_POLICY_CAPABILITY);  // even if it wasn't registered, deregistering is ok
                            } else if (!oldValue.isDefined()) {
                                // Defined now but wasn't before; register
                                context.registerCapability(JACC_POLICY_RUNTIME_CAPABILITY);
                            }
                        }
                    }
                };
                for (AttributeDefinition current : attributes) {
                    if (current != DEFAULT_POLICY) {
                        resourceRegistration.registerReadWriteAttribute(current, null, write);
                    } else {
                        resourceRegistration.registerReadWriteAttribute(current, null,
                                new ModelOnlyWriteAttributeHandler(DEFAULT_POLICY));
                    }
                }
            }
        };

    }

    private static Consumer<Consumer<Policy>> getPolicyProvider(OperationContext context, ModelNode model) throws OperationFailedException {
        Consumer<Consumer<Policy>> result = configureJaccPolicy(context, model);
        if (result == null) {
            result = configureCustomPolicy(context, model);
        }
        return result;
    }

    private static Consumer<Consumer<Policy>> configureCustomPolicy(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode policyModel = model.get(CUSTOM_POLICY);

        if (policyModel.isDefined()) {
            String className = CustomPolicyDefinition.CLASS_NAME.resolveModelAttribute(context, policyModel).asString();
            String module = CustomPolicyDefinition.MODULE.resolveModelAttribute(context, policyModel).asStringOrNull();

            return (t) -> {
                try {
                    t.accept(newPolicy(className, ClassLoadingAttributeDefinitions.resolveClassLoader(module)));
                } catch (ModuleLoadException e) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.unableToLoadModuleRuntime(module, e);
                }
            };
        }

        return null;
    }

    private static Consumer<Consumer<Policy>> configureJaccPolicy(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode policyModel = model.get(JACC_POLICY);

        if (policyModel.isDefined()) {
            final String policyProvider = JaccPolicyDefinition.POLICY_PROVIDER.resolveModelAttribute(context, policyModel).asString();
            final String configurationFactory = JaccPolicyDefinition.CONFIGURATION_FACTORY.resolveModelAttribute(context, policyModel).asString();
            final boolean defaultConfigurationFactory = configurationFactory.equals(JaccPolicyDefinition.CONFIGURATION_FACTORY.getDefaultValue().asString());
            String module = JaccPolicyDefinition.MODULE.resolveModelAttribute(context, policyModel).asStringOrNull();

            return new Consumer<Consumer<Policy>>() {

                @Override
                public void accept(Consumer<Policy> policyConsumer) {

                    try {
                        ClassLoader configuredClassLoader = ClassLoadingAttributeDefinitions.resolveClassLoader(module);

                        Policy policy = newPolicy(policyProvider, configuredClassLoader);
                        policyConsumer.accept(policy);

                        doPrivileged((PrivilegedExceptionAction<PolicyConfigurationFactory>) () -> newPolicyConfigurationFactory(
                                configurationFactory,
                                defaultConfigurationFactory ? PolicyDefinitions.class.getClassLoader() : configuredClassLoader));

                        Map<String, PolicyContextHandler> discoveredHandlers = discoverPolicyContextHandlers();

                        registerHandler(discoveredHandlers, new SubjectPolicyContextHandler());
                        registerHandler(discoveredHandlers, new SecurityIdentityHandler());
                        for (Entry<String, PolicyContextHandler> entry : discoveredHandlers.entrySet()) {
                            PolicyContext.registerHandler(entry.getKey(), entry.getValue(), true);
                        }

                    } catch (Exception cause) {
                        throw ElytronSubsystemMessages.ROOT_LOGGER.failedToRegisterPolicyHandlers(cause);
                    }
                }

                private void registerHandler(Map<String, PolicyContextHandler> discoveredHandlers, PolicyContextHandler handler) throws PolicyContextException {
                    for (String key : handler.getKeys()) {
                        PolicyContextHandler discovered = discoveredHandlers.remove(key);
                        if (discovered != null) {
                            ElytronSubsystemMessages.ROOT_LOGGER.tracef("Registering DelegatingPolicyContextHandler for key '%s'.", key);
                            PolicyContext.registerHandler(key, new DelegatingPolicyContextHandler(key, handler, discovered), true);
                        } else {
                            PolicyContext.registerHandler(key, handler, true);
                        }
                    }
                }

                private Map<String, PolicyContextHandler> discoverPolicyContextHandlers() throws PolicyContextException {
                    Map<String, PolicyContextHandler> handlerMap = new HashMap<>();
                    ServiceLoader<PolicyContextHandler> serviceLoader = ServiceLoader.load(PolicyContextHandler.class, PolicyDefinitions.class.getClassLoader());
                    for (PolicyContextHandler handler : serviceLoader) {
                        for (String key : handler.getKeys()) {
                            if (handlerMap.put(key, handler) != null) {
                                throw ElytronSubsystemMessages.ROOT_LOGGER.duplicatePolicyContextHandler(key);
                            }
                            if (ElytronSubsystemMessages.ROOT_LOGGER.isTraceEnabled()) {
                                ElytronSubsystemMessages.ROOT_LOGGER.tracef("Discovered PolicyContextHandler '%s' for key '%s'.", handler.getClass().getName(), key);
                            }
                        }
                    }

                    return handlerMap;
                }

            };
        }

        return null;
    }

    private static Policy newPolicy(String className, ClassLoader classLoader) {
        try {
            Object policy = classLoader.loadClass(className).newInstance();
            return Policy.class.cast(policy);
        } catch (Exception e) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.failedToCreatePolicy(className, e);
        }
    }

    private static PolicyConfigurationFactory newPolicyConfigurationFactory(String className, ClassLoader classLoader) throws PolicyContextException, ClassNotFoundException {
        final ClassLoader original = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            System.setProperty(PolicyConfigurationFactory.class.getName() + ".provider", className);
            PolicyConfigurationFactory policyConfigurationFactory = PolicyConfigurationFactory.getPolicyConfigurationFactory();
            String loadedClassName = policyConfigurationFactory.getClass().getName();
            if (className.equals(loadedClassName) == false) {
                throw ElytronSubsystemMessages.ROOT_LOGGER.invalidImplementationLoaded(PolicyConfigurationFactory.class.getCanonicalName(), className, loadedClassName);
            }

            return policyConfigurationFactory;
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

    }

    // The jacc-policy and custom-policy attributes used to be LIST of OBJECT
    // in some early WF Core 3.0.x releases. In case people submit such lists,
    // if they only have 1 element, correct to just use that element. If they
    // have multiple elements we can't tell here which is wanted, so don't
    // correct and it will fail validation.
    private static class ListToObjectCorrector implements ParameterCorrector {
        private static final ListToObjectCorrector INSTANCE = new ListToObjectCorrector();
        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            ModelNode result = newValue;
            if (newValue.getType() == ModelType.LIST && newValue.asInt() == 1) {
                // extract the single element
                result = newValue.get(0);
                if (result.has(NAME)) {
                    result.remove(NAME);
                }
            }
            return result;
        }
    }
}
