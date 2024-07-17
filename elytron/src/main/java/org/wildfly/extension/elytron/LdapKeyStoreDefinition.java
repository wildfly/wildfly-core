/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
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
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
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
            .setRestartAllServices()
            .setCapabilityReference(DIR_CONTEXT_CAPABILITY, KEY_STORE_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition SEARCH_PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_PATH, ModelType.STRING, false)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setXmlName("path")
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SEARCH_RECURSIVE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_RECURSIVE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setXmlName("recursive")
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(ModelNode.TRUE)
            .build();

    static final SimpleAttributeDefinition SEARCH_TIME_LIMIT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_TIME_LIMIT, ModelType.INT, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setXmlName("time-limit")
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode(10000))
            .build();

    static final SimpleAttributeDefinition FILTER_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_ALIAS, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FILTER_CERTIFICATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_CERTIFICATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FILTER_ITERATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_ITERATE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.SEARCH)
            .setAllowExpression(true)
            .setRestartAllServices()
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
                .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
                .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
                .setRequires(ElytronDescriptionConstants.NEW_ITEM_PATH, ElytronDescriptionConstants.NEW_ITEM_RDN)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { NEW_ITEM_PATH, NEW_ITEM_RDN, NEW_ITEM_ATTRIBUTES };

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.NEW_ITEM_TEMPLATE, ATTRIBUTES)
                .setRestartAllServices()
                .build();
    }

    static final SimpleAttributeDefinition ALIAS_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("cn"))
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("usercertificate"))
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("X.509"))
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_CHAIN_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("userSMIMECertificate"))
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_CHAIN_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_CHAIN_ENCODING, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("PKCS7"))
            .build();

    static final SimpleAttributeDefinition KEY_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_ATTRIBUTE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("userPKCS12"))
            .build();

    static final SimpleAttributeDefinition KEY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_TYPE, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE_MAPPING)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setDefaultValue(new ModelNode("PKCS12"))
            .build();

    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.LDAP_KEY_STORE);

    private static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIZE, ModelType.INT)
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
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }

        if (isServerOrHostController(resourceRegistration)) {
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
    }

    private static class KeyStoreAddHandler extends BaseAddHandler {

        private KeyStoreAddHandler() {
            super(KEY_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES);
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

            commonDependencies(serviceBuilder).install();
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
                .setXmlName("attribute")
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
