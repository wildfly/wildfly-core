/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.ModifiableRealmIdentity;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.authz.Attributes;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.OneTimePassword;
import org.wildfly.security.password.interfaces.SaltedSimpleDigestPassword;
import org.wildfly.security.password.interfaces.SimpleDigestPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.OneTimePasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;
import org.wildfly.security.password.spec.SaltedPasswordAlgorithmSpec;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * A {@link DelegatingResourceDefinition} that decorates a {@link ModifiableSecurityRealm} resource
 * with identity manipulation operations definitions.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class ModifiableRealmDecorator extends DelegatingResourceDefinition {

    static ResourceDefinition wrap(ResourceDefinition resourceDefinition) {
        return new ModifiableRealmDecorator(resourceDefinition);
    }

    private ModifiableRealmDecorator(ResourceDefinition resourceDefinition) {
        setDelegate(resourceDefinition);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MODIFIABLE_SECURITY_REALM);
        ReadIdentityHandler.register(resourceRegistration, resolver);
        if (isServerOrHostController(resourceRegistration)) {
            AddIdentityHandler.register(resourceRegistration, resolver);
            RemoveIdentityHandler.register(resourceRegistration, resolver);
            AddIdentityAttributeHandler.register(resourceRegistration, resolver);
            RemoveIdentityAttributeHandler.register(resourceRegistration, resolver);
            SetPasswordHandler.register(resourceRegistration, resolver);
        }
    }

    static class AddIdentityHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.ADD_IDENTITY, descriptionResolver)
                            .setParameters(IDENTITY)
                            .setRuntimeOnly()
                            .build(),
                    new AddIdentityHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);

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
    }

    static class RemoveIdentityHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REMOVE_IDENTITY, descriptionResolver)
                            .setParameters(IDENTITY)
                            .setRuntimeOnly()
                            .build(),
                    new RemoveIdentityHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);

            ModifiableRealmIdentity identity = null;
            try {
                identity = modifiableRealm.getRealmIdentityForUpdate(new NamePrincipal(principalName));

                if (!identity.exists()) {
                    throw new OperationFailedException(ROOT_LOGGER.identityNotFound(principalName));
                }

                identity.delete();
                identity.dispose();
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotDeleteIdentity(principalName, e);
            } finally {
                if (identity != null) {
                    identity.dispose();
                }
            }
        }
    }

    static class ReadIdentityHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_IDENTITY, descriptionResolver)
                            .setParameters(IDENTITY)
                            .setRuntimeOnly()
                            .setReadOnly()
                            .build(),
                    new ReadIdentityHandler());
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context, principalName);

            try {
                AuthorizationIdentity identity = realmIdentity.getAuthorizationIdentity();
                ModelNode result = context.getResult();

                result.get(ElytronDescriptionConstants.NAME).set(principalName);

                ModelNode attributesNode = result.get(ElytronDescriptionConstants.ATTRIBUTES);
                identity.getAttributes().entries().forEach(entry -> {
                    ModelNode entryNode = attributesNode.get(entry.getKey()).setEmptyList();
                    entry.forEach(entryNode::add);
                });
            } catch (RealmUnavailableException e) {
                throw ROOT_LOGGER.couldNotReadIdentity(principalName, e);
            }
        }
    }

    static class AddIdentityAttributeHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .build();

        static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
                .build();

        static final SimpleListAttributeDefinition VALUES = new SimpleListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE, VALUE)
                .setMinSize(1)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.ADD_IDENTITY_ATTRIBUTE, descriptionResolver)
                            .setParameters(IDENTITY, NAME, VALUES)
                            .setRuntimeOnly()
                            .build(),
                    new AddIdentityAttributeHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context, principalName);
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

    static class RemoveIdentityAttributeHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .build();

        static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
                .build();

        static final SimpleListAttributeDefinition VALUES = new SimpleListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE, VALUE)
                .setRequired(false)
                .setMinSize(0)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REMOVE_IDENTITY_ATTRIBUTE, descriptionResolver)
                            .setParameters(IDENTITY, NAME, VALUES)
                            .setRuntimeOnly()
                            .build(),
                    new RemoveIdentityAttributeHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context, principalName);
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

    static class SetPasswordHandler extends ElytronRuntimeOnlyHandler {

        static class Bcrypt {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(BCryptPassword.ALGORITHM_BCRYPT))
                    .setValidator(new StringAllowedValuesValidator(BCryptPassword.ALGORITHM_BCRYPT))
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .build();

            static final SimpleAttributeDefinition ITERATION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ITERATION_COUNT, ModelType.INT, false)
                    .build();

            static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT, ModelType.BYTES, false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.BCRYPT, ALGORITHM, PASSWORD, SALT, ITERATION_COUNT)
                    .setRequired(false)
                    .build();
        }

        static class Clear {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(ClearPassword.ALGORITHM_CLEAR))
                    .setValidator(new StringAllowedValuesValidator(ClearPassword.ALGORITHM_CLEAR))
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .build();


            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.CLEAR, PASSWORD)
                    .setRequired(false)
                    .build();
        }

        static class SimpleDigest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_512))
                    .setValidator(new StringAllowedValuesValidator(
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD2,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD5,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_1,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_256,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_384,
                            SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_512
                    ))
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .build();


            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.SIMPLE_DIGEST, ALGORITHM, PASSWORD)
                    .setRequired(false)
                    .build();
        }

        static class SaltedSimpleDigest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_512))
                    .setValidator(new StringAllowedValuesValidator(
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
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .build();

            static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT, ModelType.BYTES, false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST, ALGORITHM, PASSWORD, SALT)
                    .setRequired(false)
                    .build();
        }

        static class Digest {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(DigestPassword.ALGORITHM_DIGEST_SHA_512))
                    .setValidator(new StringAllowedValuesValidator(
                            DigestPassword.ALGORITHM_DIGEST_MD5,
                            DigestPassword.ALGORITHM_DIGEST_SHA,
                            DigestPassword.ALGORITHM_DIGEST_SHA_256,
                            DigestPassword.ALGORITHM_DIGEST_SHA_512
                    ))
                    .build();

            static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, false)
                    .build();

            static final SimpleAttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REALM, ModelType.STRING, false)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.DIGEST, ALGORITHM, PASSWORD, REALM)
                    .setRequired(false)
                    .build();
        }

        static class OTPassword {
            static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                    .setRequired(false)
                    .setDefaultValue(new ModelNode(OneTimePassword.ALGORITHM_OTP_SHA1))
                    .setValidator(new StringAllowedValuesValidator(
                            OneTimePassword.ALGORITHM_OTP_MD5,
                            OneTimePassword.ALGORITHM_OTP_SHA1
                    ))
                    .setAllowExpression(false)
                    .build();

            static final SimpleAttributeDefinition HASH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH, ModelType.STRING, false)
                    .setAllowExpression(true)
                    .build();

            static final SimpleAttributeDefinition SEED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEED, ModelType.STRING, false)
                    .setAllowExpression(true)
                    .build();

            static final SimpleAttributeDefinition SEQUENCE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEQUENCE, ModelType.INT, false)
                    .setAllowExpression(true)
                    .build();

            static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                    ElytronDescriptionConstants.OTP, ALGORITHM, HASH, SEED, SEQUENCE)
                    .setAllowNull(true)
                    .build();
        }

        static final SimpleAttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
                .build();

        static AttributeDefinition[] SUPPORTED_PASSWORDS = new AttributeDefinition[] {
                SetPasswordHandler.Bcrypt.OBJECT_DEFINITION,
                SetPasswordHandler.Clear.OBJECT_DEFINITION,
                SetPasswordHandler.SimpleDigest.OBJECT_DEFINITION,
                SetPasswordHandler.SaltedSimpleDigest.OBJECT_DEFINITION,
                SetPasswordHandler.Digest.OBJECT_DEFINITION,
                SetPasswordHandler.OTPassword.OBJECT_DEFINITION
        };

        static AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
                IDENTITY,
                SetPasswordHandler.Bcrypt.OBJECT_DEFINITION,
                SetPasswordHandler.Clear.OBJECT_DEFINITION,
                SetPasswordHandler.SimpleDigest.OBJECT_DEFINITION,
                SetPasswordHandler.SaltedSimpleDigest.OBJECT_DEFINITION,
                SetPasswordHandler.Digest.OBJECT_DEFINITION,
                SetPasswordHandler.OTPassword.OBJECT_DEFINITION
        };

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.SET_PASSWORD, descriptionResolver)
                            .setParameters(ATTRIBUTES)
                            .setRuntimeOnly()
                            .build(),
                    new SetPasswordHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            String principalName = IDENTITY.resolveModelAttribute(context, operation).asString();
            ModifiableRealmIdentity realmIdentity = getRealmIdentity(context, principalName);
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
            String password;
            final PasswordSpec passwordSpec;
            final String algorithm;

            if (passwordType.equals(ElytronDescriptionConstants.BCRYPT)) {
                password = Bcrypt.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
                byte[] salt = Bcrypt.SALT.resolveModelAttribute(parentContext, passwordNode).asBytes();
                int iterationCount = Bcrypt.ITERATION_COUNT.resolveModelAttribute(parentContext, passwordNode).asInt();
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), new IteratedSaltedPasswordAlgorithmSpec(iterationCount, salt));
                algorithm = Bcrypt.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();

            } else if (passwordType.equals(ElytronDescriptionConstants.CLEAR)) {
                password = Clear.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
                passwordSpec = new ClearPasswordSpec(password.toCharArray());
                algorithm = Clear.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();

            } else if (passwordType.equals(ElytronDescriptionConstants.SIMPLE_DIGEST)) {
                password = SimpleDigest.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), null);
                algorithm = SimpleDigest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();

            } else if (passwordType.equals(ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST)) {
                password = SaltedSimpleDigest.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
                byte[] salt = SaltedSimpleDigest.SALT.resolveModelAttribute(parentContext, passwordNode).asBytes();
                SaltedPasswordAlgorithmSpec spec = new SaltedPasswordAlgorithmSpec(salt);
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), spec);
                algorithm = SaltedSimpleDigest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();

            } else if (passwordType.equals(ElytronDescriptionConstants.DIGEST)) {
                password = Digest.PASSWORD.resolveModelAttribute(parentContext, passwordNode).asString();
                String realm = Digest.REALM.resolveModelAttribute(parentContext, passwordNode).asString();
                algorithm = Digest.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
                DigestPasswordAlgorithmSpec dpas = new DigestPasswordAlgorithmSpec(principalName, realm);
                passwordSpec = new EncryptablePasswordSpec(password.toCharArray(), dpas);

            } else if (passwordType.equals(ElytronDescriptionConstants.OTP)) {
                algorithm = OTPassword.ALGORITHM.resolveModelAttribute(parentContext, passwordNode).asString();
                byte[] hash = OTPassword.HASH.resolveModelAttribute(parentContext, passwordNode).asBytes();
                String seed = OTPassword.SEED.resolveModelAttribute(parentContext, passwordNode).asString();
                int sequenceNumber = OTPassword.SEQUENCE.resolveModelAttribute(parentContext, passwordNode).asInt();
                passwordSpec = new OneTimePasswordSpec(hash, seed, sequenceNumber);

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
    static ModifiableSecurityRealm getModifiableSecurityRealm(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
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
    static ModifiableRealmIdentity getRealmIdentity(OperationContext context, String principalName) throws OperationFailedException {
        ModifiableSecurityRealm modifiableRealm = getModifiableSecurityRealm(context);
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
}
