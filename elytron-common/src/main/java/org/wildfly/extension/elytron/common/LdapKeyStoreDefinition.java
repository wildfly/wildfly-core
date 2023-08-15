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

package org.wildfly.extension.elytron.common;

import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.commonRequirements;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.getRequiredService;
import static org.wildfly.extension.elytron.common.ElytronCommonDefinitions.isServerOrHostController;

import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.naming.InvalidNameException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
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
import org.wildfly.extension.elytron.common.capabilities._private.DirContextSupplier;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;
import org.wildfly.security.keystore.LdapKeyStore;

/**
 * A {@link ResourceDefinition} for a single {@link LdapKeyStore}.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public final class LdapKeyStoreDefinition extends SimpleResourceDefinition {

    public static final ServiceUtil<KeyStore> LDAP_KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronCommonConstants.LDAP_KEY_STORE, KeyStore.class);

    public static final SimpleAttributeDefinition DIR_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.DIR_CONTEXT, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(DIR_CONTEXT_CAPABILITY, KEY_STORE_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition SEARCH_PATH = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SEARCH_PATH, ModelType.STRING, false)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setXmlName("path")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition SEARCH_RECURSIVE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SEARCH_RECURSIVE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setXmlName("recursive")
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(ModelNode.TRUE)
            .build();

    public static final SimpleAttributeDefinition SEARCH_TIME_LIMIT = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SEARCH_TIME_LIMIT, ModelType.INT, true)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setXmlName("time-limit")
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode(10000))
            .build();

    public static final SimpleAttributeDefinition FILTER_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.FILTER_ALIAS, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition FILTER_CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.FILTER_CERTIFICATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition FILTER_ITERATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.FILTER_ITERATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static class NewItemTemplateObjectDefinition {

        public static final SimpleAttributeDefinition NEW_ITEM_PATH = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.NEW_ITEM_PATH, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setRequires(ElytronCommonConstants.NEW_ITEM_RDN, ElytronCommonConstants.NEW_ITEM_ATTRIBUTES)
                .build();

        public static final SimpleAttributeDefinition NEW_ITEM_RDN = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.NEW_ITEM_RDN, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setRequires(ElytronCommonConstants.NEW_ITEM_PATH, ElytronCommonConstants.NEW_ITEM_ATTRIBUTES)
                .build();

        public static final ObjectListAttributeDefinition NEW_ITEM_ATTRIBUTES = new ObjectListAttributeDefinition.Builder(ElytronCommonConstants.NEW_ITEM_ATTRIBUTES, NewItemTemplateAttributeObjectDefinition.OBJECT_DEFINITION)
                .setRequired(true)
                .setMinSize(1)
                .setAllowDuplicates(true)
                .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
                .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
                .setRequires(ElytronCommonConstants.NEW_ITEM_PATH, ElytronCommonConstants.NEW_ITEM_RDN)
                .build();

        public static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { NEW_ITEM_PATH, NEW_ITEM_RDN, NEW_ITEM_ATTRIBUTES };

        public static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronCommonConstants.NEW_ITEM_TEMPLATE, ATTRIBUTES)
                .setRestartAllServices()
                .build();
    }

    public static final SimpleAttributeDefinition ALIAS_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("cn"))
            .build();

    public static final SimpleAttributeDefinition CERTIFICATE_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CERTIFICATE_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("usercertificate"))
            .build();

    public static final SimpleAttributeDefinition CERTIFICATE_TYPE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CERTIFICATE_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("X.509"))
            .build();

    public static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CERTIFICATE_CHAIN_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("userSMIMECertificate"))
            .build();

    public static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.CERTIFICATE_CHAIN_ENCODING, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("PKCS7"))
            .build();

    public static final SimpleAttributeDefinition KEY_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("userPKCS12"))
            .build();

    public static final SimpleAttributeDefinition KEY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronCommonConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("PKCS12"))
            .build();

    private static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.SIZE, ModelType.INT)
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

    private static final ElytronReloadRequiredWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(CONFIG_ATTRIBUTES);

    public static LdapKeyStoreDefinition configure(final Class<?> extensionClass) {
        StandardResourceDescriptionResolver ldapKSResourceResolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass,
                ElytronCommonConstants.LDAP_KEY_STORE);

        KeyStoreAddHandler ldapKSAddHandler = new KeyStoreAddHandler(extensionClass);
        TrivialCapabilityServiceRemoveHandler ldapKSRemoveHandler = new TrivialCapabilityServiceRemoveHandler(extensionClass, ldapKSAddHandler, KEY_STORE_RUNTIME_CAPABILITY);

        return new LdapKeyStoreDefinition(ldapKSResourceResolver, ldapKSAddHandler, ldapKSRemoveHandler);
    }

    private LdapKeyStoreDefinition(StandardResourceDescriptionResolver ldapKSResourceResolver, KeyStoreAddHandler ldapKSAddHandler,
                                   ElytronCommonTrivialCapabilityServiceRemoveHandler ldapKSRemoveHandler) {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.LDAP_KEY_STORE), ldapKSResourceResolver)
                .setAddHandler(ldapKSAddHandler)
                .setRemoveHandler(ldapKSRemoveHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }

        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(ServiceStateDefinition.STATE, new ElytronRuntimeOnlyHandler() {
                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceName keyStoreName = LDAP_KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    ServiceStateDefinition.populateResponse(context.getResult(), serviceController);
                }
            });

            resourceRegistration.registerReadOnlyAttribute(SIZE, new LdapKeyStoreRuntimeOnlyHandler(false) {
                @Override
                protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, LdapKeyStoreService keyStoreService) throws OperationFailedException {
                    try {
                        result.set(keyStoreService.getValue().size());
                    } catch (KeyStoreException e) {
                        throw ElytronCommonMessages.ROOT_LOGGER.unableToAccessKeyStore(e);
                    }
                }
            });
        }
    }

    private static class KeyStoreAddHandler extends ElytronCommonBaseAddHandler {
        private final Class<?> extensionClass;

        private KeyStoreAddHandler(final Class<?> extensionClass) {
            super(KEY_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
            this.extensionClass = extensionClass;
        }

        @Override
        protected String getSubsystemCapability() {
            return ElytronCommonDefinitions.getSubsystemCapability(extensionClass);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();

            String dirContextName = DIR_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
            String searchPath = SEARCH_PATH.resolveModelAttribute(context, model).asStringOrNull();
            String filterAlias = FILTER_ALIAS.resolveModelAttribute(context, model).asStringOrNull();
            String filterCertificate = FILTER_CERTIFICATE.resolveModelAttribute(context, model).asStringOrNull();
            String filterIterate = FILTER_ITERATE.resolveModelAttribute(context, model).asStringOrNull();
            String aliasAttribute = ALIAS_ATTRIBUTE.resolveModelAttribute(context, model).asStringOrNull();
            String certificateAttribute = CERTIFICATE_ATTRIBUTE.resolveModelAttribute(context, model).asStringOrNull();
            String certificateType = CERTIFICATE_TYPE.resolveModelAttribute(context, model).asStringOrNull();
            String certificateChainAttribute = CERTIFICATE_CHAIN_ATTRIBUTE.resolveModelAttribute(context, model).asStringOrNull();
            String certificateChainEncoding = CERTIFICATE_CHAIN_ENCODING.resolveModelAttribute(context, model).asStringOrNull();
            String keyAttribute = KEY_ATTRIBUTE.resolveModelAttribute(context, model).asStringOrNull();
            String keyType = KEY_TYPE.resolveModelAttribute(context, model).asStringOrNull();
            LdapName createPathLdapName = null;
            String createRdn = null;
            Attributes createAttributes = null;

            if (filterAlias == null) filterAlias = "(" + aliasAttribute + "={0})";
            if (filterCertificate == null) filterCertificate = "(" + certificateAttribute + "={0})";
            if (filterIterate == null) filterIterate = "(" + aliasAttribute + "=*)";

            ModelNode newNode = NewItemTemplateObjectDefinition.OBJECT_DEFINITION.resolveModelAttribute(context, model);
            if (newNode.isDefined()) {

                String createPath = NewItemTemplateObjectDefinition.NEW_ITEM_PATH.resolveModelAttribute(context, newNode).asString();
                createRdn = NewItemTemplateObjectDefinition.NEW_ITEM_RDN.resolveModelAttribute(context, newNode).asString();
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

            commonRequirements(extensionClass, serviceBuilder).install();
        }
    }

    public static class NewItemTemplateAttributeObjectDefinition {
        public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        public static final StringListAttributeDefinition VALUE = new StringListAttributeDefinition.Builder(ElytronCommonConstants.VALUE)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        public static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { NAME, VALUE };

        public static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronCommonConstants.ATTRIBUTE, ATTRIBUTES)
                .setRequired(false)
                .setXmlName("attribute")
                .build();
    }

    protected abstract static class LdapKeyStoreRuntimeOnlyHandler extends ElytronRuntimeOnlyHandler {

        private final boolean serviceMustBeUp;
        private final boolean writeAccess;

        protected LdapKeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp, final boolean writeAccess) {
            this.serviceMustBeUp = serviceMustBeUp;
            this.writeAccess = writeAccess;
        }

        protected LdapKeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp) {
            this(serviceMustBeUp, false);
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName ldapKeyStoreName = LDAP_KEY_STORE_UTIL.serviceName(operation);

            ServiceController<KeyStore> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), ldapKeyStoreName, KeyStore.class);
            ServiceController.State serviceState;
            if ((serviceState = serviceContainer.getState()) != ServiceController.State.UP) {
                if (serviceMustBeUp) {
                    throw ElytronCommonMessages.ROOT_LOGGER.requiredServiceNotUp(ldapKeyStoreName, serviceState);
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
