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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.wildfly.extension.elytron.CredentialStoreResourceDefinition.CREDENTIAL_STORE_UTIL;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
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

    static final String OTHER = "Other";
    private static final Class<?>[] SUPPORTED_CREDENTIAL_TYPES = {
            PasswordCredential.class
    };

    static final SimpleAttributeDefinition ENTRY_TYPE;

    static {
        List<String> entryTypes = Stream.of(SUPPORTED_CREDENTIAL_TYPES).map(Class::getName)
                .collect(Collectors.toList());
        entryTypes.add(OTHER);
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
        resourceRegistration.registerReadWriteAttribute(SECRET_VALUE, null, new WriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(ENTRY_TYPE, null, new WriteAttributeHandler());
    }

    static String alias(ModelNode operation) {
        String aliasName = null;
        PathAddress pa = PathAddress.pathAddress(operation.require(OP_ADDR));
        for (int i = pa.size() - 1; i > 0; i--) {
            PathElement pe = pa.getElement(i);
            if (ElytronDescriptionConstants.ALIAS.equals(pe.getKey())) {
                aliasName = pe.getValue();
                break;
            }
        }

        if (aliasName == null) {
            throw ROOT_LOGGER.operationAddressMissingKey(ElytronDescriptionConstants.ALIAS);
        }

        return aliasName.toLowerCase(Locale.ROOT);
    }

    private static void transformOperationAddress(final ModelNode operation) {
        Property alias = propertyAliasFromOperation(operation);
        if (alias != null)
        {
            String newAlias = alias.getValue().asString().toLowerCase(Locale.ROOT);
            alias.getValue().set(newAlias);
        }
    }

    private static Property propertyAliasFromOperation(final ModelNode operation) {
        ModelNode address = operation.get(ModelDescriptionConstants.OP_ADDR);
        List<Property> list = address.asPropertyList();
        Property alias = null;
        for (Property p: list) {
            if (ElytronDescriptionConstants.ALIAS.equals(p.getName())) {
                alias = p;
                break;
            }
        }
        return alias;
    }

    private static boolean sameAlias(final OperationContext context, final ModelNode operation) {
        PathElement contextAlias = context.getCurrentAddress().getLastElement();
        Property operationAlias = propertyAliasFromOperation(operation);
        boolean outcome = false;
        if (contextAlias != null && operationAlias != null)
        {
            outcome = contextAlias.getValue().equals(operationAlias.getValue().asString());
        } else {
            outcome = contextAlias == null && operationAlias == null ? true : false;
        }

        return  outcome;
    }

    private static class AddHandler extends BaseAddHandler {

        AddHandler() {
            super(CONFIG_ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            String alias = alias(operation);
            String secretValue = asStringIfDefined(context, SECRET_VALUE, resource.getModel());
            String entryType = asStringIfDefined(context, ENTRY_TYPE, resource.getModel());
            ServiceName credentialStoreServiceName = CREDENTIAL_STORE_UTIL.serviceName(operation);
            @SuppressWarnings("unchecked")
            ServiceController<CredentialStore> serviceContainer = (ServiceController<CredentialStore>) context.getServiceRegistry(true).getRequiredService(credentialStoreServiceName);
            CredentialStore credentialStore = ((CredentialStoreService) serviceContainer.getService()).getValue();
            try {
                if (entryType == null || ClearPassword.ALGORITHM_CLEAR.equals(entryType)) {
                    if (credentialStore.exists(alias, PasswordCredential.class)) {
                        throw ROOT_LOGGER.credentialAlreadyExists(alias, PasswordCredential.class.getName());
                    }
                    char[] secret = secretValue != null ? secretValue.toCharArray() : new char[0];
                    credentialStore.store(alias, createCredentialFromPassword(secret));
                    credentialStore.flush();
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
            transformOperationAddress(operation);
            super.execute(context, operation);
        }

        @Override
        protected Resource createResource(OperationContext context, ModelNode operation) {
            Resource resource = Resource.Factory.create(true);
            if (sameAlias(context, operation)) {
                context.addResource(PathAddress.EMPTY_ADDRESS, resource);
            }
            return resource;
        }

    }

    private static class RemoveHandler extends CredentialStoreResourceDefinition.CredentialStoreRuntimeOnlyHandler {

        RemoveHandler() {
            super(true, true);
        }

        @Override
        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, CredentialStoreService credentialStoreService) throws OperationFailedException {
            String alias = alias(operation);
            try {
                CredentialStore credentialStore = credentialStoreService.getValue();
                credentialStore.remove(alias, PasswordCredential.class);
                credentialStore.flush();
            } catch (CredentialStoreException e) {
                throw new OperationFailedException(e);
            }
        }

    }


    private static class WriteAttributeHandler extends ElytronWriteAttributeHandler<String> {

        WriteAttributeHandler() {
            super(CONFIG_ATTRIBUTES);
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
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<String> handbackHolder) throws OperationFailedException {
            return false;
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
