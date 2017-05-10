/*
 * JBoss, Home of Professional Open Source.
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

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ServiceStateDefinition.STATE;
import static org.wildfly.extension.elytron.ServiceStateDefinition.populateResponse;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.naming.InvalidNameException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.capabilities._private.DirContextSupplier;
import org.wildfly.security.keystore.LdapKeyStore;

/**
 * A {@link ResourceDefinition} for a single {@link LdapKeyStore}.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
final class LdapKeyStoreDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<KeyStore> LDAP_KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronDescriptionConstants.LDAP_KEY_STORE, KeyStore.class);

    static final SimpleAttributeDefinition DIR_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIR_CONTEXT, ModelType.STRING, false)
            .setAllowExpression(false)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilityReference(DIR_CONTEXT_CAPABILITY, KEY_STORE_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition SEARCH_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_PATH, ModelType.STRING, false)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition SEARCH_RECURSIVE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_RECURSIVE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition SEARCH_TIME_LIMIT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_TIME_LIMIT, ModelType.INT, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition FILTER_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_ALIAS, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition FILTER_CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_CERTIFICATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition FILTER_ITERATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_ITERATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static class NewItemTemplateObjectDefinition {

        static final SimpleAttributeDefinition NEW_ITEM_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEW_ITEM_PATH, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setRequires(ElytronDescriptionConstants.NEW_ITEM_RDN, ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES)
                .build();

        static final SimpleAttributeDefinition NEW_ITEM_RDN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEW_ITEM_RDN, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setRequires(ElytronDescriptionConstants.NEW_ITEM_PATH, ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES)
                .build();

        static final ObjectListAttributeDefinition NEW_ITEM_ATTRIBUTES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.NEW_ITEM_ATTRIBUTES, NewItemTemplateAttributeObjectDefinition.OBJECT_DEFINITION)
                .setRequired(true)
                .setMinSize(1)
                .setAllowDuplicates(true)
                .setRequires(ElytronDescriptionConstants.NEW_ITEM_PATH, ElytronDescriptionConstants.NEW_ITEM_RDN)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { NEW_ITEM_PATH, NEW_ITEM_RDN, NEW_ITEM_ATTRIBUTES };

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.NEW_ITEM_TEMPLATE, ATTRIBUTES)
                .build();
    }

    static final SimpleAttributeDefinition ALIAS_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition KEY_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition KEY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.LDAP_KEY_STORE);

    static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIZE, ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] {
            DIR_CONTEXT,
            SEARCH_PATH, SEARCH_RECURSIVE, SEARCH_TIME_LIMIT, FILTER_ALIAS, FILTER_CERTIFICATE, FILTER_ITERATE,
            NewItemTemplateObjectDefinition.OBJECT_DEFINITION,
            ALIAS_ATTRIBUTE,
            CERTIFICATE_ATTRIBUTE, CERTIFICATE_TYPE,
            CERTIFICATE_CHAIN_ATTRIBUTE, CERTIFICATE_CHAIN_ENCODING,
            KEY_ATTRIBUTE, KEY_TYPE
    };

    private static final KeyStoreAddHandler ADD = new KeyStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, KEY_STORE_RUNTIME_CAPABILITY);
    private static final WriteAttributeHandler WRITE = new WriteAttributeHandler();

    LdapKeyStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.LDAP_KEY_STORE), RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }

        resourceRegistration.registerReadOnlyAttribute(STATE, new ElytronRuntimeOnlyHandler() {
            @Override
            protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                ServiceName keyStoreName = LDAP_KEY_STORE_UTIL.serviceName(operation);
                ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                populateResponse(context.getResult(), serviceController);
            }
        });

        resourceRegistration.registerReadOnlyAttribute(SIZE, new LdapKeyStoreRuntimeOnlyHandler(false) {
            @Override
            protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, LdapKeyStoreService keyStoreService) throws OperationFailedException {
                try {
                    result.set(keyStoreService.getValue().size());
                } catch (KeyStoreException e) {
                    throw ROOT_LOGGER.unableToAccessKeyStore(e);
                }
            }
        });
    }

    private static class KeyStoreAddHandler extends BaseAddHandler {

        private KeyStoreAddHandler() {
            super(KEY_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();

            String dirContextName = asStringIfDefined(context, DIR_CONTEXT, model);
            String searchPath = asStringIfDefined(context, SEARCH_PATH, model);
            String filterAlias = asStringIfDefined(context, FILTER_ALIAS, model);
            String filterCertificate = asStringIfDefined(context, FILTER_CERTIFICATE, model);
            String filterIterate = asStringIfDefined(context, FILTER_ITERATE, model);
            String aliasAttribute = asStringIfDefined(context, ALIAS_ATTRIBUTE, model);
            String certificateAttribute = asStringIfDefined(context, CERTIFICATE_ATTRIBUTE, model);
            String certificateType = asStringIfDefined(context, CERTIFICATE_TYPE, model);
            String certificateChainAttribute = asStringIfDefined(context, CERTIFICATE_CHAIN_ATTRIBUTE, model);
            String certificateChainEncoding = asStringIfDefined(context, CERTIFICATE_CHAIN_ENCODING, model);
            String keyAttribute = asStringIfDefined(context, KEY_ATTRIBUTE, model);
            String keyType = asStringIfDefined(context, KEY_TYPE, model);
            LdapName createPathLdapName = null;
            String createRdn = null;
            Attributes createAttributes = null;

            ModelNode newNode = NewItemTemplateObjectDefinition.OBJECT_DEFINITION.resolveModelAttribute(context, model);
            if (newNode.isDefined()) {

                String createPath = asStringIfDefined(context, NewItemTemplateObjectDefinition.NEW_ITEM_PATH, newNode);
                createRdn = asStringIfDefined(context, NewItemTemplateObjectDefinition.NEW_ITEM_RDN, newNode);
                if (createPath != null) {
                    try {
                        createPathLdapName = new LdapName(createPath);
                    } catch (InvalidNameException e) {
                        throw new OperationFailedException(e);
                    }
                }

                ModelNode createAttributesNode = NewItemTemplateObjectDefinition.NEW_ITEM_ATTRIBUTES.resolveModelAttribute(context, newNode);
                if (createAttributesNode.isDefined()) {
                    createAttributes = new BasicAttributes(true);
                    for (ModelNode attributeNode : createAttributesNode.asList()) {
                        ModelNode nameNode = NewItemTemplateAttributeObjectDefinition.NAME.resolveModelAttribute(context, attributeNode);
                        ModelNode valuesNode = NewItemTemplateAttributeObjectDefinition.VALUE.resolveModelAttribute(context, attributeNode);

                        if (valuesNode.getType() == ModelType.LIST) {
                            BasicAttribute listAttribute = new BasicAttribute(nameNode.asString());
                            for (ModelNode valueNode : valuesNode.asList()) {
                                listAttribute.add(valueNode.asString());
                            }
                            createAttributes.put(listAttribute);
                        } else {
                            createAttributes.put(new BasicAttribute(nameNode.asString(), valuesNode.asString()));
                        }
                    }
                }
            }

            LdapKeyStoreService keyStoreService = new LdapKeyStoreService(searchPath, filterAlias, filterCertificate,
                    filterIterate, createPathLdapName, createRdn, createAttributes, aliasAttribute,
                    certificateAttribute, certificateType, certificateChainAttribute, certificateChainEncoding,
                    keyAttribute, keyType);

            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, keyStoreService).setInitialMode(Mode.ACTIVE);

            String dirContextCapability = RuntimeCapability.buildDynamicCapabilityName(DIR_CONTEXT_CAPABILITY, dirContextName);
            ServiceName dirContextServiceName = context.getCapabilityServiceName(dirContextCapability, DirContextSupplier.class);
            serviceBuilder.addDependency(dirContextServiceName, DirContextSupplier.class, keyStoreService.getDirContextSupplierInjector());

            commonDependencies(serviceBuilder).install();
        }
    }

    private static class WriteAttributeHandler extends ElytronRestartParentWriteAttributeHandler {

        WriteAttributeHandler() {
            super(ElytronDescriptionConstants.LDAP_KEY_STORE, CONFIG_ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName(KeyStore.class);
        }
    }

    static class NewItemTemplateAttributeObjectDefinition {
        static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final StringListAttributeDefinition VALUE = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { NAME, VALUE };

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE, ATTRIBUTES)
                .setRequired(false)
                .build();
    }

    abstract static class LdapKeyStoreRuntimeOnlyHandler extends ElytronRuntimeOnlyHandler {

        private final boolean serviceMustBeUp;
        private final boolean writeAccess;

        LdapKeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp, final boolean writeAccess) {
            this.serviceMustBeUp = serviceMustBeUp;
            this.writeAccess = writeAccess;
        }

        LdapKeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp) {
            this(serviceMustBeUp, false);
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName ldapKeyStoreName = LDAP_KEY_STORE_UTIL.serviceName(operation);

            ServiceController<KeyStore> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), ldapKeyStoreName, KeyStore.class);
            ServiceController.State serviceState;
            if ((serviceState = serviceContainer.getState()) != ServiceController.State.UP) {
                if (serviceMustBeUp) {
                    throw ROOT_LOGGER.requiredServiceNotUp(ldapKeyStoreName, serviceState);
                }
                return;
            }

            performRuntime(context.getResult(), context, operation, (LdapKeyStoreService) serviceContainer.getService());
        }

        protected void performRuntime(ModelNode result, ModelNode operation,  LdapKeyStoreService keyStoreService) throws OperationFailedException {}

        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation,  LdapKeyStoreService keyStoreService) throws OperationFailedException {
            performRuntime(result, operation, keyStoreService);
        }
    }

}
