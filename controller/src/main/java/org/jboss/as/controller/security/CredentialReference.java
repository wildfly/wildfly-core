/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Set;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.value.InjectedValue;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.CredentialStoreSpi;
import org.wildfly.security.credential.store.UnsupportedCredentialTypeException;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

/**
 * Class unifying access to credentials defined through {@link org.wildfly.security.credential.store.CredentialStore}
 * or holding simply {@code char[]} as a secret.
 *
 * It defines credential reference attribute that other subsystems can use to reference external credentials of various
 * types.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
public final class CredentialReference implements Destroyable {

    /**
     * Definition of id used in model
     */
    public static final String CREDENTIAL_REFERENCE = "credential-reference";
    /**
     * Definition of id used in model
     */
    public static final String STORE = "store";
    /**
     * Definition of id used in model
     */
    public static final String ALIAS = "alias";
    /**
     * Definition of id used in model
     */
    public static final String TYPE = "type";
    /**
     * Definition of id used in model
     */
    public static final String CLEAR_TEXT = "clear-text";

    static final SimpleAttributeDefinition credentialStoreAttribute;
    static final SimpleAttributeDefinition credentialAliasAttribute;
    static final SimpleAttributeDefinition credentialTypeAttribute;
    static final SimpleAttributeDefinition clearTextAttribute;

    static ObjectTypeAttributeDefinition credentialReferenceAttributeDefinition;

    private final String credentialStoreName;
    private final String alias;
    private final String credentialType;
    private char[] secret;

    static {
        credentialStoreAttribute = new SimpleAttributeDefinitionBuilder(STORE, ModelType.STRING, true)
                .setXmlName(STORE)
                .build();
        credentialAliasAttribute = new SimpleAttributeDefinitionBuilder(ALIAS, ModelType.STRING, true)
                .setXmlName(ALIAS)
                .build();
        credentialTypeAttribute = new SimpleAttributeDefinitionBuilder(TYPE, ModelType.STRING, true)
                .setXmlName(TYPE)
                .build();
        clearTextAttribute = new SimpleAttributeDefinitionBuilder(CLEAR_TEXT, ModelType.STRING, true)
                .setXmlName(CLEAR_TEXT)
                .build();
        credentialReferenceAttributeDefinition = new ObjectTypeAttributeDefinition.Builder(CREDENTIAL_REFERENCE, credentialStoreAttribute, credentialAliasAttribute, credentialTypeAttribute, clearTextAttribute)
                .setXmlName(CREDENTIAL_REFERENCE)
                .build();
    }

    private CredentialReference(String credentialStoreName, String alias, String credentialType, char[] secret) {
        this.credentialStoreName = credentialStoreName;
        this.alias = alias;
        this.credentialType = credentialType;
        if (secret != null) {
            this.secret = secret.clone();
        } else {
            this.secret = null;
        }
    }

    /**
     * Get the credential store name part of this reference.
     * @return credential store name
     */
    public String getCredentialStoreName() {
        return credentialStoreName;
    }

    /**
     * Get the credential alias which denotes credential stored inside named credential store.
     * @return alias of the referenced credential
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Get credential type which narrows selection of the credential stored under the alias in the credential store.
     * @return credential type (class name of desired credential type)
     */
    public String getCredentialType() {
        return credentialType;
    }

    /**
     * Get the secret stored as clear text in this reference.
     * @return secret value as clear text
     */
    public char[] getSecret() {
        return secret;
    }


    /**
     * Destroy this {@code Object}.
     * <p>
     * <p> Sensitive information associated with this {@code Object}
     * is destroyed or cleared.  Subsequent calls to certain methods
     * on this {@code Object} will result in an
     * {@code IllegalStateException} being thrown.
     * <p>
     * <p>
     * The default implementation throws {@code DestroyFailedException}.
     *
     * @throws DestroyFailedException if the destroy operation fails. <p>
     * @throws SecurityException      if the caller does not have permission
     *                                to destroy this {@code Object}.
     */
    @Override
    public void destroy() throws DestroyFailedException {
        if (secret != null) {
            for (int i = 0; i < secret.length; i++) {
                secret[i] = 0;
            }
            secret = null;
        }
    }

    /**
     * Determine if this {@code Object} has been destroyed.
     * <p>
     * <p>
     * The default implementation returns false.
     *
     * @return true if this {@code Object} has been destroyed,
     * false otherwise.
     */
    @Override
    public boolean isDestroyed() {
        return secret == null;
    }

    // factory static methods

    /**
     * Method to create new {@link CredentialReference} based on {@link #secret} attribute only.
     * @param secret to reference
     * @return new {@link CredentialReference}
     */
    public static CredentialReference createCredentialReference(char[] secret) {
        return new CredentialReference(CredentialReference.class.getName(), null, null, secret);
    }

    /**
     * Method to create new {@link CredentialReference} based on params
     * @param credentialStoreName credential store name
     * @param alias denoting the credential
     * @param credentialType type of credential (can be {@code null})
     * @return new {@link CredentialReference}
     */
    public static CredentialReference createCredentialReference(String credentialStoreName, String alias, String credentialType) {
        return new CredentialReference(credentialStoreName, alias, credentialType, null);
    }

    // utility static methods

    /**
     * Returns new definition for credential reference attribute.
     *
     * @return credential reference attribute definition
     */
    public static ObjectTypeAttributeDefinition getAttributeDefinition() {
        return credentialReferenceAttributeDefinition;
    }

    /**
     * Utility method to return part of {@link ObjectTypeAttributeDefinition} for credential reference attribute.
     *
     * {@see CredentialReference#getAttributeDefinition}
     * @param context operational context
     * @param attributeDefinition attribute definition
     * @param model model
     * @param name name of part to return (supported names: {@link #STORE} {@link #ALIAS} {@link #TYPE}
     *    {@link #CLEAR_TEXT}
     * @return value of part as {@link String}
     * @throws OperationFailedException when something goes wrong
     */
    public static String credentialReferencePartAsStringIfDefined(OperationContext context, ObjectTypeAttributeDefinition attributeDefinition, ModelNode model, String name) throws OperationFailedException {
        ModelNode value = attributeDefinition.resolveModelAttribute(context, model);
        if (value.isDefined()) {
            ModelNode namedNode = value.get(name);
            if (namedNode != null && namedNode.isDefined()) {
                return namedNode.asString();
            }
            return null;
        }
        return null;
    }

    /**
     * Replace injection with new one referencing the same {@link org.wildfly.security.credential.store.CredentialStore} but
     * based of new values of {@link CredentialReference}
     * @param injectedCredentialStoreClient {@link InjectedValue} to replace the credential reference
     * @param credentialReference new credential reference
     * @throws ClassNotFoundException when credential reference holding credential type which cannot be resolved using current providers
     */
    public static void reinjectCredentialStoreClient(InjectedValue<CredentialStoreClient> injectedCredentialStoreClient,
            CredentialReference credentialReference) throws ClassNotFoundException {

        CredentialStoreClient originalCredentialStoreClient = injectedCredentialStoreClient.getOptionalValue();
        final CredentialStoreClient updatedCredentialStoreClient;
        if (originalCredentialStoreClient != null) {
            updatedCredentialStoreClient =
                    credentialReference.getCredentialType() != null
                            ?
                            new CredentialStoreClient(
                                    originalCredentialStoreClient.getCredentialStore(),
                                    credentialReference.getCredentialStoreName(),
                                    credentialReference.getAlias(),
                                    credentialReference.getCredentialType())
                            :
                            new CredentialStoreClient(
                                    originalCredentialStoreClient.getCredentialStore(),
                                    credentialReference.getCredentialStoreName(),
                                    credentialReference.getAlias());
        } else {
            final CredentialStore credentialStore = new ClearTextCredentialStore(credentialReference);
            updatedCredentialStoreClient = new CredentialStoreClient(credentialStore, CredentialReference.class.getName(), null);
        }
        injectedCredentialStoreClient.setValue(() -> updatedCredentialStoreClient);
    }

    /**
     * Marshall the value from {@code credentialReferenceModelNode} as an xml element into the given {@code writer}.
     * @param credentialReferenceModelNode he model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param writer stream writer to use for writing the attribute
     * @throws XMLStreamException if thrown by {@code writer}
     */
    public static void marshallAsElement(ModelNode credentialReferenceModelNode, XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(CredentialReference.CREDENTIAL_REFERENCE);
        if (credentialReferenceModelNode.hasDefined(clearTextAttribute.getName())) {
            clearTextAttribute.marshallAsAttribute(credentialReferenceModelNode, writer);
        } else {
            credentialStoreAttribute.marshallAsAttribute(credentialReferenceModelNode, writer);
            credentialAliasAttribute.marshallAsAttribute(credentialReferenceModelNode, writer);
            credentialTypeAttribute.marshallAsAttribute(credentialReferenceModelNode, writer);
        }
        writer.writeEndElement();

    }

    /**
     * Parse {@link CredentialReference} from {@code XMLStreamReader} and set addOperation.
     * @param addOperation to set
     * @param reader stream reader to parse
     * @throws XMLStreamException if thrown by {@code reader}
     */
    public static void readCredentialReference(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {
        AttributeParser.OBJECT_PARSER.parseElement(CredentialReference.getAttributeDefinition(), reader, addOperation);
    }

    static class ClearTextCredentialStore extends CredentialStore {
        private static String TYPE = "ClearTextCredentialStore";
        private CredentialReference credentialReference;

        ClearTextCredentialStore(CredentialReference credentialReference) {
            super(null, null, TYPE);
            this.credentialReference = credentialReference;
        }

        /**
         * Checks whether underlying credential store is initialized.
         *
         * @return {@code true} in case of initialization passed successfully, {@code false} otherwise.
         */
        @Override
        public boolean isInitialized() {
            return true;
        }

        /**
         * Check if credential store supports modification of actual store
         *
         * @return true in case of modification of store is supported
         */
        @Override
        public boolean isModifiable() {
            return false;
        }

        /**
         * Check whether credential store has an entry associated with the given credential alias of specified credential type.
         *
         * @param credentialAlias alias to check existence
         * @param credentialType  to check existence in the credential store
         * @return true in case key exist in store
         * @throws CredentialStoreException           when there is a problem with credential store
         * @throws UnsupportedCredentialTypeException when the credentialType is not supported
         */
        @Override
        public <C extends Credential> boolean exists(String credentialAlias, Class<C> credentialType) throws CredentialStoreException, UnsupportedCredentialTypeException {
            return false;
        }

        /**
         * Store credential to the store under the given alias. If given alias already contains specific credential type type the credential
         * replaces older one. <em>Note:</em> {@link CredentialStoreSpi} supports storing of multiple entries (credential types) per alias.
         * Each must be of different credential type.
         *
         * @param credentialAlias to store the credential to the store
         * @param credential      instance of {@link Credential} to store
         * @throws CredentialStoreException           when the credential cannot be stored
         * @throws UnsupportedCredentialTypeException when the credentialType is not supported
         */
        @Override
        public <C extends Credential> void store(String credentialAlias, C credential) throws CredentialStoreException, UnsupportedCredentialTypeException {
            throw new RuntimeException("method not implemented");
        }

        /**
         * Retrieve credential stored in the store under the key and of the credential type
         *
         * @param credentialAlias to find the credential in the store
         * @param credentialType  - credential type to retrieve from under the credentialAlias from the store
         * @return instance of {@link Credential} stored in the store
         * @throws CredentialStoreException           - if credentialAlias credentialType combination doesn't exist or credentialAlias cannot be retrieved
         * @throws UnsupportedCredentialTypeException when the credentialType is not supported
         */
        @Override
        public <C extends Credential> C retrieve(String credentialAlias, Class<C> credentialType) throws CredentialStoreException, UnsupportedCredentialTypeException {
            try {
                PasswordFactory passwordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
                return credentialType.cast(new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec(credentialReference.getSecret()))));
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new CredentialStoreException(e);
            }
        }

        /**
         * Remove the credentialType with from given alias from the store.
         *
         * @param credentialAlias alias to remove
         * @param credentialType  - credential type to be removed from under the credentialAlias from the store
         * @throws CredentialStoreException           - if credentialAlias credentialType combination doesn't exist or credentialAlias cannot be removed
         * @throws UnsupportedCredentialTypeException when the credentialType is not supported
         */
        @Override
        public <C extends Credential> void remove(String credentialAlias, Class<C> credentialType) throws CredentialStoreException, UnsupportedCredentialTypeException {
            throw new RuntimeException("method not implemented");
        }

        /**
         * Returns {@code Set<String>} stored in this store.
         *
         * @return {@code Set<String>} of all keys stored in this store
         * @throws UnsupportedOperationException when this method is not supported by the underlying credential store
         * @throws CredentialStoreException      if there is any problem with internal store
         */
        @Override
        public Set<String> getAliases() throws UnsupportedOperationException, CredentialStoreException {
            return Collections.emptySet();
        }
    }

}
