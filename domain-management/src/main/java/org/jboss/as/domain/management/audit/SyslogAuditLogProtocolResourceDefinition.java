/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.jboss.as.domain.management.security.KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME;
import static org.jboss.as.domain.management.security.KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.SyslogAuditLogHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.security.KeystoreAttributes;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class SyslogAuditLogProtocolResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST, ModelType.STRING)
        .setRequired(false)
        .setDefaultValue(new ModelNode("localhost"))
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT)
        .setRequired(false)
        .setDefaultValue(new ModelNode(514))
        .setAllowExpression(true)
        .build();

    protected final ManagedAuditLogger auditLogger;
    protected final PathManagerService pathManager;
    protected final EnvironmentNameReader environmentReader;
    protected final AttributeDefinition[] attributes;

    SyslogAuditLogProtocolResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager,
                                             AttributeDefinition[] attributes, PathElement pathElement,
                                             ResourceDescriptionResolver resolver, EnvironmentNameReader environmentReader) {
        super(
                pathElement,
                resolver,
                new ProtocolConfigAddHandler(auditLogger, pathManager, attributes, environmentReader),
                new ProtocolConfigRemoveHandler(auditLogger, pathManager, environmentReader));
        this.auditLogger = auditLogger;
        this.pathManager = pathManager;
        this.attributes = attributes;
        this.environmentReader = environmentReader;
        setDeprecated(ModelVersion.create(1, 7));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler handler = new SyslogAuditLogHandlerResourceDefinition.HandlerWriteAttributeHandler(auditLogger, pathManager, environmentReader, attributes);
        for (AttributeDefinition def : attributes){
            resourceRegistration.registerReadWriteAttribute(def, null, handler);
        }
    }

    public static void createServerAddOperations(final List<ModelNode> addOps, final PathAddress protocolAddress, final ModelNode protocol) {
        addOps.add(createProtocolAddOperation(protocolAddress, protocol));

        final SyslogAuditLogHandler.Transport transport = SyslogAuditLogHandler.Transport.valueOf(protocolAddress.getLastElement().getValue().toUpperCase(Locale.ENGLISH));
        if (transport == SyslogAuditLogHandler.Transport.TLS){
            if (protocol.hasDefined(AUTHENTICATION)){
                final ModelNode auth = protocol.get(AUTHENTICATION);
                if (auth.hasDefined(TRUSTSTORE)){
                    addOps.add(createKeystoreAddOperation(protocolAddress.append(AUTHENTICATION, TRUSTSTORE), protocol.get(AUTHENTICATION, TRUSTSTORE)));
                }
                if (auth.hasDefined(CLIENT_CERT_STORE)){
                    addOps.add(createKeystoreAddOperation(protocolAddress.append(AUTHENTICATION, CLIENT_CERT_STORE), protocol.get(AUTHENTICATION, CLIENT_CERT_STORE)));
                }
            }
        }
    }

    public static ModelNode createProtocolAddOperation(final PathAddress protocolAddress, final ModelNode protocol){
        ModelNode protocolAdd = Util.createAddOperation(protocolAddress);
        protocolAdd.get(HOST.getName()).set(protocol.get(HOST.getName()));
        protocolAdd.get(PORT.getName()).set(protocol.get(PORT.getName()));

        SyslogAuditLogHandler.Transport transport = SyslogAuditLogHandler.Transport.valueOf(protocolAddress.getLastElement().getValue().toUpperCase(Locale.ENGLISH));
        if (transport != SyslogAuditLogHandler.Transport.UDP){
            protocolAdd.get(Tcp.MESSAGE_TRANSFER.getName()).set(protocol.get(Tcp.MESSAGE_TRANSFER.getName()));
        }

        return protocolAdd;
    }

    public static ModelNode createKeystoreAddOperation(final PathAddress storeAddress, final ModelNode store){
        ModelNode storeAdd = Util.createAddOperation(storeAddress);
        for (AttributeDefinition def : TlsKeyStore.CLIENT_CERT_ATTRIBUTES){
            storeAdd.get(def.getName()).set(store.get(def.getName()));
        }

        return storeAdd;
    }


    public static class Udp extends SyslogAuditLogProtocolResourceDefinition {
        public static final PathElement PATH_ELEMENT = PathElement.pathElement(PROTOCOL, ModelDescriptionConstants.UDP);

        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{HOST, PORT};

        Udp(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            super(auditLogger, pathManager, ATTRIBUTES, PATH_ELEMENT,
                    DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.syslog-udp"), environmentReader);
        }

        Udp(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes,
                PathElement pathElement, ResourceDescriptionResolver resolver, EnvironmentNameReader environmentReader) {
            super(auditLogger, pathManager, attributes, pathElement, resolver, environmentReader);
        }

    }

    public static class Tcp extends Udp {
        public static final PathElement PATH_ELEMENT = PathElement.pathElement(PROTOCOL, ModelDescriptionConstants.TCP);

        public static final SimpleAttributeDefinition MESSAGE_TRANSFER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MESSAGE_TRANSFER, ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode(SyslogAuditLogHandler.MessageTransfer.NON_TRANSPARENT_FRAMING.name()))
            .setAllowExpression(true)
            .build();

        public static final SimpleAttributeDefinition RECONNECT_TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RECONNECT_TIMEOUT, ModelType.INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode(-1))
            .setAllowExpression(true)
            .build();

        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{HOST, PORT, MESSAGE_TRANSFER, RECONNECT_TIMEOUT};


        Tcp(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            super(auditLogger, pathManager, ATTRIBUTES, PATH_ELEMENT,
                    DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.syslog-tcp"), environmentReader);
        }

        Tcp(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes,
            PathElement pathElement, ResourceDescriptionResolver resolver, EnvironmentNameReader environmentReader) {
            super(auditLogger, pathManager, attributes, pathElement, resolver, environmentReader);
        }
    }

    public static class Tls extends Tcp {
        public static final PathElement PATH_ELEMENT = PathElement.pathElement(PROTOCOL, ModelDescriptionConstants.TLS);

        private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{HOST, PORT, MESSAGE_TRANSFER, RECONNECT_TIMEOUT};

        Tls(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            super(auditLogger, pathManager, ATTRIBUTES, PATH_ELEMENT,
                    DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.syslog-tls"), environmentReader);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(TlsKeyStore.createTrustStore(auditLogger, pathManager, environmentReader));
            resourceRegistration.registerSubModel(TlsKeyStore.createClientCertStore(auditLogger, pathManager, environmentReader));
        }
    }

    public static class TlsKeyStore extends SimpleResourceDefinition {

        public static final RuntimeCapability SYSLOG_AUDIT_TLS_HANDLER = RuntimeCapability.Builder.of("org.wildfly.management.audit.handler", true, Void.class)
                .setDynamicNameMapper((PathAddress opAddress) -> {
                    int lastElement = opAddress.size() - 1;
                    for (int i = lastElement; i > -0; i--) {
                        PathElement element = opAddress.getElement(i);
                        if (element.getKey().equals(SYSLOG_HANDLER)) {
                            return i == lastElement ? opAddress.getLastElement().getKeyValuePair() : new String[]{opAddress.subAddress(0, i + 1).getLastElement().getKey(), opAddress.subAddress(0, i + 1).getLastElement().getValue(), opAddress.getLastElement().getKey(), opAddress.getLastElement().getValue()};
                        }
                    }
                    return opAddress.getLastElement().getKeyValuePair();
                }).build();
        public static final PathElement TRUSTSTORE_ELEMENT = PathElement.pathElement(AUTHENTICATION, TRUSTSTORE);
        public static final PathElement CLIENT_CERT_ELEMENT = PathElement.pathElement(AUTHENTICATION, CLIENT_CERT_STORE);

        public static final SimpleAttributeDefinition KEYSTORE_PASSWORD = KeystoreAttributes.KEYSTORE_PASSWORD;
        public static final ObjectTypeAttributeDefinition KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME, KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME, false, true)
                    .setAlternatives(ModelDescriptionConstants.KEYSTORE_PASSWORD)
                    .build();
        public static final SimpleAttributeDefinition KEYSTORE_PATH = KeystoreAttributes.KEYSTORE_PATH;
        public static final SimpleAttributeDefinition KEYSTORE_RELATIVE_TO = KeystoreAttributes.KEYSTORE_RELATIVE_TO;
        public static final SimpleAttributeDefinition KEY_PASSWORD = KeystoreAttributes.KEY_PASSWORD;
        public static final ObjectTypeAttributeDefinition KEY_PASSWORD_CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME, KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME, true, true)
                    .setAlternatives(org.jboss.as.domain.management.ModelDescriptionConstants.KEY_PASSWORD)
                    .build();

        private static final AttributeDefinition[] CLIENT_CERT_ATTRIBUTES = new AttributeDefinition[]{
                KEYSTORE_PASSWORD, KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, KEYSTORE_PATH, KEYSTORE_RELATIVE_TO, KEY_PASSWORD, KEY_PASSWORD_CREDENTIAL_REFERENCE};

        private static final AttributeDefinition[] TRUSTSTORE_ATTRIBUTES = new AttributeDefinition[]{
                KEYSTORE_PASSWORD, KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, KEYSTORE_PATH, KEYSTORE_RELATIVE_TO};

        private final ManagedAuditLogger auditLogger;
        private final PathManagerService pathManager;
        private final AttributeDefinition[] attributes;
        private final EnvironmentNameReader environmentReader;

        private TlsKeyStore(ManagedAuditLogger auditLogger, PathManagerService pathManager, PathElement pathElement,
                           ResourceDescriptionResolver resolver, AttributeDefinition[] attributes, EnvironmentNameReader environmentReader) {
            super(new Parameters(pathElement, resolver)
                    .setAddHandler( new TlsKeyStoreAddHandler(auditLogger, pathManager, attributes, environmentReader, pathElement))
                    .setRemoveHandler(new ProtocolConfigRemoveHandler(auditLogger, pathManager, environmentReader))
                    .setCapabilities(SYSLOG_AUDIT_TLS_HANDLER));
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
            this.attributes = attributes;
            this.environmentReader = environmentReader;
        }

        static TlsKeyStore createTrustStore(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            return new TlsKeyStore(auditLogger, pathManager, TRUSTSTORE_ELEMENT,
                    DomainManagementResolver.getResolver("core.management.syslog-truststore"),
                    TRUSTSTORE_ATTRIBUTES, environmentReader);
        }

        static TlsKeyStore createClientCertStore(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            return new TlsKeyStore(auditLogger, pathManager, CLIENT_CERT_ELEMENT,
                    DomainManagementResolver.getResolver("core.management.syslog-client-cert-store"),
                    CLIENT_CERT_ATTRIBUTES, environmentReader);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            OperationStepHandler handler = new SyslogAuditLogHandlerResourceDefinition.HandlerWriteAttributeHandler(auditLogger, pathManager, environmentReader, attributes);
            if (getPathElement().equals(TRUSTSTORE_ELEMENT)) {
                for (AttributeDefinition attr : TRUSTSTORE_ATTRIBUTES){
                    resourceRegistration.registerReadWriteAttribute(attr, null, handler);
                }
            } else {
                for (AttributeDefinition attr : CLIENT_CERT_ATTRIBUTES) {
                    resourceRegistration.registerReadWriteAttribute(attr, null, handler);
                }
            }
        }
    }

    /**
     * Attribute writes on the handler or sub-resources, as well as protocol removes might/should be done as a composite,
     * which means that we might get to the runtime part several times. So if this is an update validate what we have
     * with the original handler to make sure that only the first update takes effect
     */
    private static class ProtocolConfigRemoveHandler extends AbstractRemoveStepHandler {
        private final ManagedAuditLogger auditLogger;
        private final PathManagerService pathManager;
        private final EnvironmentNameReader environmentReader;

        public ProtocolConfigRemoveHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            this.pathManager = pathManager;
            this.auditLogger = auditLogger;
            this.environmentReader = environmentReader;
        }

        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            PathAddress handlerAddress = SyslogAuditLogHandlerResourceDefinition.getAffectedHandlerAddress(context);
            try {
                Resource handleResource = context.readResourceFromRoot(handlerAddress);
                String name = Util.getNameFromAddress(handlerAddress);
                auditLogger.getUpdater().updateHandler(SyslogAuditLogHandlerResourceDefinition.createHandler(pathManager, context, name, handleResource, environmentReader));
            } catch (Resource.NoSuchResourceException ignored) {
                // WFCORE-810 handler resource has been removed in this same op, so we do nothing
            }
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().rollbackChanges();
        }

    }

    private static class ProtocolConfigAddHandler extends AbstractAddStepHandler {
        private final ManagedAuditLogger auditLogger;
        private final PathManagerService pathManager;
        private final EnvironmentNameReader environmentReader;

        ProtocolConfigAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes, EnvironmentNameReader environmentReader){
            super(attributes);
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
            this.environmentReader = environmentReader;
        }

        @Override
        protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            checkNoOtherProtocol(context, operation);
            super.populateModel(context, operation, resource);
        }

        private void checkNoOtherProtocol(OperationContext context, ModelNode operation) throws OperationFailedException {
            PathAddress opAddr = PathAddress.pathAddress(operation.require(OP_ADDR));
            PathAddress addr = opAddr.subAddress(0, opAddr.size() - 1);
            Resource resource = context.readResourceFromRoot(addr);
            Set<ResourceEntry> existing = resource.getChildren(PROTOCOL);
            if (existing.size() > 1) {
                for (ResourceEntry entry : existing) {
                    PathElement mine = addr.getLastElement();
                    if (!entry.getPathElement().equals(mine)) {
                        throw DomainManagementLogger.ROOT_LOGGER.sysLogProtocolAlreadyConfigured(addr.append(mine));
                    }
                }
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            //On boot, the parent add will pick up these changes
            return !context.isBooting();
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().updateHandler(SyslogAuditLogHandlerResourceDefinition.createHandler(pathManager, context, environmentReader));
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            auditLogger.getUpdater().rollbackChanges();
        }
    }

    private static class TlsKeyStoreAddHandler extends AbstractAddStepHandler {
        private final ManagedAuditLogger auditLogger;
        private final PathManagerService pathManager;
        private final EnvironmentNameReader environmentReader;
        private final PathElement pathElement;

        TlsKeyStoreAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, AttributeDefinition[] attributes, EnvironmentNameReader environmentReader, PathElement pathElement) {
            super(attributes);
            this.auditLogger = auditLogger;
            this.pathManager = pathManager;
            this.environmentReader = environmentReader;
            this.pathElement = pathElement;
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            final ModelNode model = resource.getModel();
            if (pathElement.equals(TlsKeyStore.TRUSTSTORE_ELEMENT)) {
                handleCredentialReferenceUpdate(context, model.get(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME), KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME);
            } else if (pathElement.equals(TlsKeyStore.CLIENT_CERT_ELEMENT)) {
                handleCredentialReferenceUpdate(context, model.get(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME), KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME);
                handleCredentialReferenceUpdate(context, model.get(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME), KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME);
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            //On boot, the parent add will pick up these changes
            return !context.isBooting();
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().updateHandler(SyslogAuditLogHandlerResourceDefinition.createHandler(pathManager, context, environmentReader));
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            rollbackCredentialStoreUpdate(TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, context, resource);
            rollbackCredentialStoreUpdate(TlsKeyStore.KEY_PASSWORD_CREDENTIAL_REFERENCE, context, resource);
            auditLogger.getUpdater().rollbackChanges();
        }

    }
}
