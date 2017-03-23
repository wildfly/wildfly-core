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

import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.RealmDefinitions.CASE_SENSITIVE;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.ModifiableRealmIdentity;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.evidence.PasswordGuessEvidence;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.SaltedSimpleDigestPassword;
import org.wildfly.security.password.interfaces.SimpleDigestPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.PasswordSpec;
import org.wildfly.security.password.spec.SaltedPasswordAlgorithmSpec;

/**
 * A {@link org.jboss.as.controller.ResourceDefinition} that defines identity management operations for those {@link ModifiableSecurityRealm} resources.
 *
 * @see SecurityRealmResourceDecorator
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class IdentityResourceDefinition extends SimpleResourceDefinition {

    private static final OperationStepHandler ADD = new IdentityAddHandler();
    private static final OperationStepHandler REMOVE = new IdentityRemoveHandler();

    IdentityResourceDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.IDENTITY),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MODIFIABLE_SECURITY_REALM, ElytronDescriptionConstants.IDENTITY))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ReadIdentityHandler.register(resourceRegistration, getResourceDescriptionResolver());
        registerAttributeOperations(resourceRegistration);
        registerCredentialOperations(resourceRegistration);
    }

    private void registerCredentialOperations(ManagementResourceRegistration resourceRegistration) {
        PasswordSetHandler.register(resourceRegistration, getResourceDescriptionResolver());
    }

    private void registerAttributeOperations(ManagementResourceRegistration resourceRegistration) {
        AttributeAddHandler.register(resourceRegistration, getResourceDescriptionResolver());
        AttributeRemoveHandler.register(resourceRegistration, getResourceDescriptionResolver());
    }

    private static class IdentityAddHandler extends BaseAddHandler {

        private IdentityAddHandler() {
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);
            String principalName = Normalizer.normalize(context.getCurrentAddressValue(), Normalizer.Form.NFKC);

            ModifiableRealmIdentity identity = null;
            try {
                identity = modifiableRealm.getRealmIdentityForUpdate(new NamePrincipal(principalName));

                if (identity.exists()) {
                    throw ROOT_LOGGER.identityAlreadyExists(principalName);
                }

                identity.create();
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotCreateIdentity(principalName, e);
            } finally {
                if (identity != null) {
                    identity.dispose();
                }
            }
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (!CASE_SENSITIVE.resolveModelAttribute(context, context.readResourceFromRoot(context.getCurrentAddress().getParent(), false).getModel()).asBoolean(false)) {
                String principalName = context.getCurrentAddressValue();
                if (!principalName.equals(principalName.toLowerCase(Locale.ROOT))) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.invalidUsername(principalName, context.getCurrentAddress().getParent().getLastElement().getValue());
                }
            }
            super.execute(context, operation);
        }
    }

    private static class IdentityRemoveHandler extends ElytronRuntimeOnlyHandler {

        private IdentityRemoveHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);
            String principalName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();

            ModifiableRealmIdentity realmIdentity = null;
            try {
                realmIdentity = modifiableRealm.getRealmIdentityForUpdate(new NamePrincipal(principalName));

                if (!realmIdentity.exists()) {
                    throw new OperationFailedException(ROOT_LOGGER.identityNotFound(principalName));
                }

                realmIdentity.delete();
                realmIdentity.dispose();
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotCreateIdentity(principalName, e);
            } finally {
                if (realmIdentity != null) {
                    realmIdentity.dispose();
                }
            }
        }
    }

    static class ReadSecurityDomainIdentityHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.READ_IDENTITY, descriptionResolver, NAME), new ReadSecurityDomainIdentityHandler());
        }

        private ReadSecurityDomainIdentityHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            // Retrieving the modifiable registry here as the SecurityDomain is used to create a new authentication
            // context.
            ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
            RuntimeCapability<Void> runtimeCapability = SECURITY_DOMAIN_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName domainServiceName = runtimeCapability.getCapabilityServiceName(SecurityDomain.class);
            ServiceController<SecurityDomain> serviceController = getRequiredService(serviceRegistry, domainServiceName, SecurityDomain.class);
            SecurityDomain domain = serviceController.getValue();
            ServerAuthenticationContext authenticationContext = domain.createNewAuthenticationContext();
            String principalName = NAME.resolveModelAttribute(context, operation).asString();

            try {
                authenticationContext.setAuthenticationName(principalName);

                if (!authenticationContext.exists()) {
                    context.getFailureDescription().add(ROOT_LOGGER.identityNotFound(principalName));
                    return;
                }

                if (!authenticationContext.authorize(principalName)) {
                    context.getFailureDescription().add(ROOT_LOGGER.identityNotAuthorized(principalName));
                    return;
                }

                SecurityIdentity identity = authenticationContext.getAuthorizedIdentity();
                ModelNode result = context.getResult();

                result.get(ElytronDescriptionConstants.NAME).set(principalName);

                ModelNode attributesNode = result.get(ElytronDescriptionConstants.ATTRIBUTES);

                identity.getAttributes().entries().forEach(entry -> {
                    ModelNode entryNode = attributesNode.get(entry.getKey()).setEmptyList();
                    entry.forEach(value -> entryNode.add(value));
                });

                ModelNode rolesNode = result.get(ElytronDescriptionConstants.ROLES);
                identity.getRoles().forEach(roleName -> rolesNode.add(roleName));
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotReadIdentity(principalName, domainServiceName, e);
            }
        }
    }

    static class ReadIdentityHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.READ_IDENTITY, descriptionResolver), new ReadIdentityHandler());
        }

        private ReadIdentityHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.ADDRESS)).getLastElement().getValue();
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context);

            try {
                if (!realmIdentity.exists()) {
                    context.getFailureDescription().add(ROOT_LOGGER.identityNotFound(principalName));
                    return;
                }
                AuthorizationIdentity identity = realmIdentity.getAuthorizationIdentity();
                ModelNode result = context.getResult();

                result.get(ElytronDescriptionConstants.NAME).set(principalName);

                ModelNode attributesNode = result.get(ElytronDescriptionConstants.ATTRIBUTES);

                identity.getAttributes().entries().forEach(entry -> {
                    ModelNode entryNode = attributesNode.get(entry.getKey()).setEmptyList();
                    entry.forEach(value -> entryNode.add(value));
                });

                ModelNode credentialsNode = result.get(ElytronDescriptionConstants.CREDENTIALS).setEmptyList();
                getCredentials(realmIdentity).forEach(password -> {
                    String passwordType;
                    if (password instanceof BCryptPassword) {
                        passwordType = ElytronDescriptionConstants.BCRYPT;
                    } else if (password instanceof ClearPassword) {
                        passwordType = ElytronDescriptionConstants.CLEAR;
                    } else if (password instanceof SimpleDigestPassword) {
                        passwordType = ElytronDescriptionConstants.SIMPLE_DIGEST;
                    } else if (password instanceof SaltedSimpleDigestPassword) {
                        passwordType = ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST;
                    } else if (password instanceof DigestPassword) {
                        passwordType = ElytronDescriptionConstants.DIGEST;
                    } else {
                        throw ROOT_LOGGER.unsupportedPasswordType(password.getClass());
                    }
                    credentialsNode.add(passwordType);
                });
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotReadIdentity(principalName, e);
            }
        }
    }

    static class AttributeAddHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static final SimpleListAttributeDefinition VALUES = new SimpleListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE, VALUE)
                .setMinSize(1)
                .setAllowExpression(false)
                .build();

        public static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver resourceDescriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.ADD_ATTRIBUTE, resourceDescriptionResolver, NAME, VALUES), new AttributeAddHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context);
            AuthorizationIdentity authorizationIdentity;

            try {
                authorizationIdentity = realmIdentity.getAuthorizationIdentity();
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotObtainAuthorizationIdentity(e);
            }

            try {
                Attributes attributes = new MapAttributes(authorizationIdentity.getAttributes());
                String name = NAME.resolveModelAttribute(context, operation).asString();
                VALUES.resolveModelAttribute(context, operation).asList().forEach(modelNode -> attributes.addLast(name, modelNode.asString()));

                realmIdentity.setAttributes(attributes);
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotAddAttribute(e);
            }
        }
    }

    static class AttributeRemoveHandler extends ElytronRuntimeOnlyHandler {

        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static final SimpleListAttributeDefinition VALUES = new SimpleListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE, VALUE)
                .setAllowNull(true)
                .setMinSize(0)
                .setAllowExpression(false)
                .build();

        public static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver resourceDescriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.REMOVE_ATTRIBUTE, resourceDescriptionResolver, NAME, VALUES), new AttributeRemoveHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context);
            AuthorizationIdentity authorizationIdentity;

            try {
                authorizationIdentity = realmIdentity.getAuthorizationIdentity();
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotObtainAuthorizationIdentity(e);
            }

            try {
                Attributes attributes = new MapAttributes(authorizationIdentity.getAttributes());

                String name = NAME.resolveModelAttribute(context, operation).asString();
                ModelNode valuesNode = VALUES.resolveModelAttribute(context, operation);

                if (valuesNode.isDefined()) {
                    for (ModelNode valueNode : valuesNode.asList()) {
                        attributes.removeAll(name, valueNode.asString());
                    }
                } else {
                    attributes.remove(name);
                }

                realmIdentity.setAttributes(attributes);
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotRemoveAttribute(e);
            }
        }
    }

    static class PasswordSetHandler extends ElytronRuntimeOnlyHandler {

        static class Bcrypt {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(BCryptPassword.ALGORITHM_BCRYPT))
                    .setValidator(new StringValuesValidator(BCryptPassword.ALGORITHM_BCRYPT))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .setMinSize(1)
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition ITERATION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ITERATION_COUNT, ModelType.INT, false)
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT, ModelType.BYTES, false)
                    .setAllowExpression(false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.BCRYPT, PASSWORD, SALT, ITERATION_COUNT)
                    .setAllowNull(true)
                    .build();
        }

        static class Clear {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(ClearPassword.ALGORITHM_CLEAR))
                    .setValidator(new StringValuesValidator(ClearPassword.ALGORITHM_CLEAR))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .setMinSize(1)
                    .setAllowExpression(false)
                    .build();


            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.CLEAR, PASSWORD)
                    .setAllowNull(true)
                    .build();
        }

        static class SimpleDigest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_512))
                    .setValidator(new StringValuesValidator(
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD2,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD5,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_1,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_256,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_384,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_512
                    ))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .setMinSize(1)
                    .setAllowExpression(false)
                    .build();


            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.SIMPLE_DIGEST, ALGORITHM, PASSWORD)
                    .setAllowNull(true)
                    .build();
        }

        static class SaltedSimpleDigest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_512))
                    .setValidator(new StringValuesValidator(
                            SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_MD5,
                            SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_1,
                            SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_256,
                            SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_384,
                            SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_512,
                            SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_MD5,
                            SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_1,
                            SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_256,
                            SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_384,
                            SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_512
                    ))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT, ModelType.BYTES, false)
                    .setAllowExpression(false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST, ALGORITHM, PASSWORD, SALT)
                    .setAllowNull(true)
                    .build();
        }

        static class Digest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(DigestPassword.ALGORITHM_DIGEST_SHA_512))
                    .setValidator(new StringValuesValidator(
                            DigestPassword.ALGORITHM_DIGEST_MD5,
                            DigestPassword.ALGORITHM_DIGEST_SHA,
                            DigestPassword.ALGORITHM_DIGEST_SHA_256,
                            DigestPassword.ALGORITHM_DIGEST_SHA_512
                    ))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.DIGEST, ALGORITHM, PASSWORD, REALM)
                    .setAllowNull(true)
                    .build();
        }

        static AttributeDefinition[] SUPPORTED_PASSWORDS = new AttributeDefinition[] {
            Bcrypt.OBJECT_DEFINITION,
            Clear.OBJECT_DEFINITION,
            SimpleDigest.OBJECT_DEFINITION,
            SaltedSimpleDigest.OBJECT_DEFINITION,
            Digest.OBJECT_DEFINITION
        };

        public static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver resourceDescriptionResolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(ElytronDescriptionConstants.SET_PASSWORD, resourceDescriptionResolver, SUPPORTED_PASSWORDS), new PasswordSetHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context);
            PathAddress currentAddress = context.getCurrentAddress();
            String principalName = currentAddress.getLastElement().getValue();
            List<Credential> passwords = new ArrayList<>();

            try {
                for (AttributeDefinition passwordDef : SUPPORTED_PASSWORDS) {
                    String passwordType = passwordDef.getName();
                    if (operation.hasDefined(passwordType)) {
                        passwords.add(new PasswordCredential(createPassword(context, principalName, passwordType, operation.get(passwordType))));
                    }
                }
                realmIdentity.setCredentials(passwords);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotCreatePassword(e);
            }
        }

        private Password createPassword(final OperationContext parentContext, final String principalName, String passwordType, ModelNode passwordNode) throws OperationFailedException, NoSuchAlgorithmException, InvalidKeySpecException {
            String password = Bcrypt.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
            final PasswordSpec passwordSpec;
            final String algorithm;

            if (passwordType.equals(ElytronDescriptionConstants.BCRYPT)) {
                byte[] salt = Bcrypt.SALT.resolveModelAttribute(parentContext, passwordNode).asBytes();
                int iterationCount = Bcrypt.ITERATION_COUNT.resolveModelAttribute(parentContext, passwordNode).asInt();
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), new IteratedSaltedPasswordAlgorithmSpec(iterationCount, salt));
                algorithm = Bcrypt.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
            } else if (passwordType.equals(ElytronDescriptionConstants.CLEAR)) {
                passwordSpec = new ClearPasswordSpec(password.toCharArray());
                algorithm = Clear.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
            } else if (passwordType.equals(ElytronDescriptionConstants.SIMPLE_DIGEST)) {
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), null);
                algorithm = SimpleDigest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
            } else if (passwordType.equals(ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST)) {
                byte[] salt = SaltedSimpleDigest.SALT.resolveModelAttribute(parentContext, passwordNode).asBytes();
                SaltedPasswordAlgorithmSpec spac = new SaltedPasswordAlgorithmSpec(salt);
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), spac);
                algorithm = SaltedSimpleDigest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
            } else if (passwordType.equals(ElytronDescriptionConstants.DIGEST)) {
                String realm = Digest.REALM.resolveModelAttribute(parentContext, passwordNode).asString();
                algorithm = Digest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
                DigestPasswordAlgorithmSpec dpas = new DigestPasswordAlgorithmSpec(principalName, realm);
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), dpas);
            } else {
                throw ROOT_LOGGER.unexpectedPasswordType(passwordType);
            }

            return PasswordFactory.getInstance(algorithm).generatePassword(passwordSpec);
        }
    }

    /**
     * Try to obtain a {@link ModifiableSecurityRealm} based on the given {@link OperationContext}.
     *
     * @param context the current context
     * @return the current security realm
     * @throws OperationFailedException if any error occurs obtaining the reference to the security realm.
     */
    private static ModifiableSecurityRealm getModifiableSecurityRealm(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.subAddress(0, currentAddress.size() - 1).getLastElement().getValue());
        ServiceName realmName = runtimeCapability.getCapabilityServiceName();
        ServiceController<ModifiableSecurityRealm> serviceController = getRequiredService(serviceRegistry, realmName, ModifiableSecurityRealm.class);

        return serviceController.getValue();
    }

    /**
     * Try to obtain a {@link ModifiableRealmIdentity} based on the identity and {@link ModifiableSecurityRealm} associated with given {@link OperationContext}.
     *
     * @param context the current context
     * @return the current identity
     * @throws OperationFailedException if the identity does not exists or if any error occurs while obtaining it.
     */
    private static ModifiableRealmIdentity getRealmIdentity(OperationContext context) throws OperationFailedException {
        ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);
        PathAddress currentAddress = context.getCurrentAddress();
        String principalName = currentAddress.getLastElement().getValue();

        ModifiableRealmIdentity realmIdentity = null;
        try {
             realmIdentity = modifiableRealm.getRealmIdentityForUpdate(new NamePrincipal(principalName));

            if (!realmIdentity.exists()) {
                throw new OperationFailedException(ROOT_LOGGER.identityNotFound(principalName));
            }

            return realmIdentity;
        } catch (RealmUnavailableException e) {
            throw ROOT_LOGGER.couldNotReadIdentity(principalName, e);
        } finally {
            if (realmIdentity != null) {
                realmIdentity.dispose();
            }
        }
    }

    /**
     * A simple {@link ModelTypeValidator} that requires that values are contained on a pre-defined list of string.
     * <p>
     * //TODO: couldn't find a built-in validator for that. see if there is one or even if it can be moved to its own file.
     */
    static class StringValuesValidator extends ModelTypeValidator implements AllowedValuesValidator {

        private List<ModelNode> allowedValues = new ArrayList<>();

        StringValuesValidator(String... values) {
            super(ModelType.STRING);

            for (String value : values) {
                allowedValues.add(new ModelNode().set(value));
            }
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);

            if (value.isDefined()) {
                if (!allowedValues.contains(value)) {
                    throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalidValue(value.asString(), parameterName, allowedValues));
                }
            }
        }

        @Override
        public List<ModelNode> getAllowedValues() {
            return this.allowedValues;
        }
    }

    private static List<Object> getCredentials(final ModifiableRealmIdentity realmIdentity) throws RealmUnavailableException {
        List<Object> credentials = new ArrayList<>();

        //TODO: accordingly with David, we should now obtain the credential names instead of the actual credential based on a type. However, this is not being considered in ELY-282 yet. Once this capability (obtain credential names) is implemented, need to change this method.
        //        Password credential = realmIdentity.getCredential(null, credentialType);
        //
        //        if (credential != null) {
        //            credentials.add(credential);
        //        }

        return credentials;
    }

    private static void addPassword(RealmIdentity realmIdentity, Class<? extends Password> credentialType, List<Object> credentials) throws RealmUnavailableException {

    }

    /**
     * <p>A temporary operation that performs authentication based on a {@link SecurityDomain}. This operation will be removed once
     * the subsystem is fully functional. It should be used for <em>test</em> purposes only.
     *
     * <p>This operation is very verbose in order to push messages back to CLI during tests.
     */
    static class AuthenticatorOperationHandler extends ElytronRuntimeOnlyHandler {

        private static final ServiceUtil<SecurityDomain> DOMAIN_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_DOMAIN_RUNTIME_CAPABILITY, ElytronDescriptionConstants.SECURITY_DOMAIN, SecurityDomain.class);

        private static final String OPERATION_NAME = "authenticate";
        private static final String PARAMETER_USERNAME = "username";
        private static final String PARAMETER_PASSWORD = "password";

        public static final SimpleAttributeDefinition USER_NAME = new SimpleAttributeDefinitionBuilder(PARAMETER_USERNAME, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        public static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(PARAMETER_PASSWORD, ModelType.STRING, false)
                .setAllowExpression(false)
                .build();

        static String getOperationName() {
            return OPERATION_NAME;
        }

        static AttributeDefinition[] getParameterDefinitions() {
            return new AttributeDefinition[] {USER_NAME, PASSWORD};
        }

        public static void register(final ManagementResourceRegistration resourceRegistration, final ResourceDescriptionResolver resolver) {
            resourceRegistration.registerOperationHandler(new SimpleOperationDefinition(AuthenticatorOperationHandler.getOperationName(), resolver,
                    AuthenticatorOperationHandler.getParameterDefinitions()), new AuthenticatorOperationHandler());
        }

        AuthenticatorOperationHandler() {
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = USER_NAME.resolveModelAttribute(context, operation).asString();
            String password = PASSWORD.resolveModelAttribute(context, operation).asString();
            SecurityDomain securityDomain = getSecurityDomain(context, operation);

            try {
                ServerAuthenticationContext authenticationContext = securityDomain.createNewAuthenticationContext();

                authenticationContext.setAuthenticationName(principalName);

                if (!authenticationContext.exists()) {
                    addFailureDescription("Principal [" + principalName + "] does not exist.", context);
                    return;
                }

                // for now, only clear passwords. we can provide an enum with different types later. if necessary.
                if (authenticationContext.verifyEvidence(new PasswordGuessEvidence(password.toCharArray()))) {
                    authenticationContext.authorize();
                    authenticationContext.succeed();

                    SecurityIdentity authorizedIdentity = authenticationContext.getAuthorizedIdentity();

                    if (authorizedIdentity == null) {
                        addFailureDescription("Principal [" + principalName + "] authenticated but no identity could be obtained.", context);
                        return;
                    }

                    context.getResult().add("Principal [" + principalName + "] successfully authenticated.");
                    context.getResult().add("Roles are " + authorizedIdentity.getRoles() + ".");
                } else {
                    authenticationContext.fail();
                    addFailureDescription("Invalid credentials for Principal [" + principalName + "].", context);
                }
            } catch (Exception cause) {
                addFailureDescription(cause.getMessage(), context);
                ElytronSubsystemMessages.ROOT_LOGGER.error(cause);
            }
        }

        private void addFailureDescription(String message, OperationContext context) {
            ModelNode failureDescription = context.getFailureDescription();
            failureDescription.add(message);
        }

        private SecurityDomain getSecurityDomain(OperationContext context, ModelNode operation) {
            // Retrieving the modifiable registry here as the SecurityDomain is used to create a new authentication
            // context.
            ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
            ServiceController<SecurityDomain> serviceController = getRequiredService(serviceRegistry, DOMAIN_SERVICE_UTIL.serviceName(operation), SecurityDomain.class);
            Service<SecurityDomain> service = serviceController.getService();

            return service.getValue();
        }
    }
}
