/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.ServiceStateDefinition.STATE;
import static org.wildfly.extension.elytron.ServiceStateDefinition.populateResponse;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;
import static org.wildfly.security.encryption.SecretKeyUtil.exportSecretKey;
import static org.wildfly.security.encryption.SecretKeyUtil.generateSecretKey;
import static org.wildfly.security.encryption.SecretKeyUtil.importSecretKey;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.crypto.SecretKey;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.UnsupportedCredentialTypeException;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

/**
 * A common base for resource definitions representing credential stores.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class AbstractCredentialStoreResourceDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<CredentialStore> CREDENTIAL_STORE_UTIL = ServiceUtil.newInstance(CREDENTIAL_STORE_RUNTIME_CAPABILITY, ElytronDescriptionConstants.CREDENTIAL_STORE, CredentialStore.class);

    protected ServiceUtil<CredentialStore> getCredentialStoreUtil() {
        return CREDENTIAL_STORE_UTIL;
    }

    // Operations

    static final StandardResourceDescriptionResolver OPERATION_RESOLVER = ElytronExtension
            .getResourceDescriptionResolver(ElytronDescriptionConstants.CREDENTIAL_STORE,
                    ElytronDescriptionConstants.OPERATIONS);

    static final SimpleOperationDefinition READ_ALIASES = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.READ_ALIASES, OPERATION_RESOLVER)
            .setRuntimeOnly()
            .setReadOnly()
            .build();

    static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition KEY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY, ModelType.STRING, false)
            .setMinSize(1)
            .build();

    static final SimpleOperationDefinition EXPORT_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.EXPORT_SECRET_KEY, OPERATION_RESOLVER)
            .setParameters(ALIAS)
            .setRuntimeOnly()
            .build();

    static final SimpleOperationDefinition IMPORT_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.IMPORT_SECRET_KEY, OPERATION_RESOLVER)
            .setParameters(ALIAS, KEY)
            .setRuntimeOnly()
            .build();

    static final SimpleOperationDefinition RELOAD = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.RELOAD, OPERATION_RESOLVER)
            .setRuntimeOnly()
            .build();

    static final OperationStepHandler RELOAD_HANDLER = new CredentialStoreReloadHandler();

    protected AbstractCredentialStoreResourceDefinition(Parameters parameters) {
        super(parameters);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AttributeDefinition[] configAttributes = getAttributeDefinitions();
        AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(configAttributes);
        for (AttributeDefinition current : configAttributes) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(STATE, new ElytronRuntimeOnlyHandler() {

                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceName credentialStoreClientServiceName = getCredentialStoreUtil().serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(credentialStoreClientServiceName);

                    populateResponse(context.getResult(), serviceController);
                }

            });
        }
    }

    protected abstract AttributeDefinition[] getAttributeDefinitions();

    protected void readAliasesOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        try {
            try {
                List<ModelNode> list = new ArrayList<>();
                Set<String> aliases = credentialStore.getAliases();
                for (String s : aliases) {
                    ModelNode modelNode = new ModelNode(s);
                    list.add(modelNode);
                }
                context.getResult().set(list);
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, dumpCause(e));
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new OperationFailedException(e);
        }
    }

    protected void removeAliasOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore, Class<? extends Credential> credentialType) throws OperationFailedException {
        try {
            try {
                String alias = ALIAS.resolveModelAttribute(context, operation).asString();

                Credential retrieved = credentialStore.retrieve(alias, credentialType);
                if (retrieved == null) {
                    throw ROOT_LOGGER.credentialDoesNotExist(alias, credentialType.getSimpleName());
                }
                credentialStore.remove(alias, credentialType);
                context.addResponseWarning(Level.WARNING, ROOT_LOGGER.updateDependantServices(alias));
                try {
                    credentialStore.flush();
                } catch (CredentialStoreException e) {
                    // the operation fails, return removed entry back to the store to avoid an inconsistency
                    // between the store on the FS and in the memory
                    credentialStore.store(alias, retrieved);
                    throw e;
                }
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, dumpCause(e));
            }
        } catch (RuntimeException e) {
            throw new OperationFailedException(e);
        }
    }

    protected void exportSecretKeyOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore)
            throws OperationFailedException {
        try {
            String alias = ALIAS.resolveModelAttribute(context, operation).asString();

            SecretKeyCredential credential = credentialStore.retrieve(alias, SecretKeyCredential.class);
            if (credential == null) {
                throw ROOT_LOGGER.credentialDoesNotExist(alias, SecretKeyCredential.class.getSimpleName());
            }

            SecretKey secretKey = credential.getSecretKey();
            String exportedKey = exportSecretKey(secretKey);

            ModelNode result = context.getResult();
            result.get(ElytronDescriptionConstants.KEY).set(exportedKey);
        } catch (GeneralSecurityException e) {
            throw ROOT_LOGGER.secretKeyOperationFailed(ElytronDescriptionConstants.EXPORT_SECRET_KEY, dumpCause(e), e);
        }
    }

    protected void importSecretKeyOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore)
            throws OperationFailedException {
        try {
            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String rawKey = KEY.resolveModelAttribute(context, operation).asString();

            if (credentialStore.exists(alias, SecretKeyCredential.class)) {
                throw ROOT_LOGGER.credentialAlreadyExists(alias, SecretKeyCredential.class.getName());
            }

            SecretKey secretKey = importSecretKey(rawKey);

            storeSecretKey(credentialStore, alias, secretKey);

        } catch (GeneralSecurityException e) {
            throw ROOT_LOGGER.secretKeyOperationFailed(ElytronDescriptionConstants.IMPORT_SECRET_KEY, dumpCause(e), e);
        }
    }

    protected void generateSecretKeyOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore,
            int keySize) throws OperationFailedException {
        try {
            String alias = ALIAS.resolveModelAttribute(context, operation).asString();

            if (credentialStore.exists(alias, SecretKeyCredential.class)) {
                throw ROOT_LOGGER.credentialAlreadyExists(alias, SecretKeyCredential.class.getName());
            }

            SecretKey secretKey = generateSecretKey(keySize);
            storeSecretKey(credentialStore, alias, secretKey);

        } catch (GeneralSecurityException e) {
            throw ROOT_LOGGER.secretKeyOperationFailed(ElytronDescriptionConstants.GENERATE_SECRET_KEY, dumpCause(e), e);
        }
    }

    /**
     * Convert {@code char[]} password to {@code PasswordCredential}
     * @param password to convert
     * @return new {@code PasswordCredential}
     * @throws UnsupportedCredentialTypeException should never happen as we have only supported types and algorithms
     */
    protected static PasswordCredential createCredentialFromPassword(char[] password) throws UnsupportedCredentialTypeException {
        try {
            PasswordFactory passwordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
            return new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec(password)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedCredentialTypeException(e);
        }
    }

    protected static void storeSecret(CredentialStore credentialStore, String alias, String secretValue) throws CredentialStoreException {
        char[] secret = secretValue != null ? secretValue.toCharArray() : new char[0];
        storeCredential(credentialStore, alias, createCredentialFromPassword(secret));
    }

    protected static void storeSecretKey(CredentialStore credentialStore, String alias, SecretKey secretKey) throws CredentialStoreException {
        storeCredential(credentialStore, alias, new SecretKeyCredential(secretKey));
    }

    protected static void storeCredential(CredentialStore credentialStore, String alias, Credential credential) throws CredentialStoreException {
        credentialStore.store(alias, credential);
        try {
            credentialStore.flush();
        } catch (CredentialStoreException e) {
            // operation fails, remove the entry from the store, to avoid an inconsistency between
            // the store on the FS and in the memory
            credentialStore.remove(alias, PasswordCredential.class);
            throw e;
        }
    }

    protected static String dumpCause(Throwable e) {
        StringBuffer sb = new StringBuffer().append(e.getLocalizedMessage());
        Throwable c = e.getCause();
        int depth = 0;
        while(c != null && depth++ < 10) {
            sb.append("->").append(c.getLocalizedMessage());
            c = c.getCause() == c ? null : c.getCause();
        }
        return sb.toString();
    }

    protected abstract static class AbstractCredentialStoreDoohickey extends ElytronDoohickey<CredentialStore> {

        protected AbstractCredentialStoreDoohickey(PathAddress resourceAddress) {
            super(resourceAddress);
        }

        protected abstract void reload(OperationContext context) throws GeneralSecurityException, OperationFailedException;

    }

    protected class CredentialStoreRuntimeHandler extends ElytronRuntimeOnlyHandler {

        private final Map<String, CredentialStoreRuntimeOperation> definedOperations;

        protected CredentialStoreRuntimeHandler(final Map<String, CredentialStoreRuntimeOperation> definedOperations) {
            this.definedOperations = definedOperations;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String operationName = operation.require(ModelDescriptionConstants.OP).asString();
            CredentialStoreRuntimeOperation operationMethod = definedOperations.get(operationName);
            if (operationMethod == null) {
                throw ROOT_LOGGER.invalidOperationName(operationName, getExpectedOperationNames());
            }

            CredentialStore credentialStore = getCredentialStore(context);
            operationMethod.handle(context, operation, credentialStore);
        }

        private String[] getExpectedOperationNames() {
            return definedOperations.keySet().toArray(new String[definedOperations.size()]);
        }

        protected CredentialStore getCredentialStore(OperationContext context) throws OperationFailedException {
            final ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> credentialStoreApi = context
                    .getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, context.getCurrentAddressValue(), ExceptionFunction.class);

            return credentialStoreApi.apply(context);
        }

    }

    static class CredentialStoreReloadHandler extends ElytronRuntimeOnlyHandler {

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            final ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> credentialStoreApi = context
                    .getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, context.getCurrentAddressValue(), ExceptionFunction.class);

            AbstractCredentialStoreDoohickey doohickey = (AbstractCredentialStoreDoohickey) credentialStoreApi;

            try {
                doohickey.reload(context);
            } catch (GeneralSecurityException e) {
                throw ROOT_LOGGER.unableToReloadCredentialStore(e);
            }

        }

    }

    @FunctionalInterface
    interface CredentialStoreRuntimeOperation {

        void handle(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException;

    }
}
