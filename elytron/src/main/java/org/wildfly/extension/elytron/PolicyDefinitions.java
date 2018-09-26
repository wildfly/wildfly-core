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
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.acl.Group;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.security.auth.Subject;
import javax.security.jacc.PolicyContext;
import javax.security.jacc.PolicyContextException;
import javax.security.jacc.PolicyContextHandler;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.security.SecurityConstants;
import org.jboss.security.auth.callback.CallbackHandlerPolicyContextHandler;
import org.jboss.security.jacc.SubjectPolicyContextHandler;
import org.wildfly.common.Assert;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.authz.jacc.ElytronPolicyConfigurationFactory;
import org.wildfly.security.authz.jacc.JaccDelegatingPolicy;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.KeyPairCredential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.PublicKeyCredential;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.X509CertificateChainPrivateCredential;
import org.wildfly.security.credential.X509CertificateChainPublicCredential;
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

                ServiceName serviceName = POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName(Policy.class);
                InjectedValue<Supplier<Policy>> policyProviderInjector = new InjectedValue<>();
                ServiceTarget serviceTarget = context.getServiceTarget();
                ServiceBuilder<Policy> serviceBuilder = serviceTarget.addService(serviceName, createPolicyService(policyProviderInjector));
                Supplier<Policy> policySupplier = getPolicyProvider(context, model, serviceBuilder);

                policyProviderInjector.setValue(() -> policySupplier);

                serviceBuilder.setInitialMode(Mode.ACTIVE).install();

                if (!context.isBooting()) {
                    context.reloadRequired();
                }
            }

            private Service<Policy> createPolicyService(InjectedValue<Supplier<Policy>> injector) {
                return new Service<Policy>() {
                    volatile Policy delegated;
                    volatile Policy policy;

                    @Override
                    public void start(StartContext context) throws StartException {
                        delegated = getPolicy();
                        policy = injector.getValue().get();

                        try {
                            setPolicy(policy);
                            policy.refresh();
                        } catch (Exception cause) {
                            setPolicy(delegated);
                            throw ElytronSubsystemMessages.ROOT_LOGGER.failedToSetPolicy(policy, cause);
                        }
                    }

                    @Override
                    public void stop(StopContext context) {
                        setPolicy(delegated);
                    }

                    @Override
                    public Policy getValue() throws IllegalStateException, IllegalArgumentException {
                        return policy;
                    }

                    private void setPolicy(Policy policy) {
                        if (WildFlySecurityManager.isChecking()) {
                            AccessController.doPrivileged(setPolicyAction(policy));
                        } else {
                            setPolicyAction(policy).run();
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
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler() {
                    @Override
                    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
                        super.recordCapabilitiesAndRequirements(context, operation, resource);
                        context.deregisterCapability(JACC_POLICY_CAPABILITY); // even if it wasn't registered, deregistering is ok
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

    private static Supplier<Policy> getPolicyProvider(OperationContext context, ModelNode model, ServiceBuilder<Policy> serviceBuilder) throws OperationFailedException {
        Supplier<Policy> result = configureJaccPolicy(context, model, serviceBuilder);
        if (result == null) {
            result = configureCustomPolicy(context, model);
        }
        return result;
    }

    private static Supplier<Policy> configureCustomPolicy(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode policyModel = model.get(CUSTOM_POLICY);

        if (policyModel.isDefined()) {
            String className = CustomPolicyDefinition.CLASS_NAME.resolveModelAttribute(context, policyModel).asString();
            String module = CustomPolicyDefinition.MODULE.resolveModelAttribute(context, policyModel).asStringOrNull();

            return () -> newPolicy(className, module);
        }

        return null;
    }

    private static Supplier<Policy> configureJaccPolicy(OperationContext context, ModelNode model, ServiceBuilder<Policy> serviceBuilder) throws OperationFailedException {
        ModelNode policyModel = model.get(JACC_POLICY);

        if (policyModel.isDefined()) {
            String policyProvider = JaccPolicyDefinition.POLICY_PROVIDER.resolveModelAttribute(context, policyModel).asString();
            String configurationFactory = JaccPolicyDefinition.CONFIGURATION_FACTORY.resolveModelAttribute(context, policyModel).asString();
            String module = JaccPolicyDefinition.MODULE.resolveModelAttribute(context, policyModel).asStringOrNull();

            serviceBuilder.addAliases(JACC_POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName());

            return new Supplier<Policy>() {
                @Override
                public Policy get() {
                    if (configurationFactory != null) {
                        if (WildFlySecurityManager.isChecking()) {
                            AccessController.doPrivileged(setConfigurationProviderSystemProperty());
                        } else {
                            setConfigurationProviderSystemProperty().run();
                        }
                    }

                    Policy policy = newPolicy(policyProvider, module);

                    try {
                        PolicyContext.registerHandler(SecurityConstants.SUBJECT_CONTEXT_KEY, createSubjectPolicyContextHandler(), true);
                        PolicyContext.registerHandler(SecurityConstants.CALLBACK_HANDLER_KEY, createCallbackHandlerContextHandler(), true);
                        PolicyContext.registerHandler(SecurityIdentity.class.getName(), createSecurityIdentityContextHandler(), true);
                    } catch (PolicyContextException cause) {
                        throw ElytronSubsystemMessages.ROOT_LOGGER.failedToRegisterPolicyHandlers(cause);
                    }

                    return policy;
                }

                private PrivilegedAction<Void> setConfigurationProviderSystemProperty() {
                    return () -> {
                        if (WildFlySecurityManager.isChecking()) {
                            WildFlySecurityManager.setPropertyPrivileged("javax.security.jacc.PolicyConfigurationFactory.provider", configurationFactory);
                        } else {
                            System.setProperty("javax.security.jacc.PolicyConfigurationFactory.provider", configurationFactory);
                        }
                        return null;
                    };
                }

                private PolicyContextHandler createSecurityIdentityContextHandler() {
                    return new PolicyContextHandler() {
                        final String KEY = SecurityIdentity.class.getName();

                        @Override
                        public Object getContext(String key, Object data) throws PolicyContextException {
                            if (supports(key)) {
                                SecurityDomain securityDomain = doPrivileged((PrivilegedAction<SecurityDomain>) SecurityDomain::getCurrent);

                                if (securityDomain == null) {
                                    return null;
                                }

                                SecurityIdentity securityIdentity = securityDomain.getCurrentSecurityIdentity();

                                if (securityIdentity != null) {
                                    return securityIdentity;
                                }
                            }

                            return null;
                        }

                        @Override
                        public String[] getKeys() throws PolicyContextException {
                            return new String[]{KEY};
                        }

                        @Override
                        public boolean supports(String key) throws PolicyContextException {
                            return getKeys()[0].equalsIgnoreCase(key);
                        }
                    };
                }

                private PolicyContextHandler createCallbackHandlerContextHandler() {
                    return new PolicyContextHandler() {
                        // in case applications are using legacy (PicketBox) security infrastructure
                        CallbackHandlerPolicyContextHandler legacy = new CallbackHandlerPolicyContextHandler();

                        @Override
                        public Object getContext(String key, Object data) throws PolicyContextException {
                            return legacy.getContext(key, data);
                        }

                        @Override
                        public String[] getKeys() throws PolicyContextException {
                            return legacy.getKeys();
                        }

                        @Override
                        public boolean supports(String key) throws PolicyContextException {
                            return legacy.supports(key);
                        }
                    };
                }

                private PolicyContextHandler createSubjectPolicyContextHandler() {
                    return new PolicyContextHandler() {
                        // in case applications are using legacy (PicketBox) security infrastructure
                        SubjectPolicyContextHandler legacy = new SubjectPolicyContextHandler();

                        @Override
                        public Object getContext(String key, Object data) throws PolicyContextException {
                            if (supports(key)) {
                                SecurityIdentity securityIdentity = (SecurityIdentity) PolicyContext.getContext(SecurityIdentity.class.getName());

                                if (securityIdentity == null) {
                                    return legacy.getContext(key, data);
                                }

                                return SubjectUtil.fromSecurityIdentity(securityIdentity);
                            }

                            return null;
                        }

                        @Override
                        public String[] getKeys() throws PolicyContextException {
                            return legacy.getKeys();
                        }

                        @Override
                        public boolean supports(String key) throws PolicyContextException {
                            return legacy.supports(key);
                        }
                    };
                }
            };
        }

        return null;
    }

    private static Policy newPolicy(String className, String module) {
        try {
            ClassLoader classLoader = ClassLoadingAttributeDefinitions.resolveClassLoader(module);
            Object policy = classLoader.loadClass(className).newInstance();
            return Policy.class.cast(policy);
        } catch (Exception e) {
            throw ElytronSubsystemMessages.ROOT_LOGGER.failedToCreatePolicy(className, e);
        }
    }

    /**
     * Utilities for dealing with {@link Subject}.
     *
     * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
     */
    static final class SubjectUtil {

        /**
         * Converts the supplied {@link SecurityIdentity} into a {@link Subject}.
         *
         * @param securityIdentity the {@link SecurityIdentity} to be converted.
         * @return the constructed {@link Subject} instance.
         */
        static Subject fromSecurityIdentity(final SecurityIdentity securityIdentity) {
            Assert.checkNotNullParam("securityIdentity", securityIdentity);
            Subject subject = new Subject();
            subject.getPrincipals().add(securityIdentity.getPrincipal());

            // add the 'Roles' group to the subject containing the identity's mapped roles.
            Group rolesGroup = new SimpleGroup("Roles");
            for (String role : securityIdentity.getRoles()) {
                rolesGroup.addMember(new NamePrincipal(role));
            }
            subject.getPrincipals().add(rolesGroup);

            // add a 'CallerPrincipal' group containing the identity's principal.
            Group callerPrincipalGroup = new SimpleGroup("CallerPrincipal");
            callerPrincipalGroup.addMember(securityIdentity.getPrincipal());
            subject.getPrincipals().add(callerPrincipalGroup);

            // process the identity's public and private credentials.
            for (Credential credential : securityIdentity.getPublicCredentials()) {
                if (credential instanceof PublicKeyCredential) {
                    subject.getPublicCredentials().add(credential.castAs(PublicKeyCredential.class).getPublicKey());
                }
                else if (credential instanceof X509CertificateChainPublicCredential) {
                    subject.getPublicCredentials().add(credential.castAs(X509CertificateChainPublicCredential.class).getCertificateChain());
                }
                else {
                    subject.getPublicCredentials().add(credential);
                }
            }

            for (Credential credential : doPrivileged((PrivilegedAction<IdentityCredentials>) securityIdentity::getPrivateCredentials)) {
                if (credential instanceof PasswordCredential) {
                    addPrivateCredential(subject, credential.castAs(PasswordCredential.class).getPassword());
                }
                else if (credential instanceof SecretKeyCredential) {
                    addPrivateCredential(subject, credential.castAs(SecretKeyCredential.class).getSecretKey());
                }
                else if (credential instanceof KeyPairCredential) {
                    addPrivateCredential(subject, credential.castAs(KeyPairCredential.class).getKeyPair());
                }
                else if (credential instanceof X509CertificateChainPrivateCredential) {
                    addPrivateCredential(subject, credential.castAs(X509CertificateChainPrivateCredential.class).getCertificateChain());
                }
                else {
                    addPrivateCredential(subject, credential);
                }
            }

            // add the identity itself as a private credential - integration code can interact with the SI instead of the Subject if desired.
            addPrivateCredential(subject, securityIdentity);

            return subject;
        }

        static void addPrivateCredential(final Subject subject, final Object credential) {
            if (!WildFlySecurityManager.isChecking()) {
                subject.getPrivateCredentials().add(credential);
            }
            else {
                AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                    subject.getPrivateCredentials().add(credential);
                    return null;
                });
            }
        }


        private static class SimpleGroup implements Group {

            private final String name;

            private final Set<Principal> principals;

            SimpleGroup(final String name) {
                this.name = name;
                this.principals = new HashSet<>();
            }

            @Override
            public String getName() {
                return this.name;
            }

            @Override
            public boolean addMember(Principal principal) {
                return this.principals.add(principal);
            }

            @Override
            public boolean removeMember(Principal principal) {
                return this.principals.remove(principal);
            }

            @Override
            public Enumeration<? extends Principal> members() {
                return Collections.enumeration(this.principals);
            }

            @Override
            public boolean isMember(Principal principal) {
                return this.principals.contains(principal);
            }
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
