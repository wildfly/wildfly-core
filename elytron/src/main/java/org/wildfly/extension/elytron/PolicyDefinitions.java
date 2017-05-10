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

import static org.wildfly.extension.elytron.Capabilities.JACC_POLICY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.POLICY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JACC_POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;

import java.security.AccessController;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.acl.Group;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.security.auth.Subject;
import javax.security.jacc.PolicyContext;
import javax.security.jacc.PolicyContextException;
import javax.security.jacc.PolicyContextHandler;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.AttributeAccess;
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
import org.wildfly.security.auth.principal.NamePrincipal;
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

    private static final String DEFAULT_POLICY_NAME = "policy";

    // providers
    static final SimpleAttributeDefinition DEFAULT_POLICY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEFAULT_POLICY, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(DEFAULT_POLICY_NAME))
            .setAllowExpression(false)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static class JaccPolicyDefinition {
        static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition POLICY_PROVIDER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.POLICY, ModelType.STRING, true)
                .setDefaultValue(new ModelNode(JaccDelegatingPolicy.class.getName()))
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition CONFIGURATION_FACTORY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CONFIGURATION_FACTORY, ModelType.STRING, true)
                .setDefaultValue(new ModelNode(ElytronPolicyConfigurationFactory.class.getName()))
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition MODULE = ClassLoadingAttributeDefinitions.MODULE;
        static ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(JACC_POLICY, NAME, POLICY_PROVIDER, CONFIGURATION_FACTORY, MODULE).build();
        static final ObjectListAttributeDefinition POLICIES = new ObjectListAttributeDefinition.Builder(JACC_POLICY, POLICY)
                .setMinSize(1)
                .setRequired(false)
                .build();
    }

    static class CustomPolicyDefinition {
        static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setMinSize(1)
                .build();
        static final SimpleAttributeDefinition CLASS_NAME = ClassLoadingAttributeDefinitions.CLASS_NAME;
        static final SimpleAttributeDefinition MODULE = ClassLoadingAttributeDefinitions.MODULE;
        static ObjectTypeAttributeDefinition POLICY = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CUSTOM_POLICY, NAME, CLASS_NAME, MODULE).build();
        static final ObjectListAttributeDefinition POLICIES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.CUSTOM_POLICY, POLICY)
                .setRequired(false)
                .build();
    }

    static ResourceDefinition getPolicy() {
        AttributeDefinition[] attributes = new AttributeDefinition[] {DEFAULT_POLICY, JaccPolicyDefinition.POLICIES, CustomPolicyDefinition.POLICIES};
        AbstractAddStepHandler add = new BaseAddHandler(POLICY_RUNTIME_CAPABILITY, attributes) {
            @Override
            protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
                String defaultPolicy = context.getCurrentAddress().getLastElement().getValue();
                ServiceName serviceName = POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName(Policy.class);
                InjectedValue<Supplier<Policy>> policyProviderInjector = new InjectedValue<>();
                ServiceTarget serviceTarget = context.getServiceTarget();
                ServiceBuilder<Policy> serviceBuilder = serviceTarget.addService(serviceName, createPolicyService(policyProviderInjector));
                Supplier<Policy> policySupplier = getPolicyProvider(context, model, defaultPolicy, serviceBuilder);

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
                            setPolicy((Policy) policy);
                            policy.refresh();
                        } catch (Exception cause) {
                            setPolicy((Policy) delegated);
                            throw new RuntimeException("Failed to set policy [" + policy + "]", cause);
                        }
                    }

                    @Override
                    public void stop(StopContext context) {
                        setPolicy((Policy) delegated);
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
                        return (PrivilegedAction<Void>) () -> {
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
                        return (PrivilegedAction<Policy>) Policy::getPolicy;
                    }
                };
            }
        };

        return new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement(POLICY),
                ElytronExtension.getResourceDescriptionResolver(POLICY))
                .setAddHandler(add)
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setCapabilities(POLICY_RUNTIME_CAPABILITY)) {
            @Override
            public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
                OperationStepHandler write = new ReloadRequiredWriteAttributeHandler(attributes) {
                    @Override
                    protected void validateUpdatedModel(OperationContext context, Resource resource) throws OperationFailedException {
                        ModelNode model = resource.getModel();
                        String defaultPolicy = context.getCurrentAddress().getLastElement().getValue();

                        if (model.hasDefined(ElytronDescriptionConstants.DEFAULT_POLICY)) {
                            defaultPolicy = ElytronExtension.asStringIfDefined(context, DEFAULT_POLICY, model);
                        }

                        getPolicyProvider(context, model, defaultPolicy, null);
                    }
                };
                for (AttributeDefinition current : attributes) {
                    resourceRegistration.registerReadWriteAttribute(current, null, write);
                }
            }
        };

    }

    private static Supplier<Policy> getPolicyProvider(OperationContext context, ModelNode model, String defaultPolicy, ServiceBuilder<Policy> serviceBuilder) throws OperationFailedException {
        Map<String, Supplier<Policy>> policies = new HashMap<>();

        policies.computeIfAbsent(defaultPolicy, name -> {
            try {
                return configureJaccPolicy(context, model, name, serviceBuilder);
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
        });

        policies.computeIfAbsent(defaultPolicy, name -> {
            try {
                return configureCustomPolicies(context, model, name);
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
        });

        if (policies.isEmpty()) {
            throw new OperationFailedException("Could find policy provider with name [" + defaultPolicy + "]");
        }

        return policies.get(defaultPolicy);
    }

    private static Supplier<Policy> configureCustomPolicies(OperationContext context, ModelNode model, String defaultPolicy) throws OperationFailedException {
        ModelNode customPolicies = model.get(CUSTOM_POLICY);

        if (customPolicies.isDefined()) {
            for (ModelNode policyModel : customPolicies.asList()) {
                String name = ElytronExtension.asStringIfDefined(context, CustomPolicyDefinition.NAME, policyModel);

                if (!defaultPolicy.equals(name)) {
                    continue;
                }

                String className = ElytronExtension.asStringIfDefined(context, CustomPolicyDefinition.CLASS_NAME, policyModel);
                String module = ElytronExtension.asStringIfDefined(context, CustomPolicyDefinition.MODULE, policyModel);

                return (Supplier<Policy>) () -> newPolicy(className, module);
            }
        }

        return null;
    }

    private static Supplier<Policy> configureJaccPolicy(OperationContext context, ModelNode model, String defaultPolicy, ServiceBuilder<Policy> serviceBuilder) throws OperationFailedException {
        ModelNode jaccPolicies = model.get(JACC_POLICY);

        if (jaccPolicies.isDefined()) {
            for (ModelNode policyModel : jaccPolicies.asList()) {
                String name = ElytronExtension.asStringIfDefined(context, JaccPolicyDefinition.NAME, policyModel);

                if (!defaultPolicy.equals(name)) {
                    continue;
                }

                String policyProvider = ElytronExtension.asStringIfDefined(context, JaccPolicyDefinition.POLICY_PROVIDER, policyModel);
                String configurationFactory = ElytronExtension.asStringIfDefined(context, JaccPolicyDefinition.CONFIGURATION_FACTORY, policyModel);
                String module = ElytronExtension.asStringIfDefined(context, JaccPolicyDefinition.MODULE, policyModel);

                if (serviceBuilder != null) {
                    serviceBuilder.addAliases(JACC_POLICY_RUNTIME_CAPABILITY.getCapabilityServiceName());
                }

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
                            throw new RuntimeException("Failed to register policy context handlers.", cause);
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
                                    SecurityDomain securityDomain = SecurityDomain.getCurrent();

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
        }

        return null;
    }

    private static Policy newPolicy(String className, String module) {
        try {
            ClassLoader classLoader = ClassLoadingAttributeDefinitions.resolveClassLoader(module);
            Object policy = classLoader.loadClass(className).newInstance();
            return Policy.class.cast(policy);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create policy [" + className + "]", e);
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
        public static Subject fromSecurityIdentity(final SecurityIdentity securityIdentity) {
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

            for (Credential credential : securityIdentity.getPrivateCredentials()) {
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
}
