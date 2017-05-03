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

import static org.wildfly.extension.elytron.CredentialStoreResourceDefinition.CASE_SENSITIVE;
import static org.wildfly.extension.elytron.CredentialStoreResourceDefinition.CREDENTIAL_STORE_UTIL;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.as.controller.AbstractWriteAttributeHandler;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.jboss.msc.service.ServiceNotFoundException;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.UnsupportedCredentialTypeException;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

/**
 * A {@link ResourceDefinition} for an entry stored in credential store.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
class CredentialStoreAliasDefinition extends SimpleResourceDefinition {

    private static final Class<?>[] SUPPORTED_CREDENTIAL_TYPES = {
            PasswordCredential.class
    };

    static final SimpleAttributeDefinition ENTRY_TYPE;

    static {
        List<String> entryTypes = Stream.of(SUPPORTED_CREDENTIAL_TYPES).map(Class::getCanonicalName)
                .collect(Collectors.toList());
        ENTRY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENTRY_TYPE, ModelType.STRING, true)
                .setStorageRuntime()
                .setAllowedValues(entryTypes.toArray(new String[entryTypes.size()]))
                .build();
    }

    static final StandardResourceDescriptionResolver RESOURCE_DESCRIPTION_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CREDENTIAL_STORE, ElytronDescriptionConstants.ALIAS);

    static final SimpleAttributeDefinition SECRET_VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECRET_VALUE, ModelType.STRING, true)
            .setStorageRuntime()
            .setMinSize(0)
            .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] {SECRET_VALUE, ENTRY_TYPE};

    private static final SimpleOperationDefinition ADD_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD,
            RESOURCE_DESCRIPTION_RESOLVER)
            .setParameters(SECRET_VALUE, ENTRY_TYPE)
            .build();
    private static final AddHandler ADD_HANDLER = new AddHandler();

    CredentialStoreAliasDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.ALIAS), RESOURCE_DESCRIPTION_RESOLVER)
                .setRemoveHandler(new RemoveHandler())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRuntime()
        );
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        // We needed a custom add operation so we could specify the parameters.
        resourceRegistration.registerOperationHandler(ADD_DEFINITION, ADD_HANDLER);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(SECRET_VALUE, null, new WriteSecretAttributeHandler());
        resourceRegistration.registerReadOnlyAttribute(ENTRY_TYPE, null);
    }

    private static CredentialStore getCredentialStore(ModelNode operation, OperationContext context) throws IllegalArgumentException, IllegalStateException, ServiceNotFoundException, UnsupportedOperationException {
            ServiceName credentialStoreServiceName = CREDENTIAL_STORE_UTIL.serviceName(operation);
            @SuppressWarnings("unchecked")
            ServiceController<CredentialStore> serviceContainer = (ServiceController<CredentialStore>) context.getServiceRegistry(false).getRequiredService(credentialStoreServiceName);
            CredentialStore credentialStore = ((CredentialStoreService) serviceContainer.getService()).getValue();
            return credentialStore;
        }

    private static void storeSecret(CredentialStore credentialStore, String alias, String secretValue) throws CredentialStoreException {
        char[] secret = secretValue != null ? secretValue.toCharArray() : new char[0];
        credentialStore.store(alias, createCredentialFromPassword(secret));
        try {
            credentialStore.flush();
        } catch (CredentialStoreException e) {
            // operation fails, remove the entry from the store, to avoid an inconsistency between
            // the store on the FS and in the memory
            credentialStore.remove(alias, PasswordCredential.class);
            throw e;
        }
    }

    private static class AddHandler extends BaseAddHandler {

        AddHandler() {
            super(CONFIG_ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            String alias = context.getCurrentAddressValue();
            String secretValue = asStringIfDefined(context, SECRET_VALUE, resource.getModel());
            String entryType = asStringIfDefined(context, ENTRY_TYPE, resource.getModel());
            CredentialStore credentialStore = getCredentialStore(operation, context);
            try {
                if (entryType == null || entryType.equals(PasswordCredential.class.getCanonicalName())) {
                    if (credentialStore.exists(alias, PasswordCredential.class)) {
                        throw ROOT_LOGGER.credentialAlreadyExists(alias, PasswordCredential.class.getName());
                    }
                    storeSecret(credentialStore, alias, secretValue);
                } else {
                    String credentialStoreName = CredentialStoreResourceDefinition.credentialStoreName(operation);
                    throw ROOT_LOGGER.credentialStoreEntryTypeNotSupported(credentialStoreName, entryType);
                }
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, e.getLocalizedMessage());
            }
        }

        /**
         * {@inheritDoc
         *
         * @param context
         * @param operation
         */
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if (!CASE_SENSITIVE.resolveModelAttribute(context, context.readResourceFromRoot(context.getCurrentAddress().getParent(), false).getModel()).asBoolean()) {
                String aliasName = context.getCurrentAddressValue();
                if (!aliasName.equals(aliasName.toLowerCase(Locale.ROOT))) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.invalidAliasName(aliasName, context.getCurrentAddress().getParent().getLastElement().getValue());
                }
            }
            super.execute(context, operation);
        }
    }

    private static class RemoveHandler extends CredentialStoreResourceDefinition.CredentialStoreRuntimeOnlyHandler {

        RemoveHandler() {
            super(true, true);
        }

        @Override
        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, CredentialStoreService credentialStoreService) throws OperationFailedException {
            try {
                CredentialStore credentialStore = credentialStoreService.getValue();
                String currentAddress = context.getCurrentAddressValue();
                PasswordCredential retrieved = credentialStore.retrieve(currentAddress, PasswordCredential.class);
                credentialStore.remove(currentAddress, PasswordCredential.class);
                try {
                    credentialStore.flush();
                } catch (CredentialStoreException e) {
                    // the operation fails, return removed entry back to the store to avoid an inconsistency
                    // between the store on the FS and in the memory
                    credentialStore.store(currentAddress, retrieved);
                    throw e;
                }
            } catch (CredentialStoreException e) {
                throw new OperationFailedException(e);
            }
        }

    }

private static class WriteSecretAttributeHandler extends ElytronWriteAttributeHandler<String> {

        WriteSecretAttributeHandler() {
            super(SECRET_VALUE);
        }

        /**
         * Hook to allow subclasses to make runtime changes to effect the attribute value change.
         *
         * @param context        the context of the operation
         * @param operation      the operation
         * @param attributeName  the name of the attribute being modified
         * @param resolvedValue  the new value for the attribute, after {@link ModelNode#resolve()} has been called on it
         * @param currentValue   the existing value for the attribute
         * @param handbackHolder holder for an arbitrary object to pass to
         *                       {@link #revertUpdateToRuntime(OperationContext, ModelNode, String, ModelNode, ModelNode, Object)} if
         *                       the operation needs to be rolled back
         * @return {@code true} if the server requires reload to effect the attribute
         * value change; {@code false} if not
         */
        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, AbstractWriteAttributeHandler.HandbackHolder<String> handbackHolder) throws OperationFailedException {
            String alias = context.getCurrentAddressValue();
            Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
            String secretValue = resolvedValue.asString();
            String entryType = asStringIfDefined(context, ENTRY_TYPE, resource.getModel());
            CredentialStore credentialStore = getCredentialStore(operation, context);
            try {
                if (entryType == null || ClearPassword.ALGORITHM_CLEAR.equals(entryType)) {
                    storeSecret(credentialStore, alias, secretValue);
                    return false;
                } else {
                    String credentialStoreName = CredentialStoreResourceDefinition.credentialStoreName(operation);
                    throw ROOT_LOGGER.credentialStoreEntryTypeNotSupported(credentialStoreName, entryType);
                }
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, e.getLocalizedMessage());
            }
        }

        /**
         * Hook to allow subclasses to revert runtime changes made in
         * {@link #applyUpdateToRuntime(OperationContext, ModelNode, String, ModelNode, ModelNode, HandbackHolder)}.
         *
         * @param context        the context of the operation
         * @param operation      the operation
         * @param attributeName  the name of the attribute being modified
         * @param valueToRestore the previous value for the attribute, before this operation was executed
         * @param valueToRevert  the new value for the attribute that should be reverted
         * @param handback       an object, if any, passed in to the {@code handbackHolder} by the {@code applyUpdateToRuntime}
         */
        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, String handback) throws OperationFailedException {
            String alias = context.getCurrentAddressValue();
            Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
            String secretValue = valueToRestore.asString();
            String entryType = asStringIfDefined(context, ENTRY_TYPE, resource.getModel());
            CredentialStore credentialStore = getCredentialStore(operation, context);
            try {
                if (entryType == null || ClearPassword.ALGORITHM_CLEAR.equals(entryType)) {
                    storeSecret(credentialStore, alias, secretValue);
                }
            } catch (CredentialStoreException e) {
                ROOT_LOGGER.error(ROOT_LOGGER.unableToCompleteOperation(e, e.getLocalizedMessage()), e);
            }
        }
    }

    /**
     * Convert {@code char[]} password to {@code PasswordCredential}
     * @param password to convert
     * @return new {@code PasswordCredential}
     * @throws UnsupportedCredentialTypeException should never happen as we have only supported types and algorithms
     */
    static PasswordCredential createCredentialFromPassword(char[] password) throws UnsupportedCredentialTypeException {
        try {
            PasswordFactory passwordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
            return new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec(password)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnsupportedCredentialTypeException(e);
        }
    }

}
