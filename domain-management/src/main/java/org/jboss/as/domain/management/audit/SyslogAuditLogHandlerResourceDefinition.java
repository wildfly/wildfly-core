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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECONNECT_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.security.CredentialReference.applyCredentialReferenceUpdateToRuntime;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.jboss.as.domain.management.audit.SyslogAuditLogHandlerService.SYSLOG_AUDIT_HANDLER;
import static org.jboss.as.domain.management.security.KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME;
import static org.jboss.as.domain.management.security.KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.SyslogAuditLogHandler;
import org.jboss.as.controller.audit.SyslogAuditLogHandler.Facility;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.jboss.msc.service.ServiceName;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SyslogAuditLogHandlerResourceDefinition extends AuditLogHandlerResourceDefinition {

    private final EnvironmentNameReader environmentReader;

    private static final String DEFAULT_APP_NAME_IF_NOT_A_PRODUCT = "WildFly";

    public static final SimpleAttributeDefinition SYSLOG_FORMAT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SYSLOG_FORMAT, ModelType.STRING)
        .setRequired(false)
        .setDefaultValue(new ModelNode(SyslogHandler.SyslogType.RFC5424.toString()))
        .setAllowExpression(true)
        .setValidator(new EnumValidator<>(SyslogHandler.SyslogType.class, true, true))
        .setMinSize(1)
        .build();

    public static final SimpleAttributeDefinition TRUNCATE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TRUNCATE, ModelType.BOOLEAN)
        .setRequired(false)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .build();

    public static final SimpleAttributeDefinition MAX_LENGTH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MAX_LENGTH, ModelType.INT)
        .setRequired(false)
        .setAllowExpression(true)
        .setMinSize(0)
        .build();

    public static final SimpleAttributeDefinition FACILITY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.FACILITY, ModelType.STRING)
        .setRequired(false)
        .setValidator(new EnumValidator<SyslogAuditLogHandler.Facility>(SyslogAuditLogHandler.Facility.class, true, true))
        .setDefaultValue(new ModelNode(SyslogAuditLogHandler.Facility.USER_LEVEL.name()))
        .setAllowExpression(true)
        .build();

    public static final SimpleAttributeDefinition APP_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.APP_NAME, ModelType.STRING)
        .setRequired(false)
        .setAllowExpression(true)
        .build();



    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {FORMATTER, MAX_LENGTH, SYSLOG_FORMAT, TRUNCATE, MAX_FAILURE_COUNT, APP_NAME, FACILITY};

    public SyslogAuditLogHandlerResourceDefinition(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
        super(auditLogger, pathManager, new Parameters(PathElement.pathElement(SYSLOG_HANDLER),
                DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.syslog-handler"))
                .setAddHandler(new SyslogAuditLogHandlerAddHandler(auditLogger, pathManager, environmentReader))
                .setRemoveHandler(new HandlerRemoveHandler(auditLogger)));
        this.environmentReader = environmentReader;
        setDeprecated(ModelVersion.create(1, 7));
    }

    public static void createServerAddOperations(List<ModelNode> addOps, PathAddress syslogHandlerAddress, ModelNode syslogHandler) {
        ModelNode syslogHandlerAdd = createServerAddOperation(syslogHandlerAddress, syslogHandler);
        addOps.add(syslogHandlerAdd);

        Property protocol = syslogHandler.get(PROTOCOL).asPropertyList().iterator().next();
        PathAddress protocolAddress = syslogHandlerAddress.append(PathElement.pathElement(PROTOCOL, protocol.getName()));
        SyslogAuditLogProtocolResourceDefinition.createServerAddOperations(addOps, protocolAddress, protocol.getValue());
    }

    public static ModelNode createServerAddOperation(PathAddress syslogHandlerAddress, ModelNode syslogHandler){
        ModelNode syslogHandlerAdd = Util.createAddOperation(syslogHandlerAddress);
        for (AttributeDefinition def : ATTRIBUTES) {
            if (syslogHandler.get(def.getName()).isDefined()) {
                syslogHandlerAdd.get(def.getName()).set(syslogHandler.get(def.getName()));
            }
        }
        return syslogHandlerAdd;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        OperationStepHandler writeAttribute = new HandlerWriteAttributeHandler(auditLogger, pathManager, environmentReader, ATTRIBUTES);
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, writeAttribute);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new SyslogAuditLogProtocolResourceDefinition.Udp(auditLogger, pathManager, environmentReader));
        resourceRegistration.registerSubModel(new SyslogAuditLogProtocolResourceDefinition.Tcp(auditLogger, pathManager, environmentReader));
        resourceRegistration.registerSubModel(new SyslogAuditLogProtocolResourceDefinition.Tls(auditLogger, pathManager, environmentReader));
    }

    static PathAddress getAffectedHandlerAddress(OperationContext context){
        final PathAddress opAddress = context.getCurrentAddress();
        int lastElement = opAddress.size() - 1;
        for (int i = lastElement; i >-0; i--) {
            PathElement element = opAddress.getElement(i);
            if (element.getKey().equals(SYSLOG_HANDLER)) {
                return i == lastElement ? opAddress : opAddress.subAddress(0, i + 1);
            }
        }
        return PathAddress.EMPTY_ADDRESS;
    }

    static SyslogAuditLogHandler createHandler(final PathManagerService pathManager,
                                               final OperationContext context,
                                               final EnvironmentNameReader environmentReader) throws OperationFailedException {
        final PathAddress pathAddress = getAffectedHandlerAddress(context);
        final String name = Util.getNameFromAddress(pathAddress);
        final Resource handlerResource = context.readResourceFromRoot(pathAddress);
        return createHandler(pathManager, context, name, handlerResource, environmentReader);
    }

    static SyslogAuditLogHandler createHandler(final PathManagerService pathManager,
                                               final OperationContext context,
                                               final String name,
                                               final Resource handlerResource,
                                               final EnvironmentNameReader environmentReader) throws OperationFailedException {
        ServiceName serviceName = SYSLOG_AUDIT_HANDLER.append(name);
        if(context.getServiceRegistry(true).getService(serviceName) != null) {
            context.removeService(serviceName);
        }
        SyslogAuditLogHandlerService service = SyslogAuditLogHandlerService.installService(context, serviceName, handlerResource);
        final ModelNode handlerModel = handlerResource.getModel();
        final String formatterName = FORMATTER.resolveModelAttribute(context, handlerModel).asString();
        final int maxFailureCount = MAX_FAILURE_COUNT.resolveModelAttribute(context, handlerModel).asInt();

        final SyslogAuditLogHandler handler = new SyslogAuditLogHandler(name, formatterName, maxFailureCount, pathManager, service);

        handler.setFacility(SyslogAuditLogHandler.Facility.valueOf(FACILITY.resolveModelAttribute(context, handlerModel).asString()));

        if (environmentReader.isServer()) {
            handler.setHostName(environmentReader.getHostName() != null ? environmentReader.getHostName() + ":" + environmentReader.getServerName() : environmentReader.getServerName());
        } else {
            handler.setHostName(environmentReader.getHostName());
        }

        handler.setAppName(resolveAppName(context, handlerModel.get(APP_NAME.getName()), environmentReader));


        handler.setSyslogType(SyslogHandler.SyslogType.valueOf(SYSLOG_FORMAT.resolveModelAttribute(context, handlerModel).asString()));
        handler.setTruncate(TRUNCATE.resolveModelAttribute(context, handlerModel).asBoolean());
        if (handlerModel.hasDefined(MAX_LENGTH.getName())) {
            handler.setMaxLength(MAX_LENGTH.resolveModelAttribute(context, handlerModel).asInt());
        }
        final Set<ResourceEntry> protocols = handlerResource.getChildren(PROTOCOL);
        if (protocols.isEmpty()) {
            //We already check in SyslogAuditLogProtocolResourceDefinition that there is only one protocol
            throw DomainManagementLogger.ROOT_LOGGER.noSyslogProtocol();
        }
        final ResourceEntry protocol = protocols.iterator().next();
        final SyslogAuditLogHandler.Transport transport = SyslogAuditLogHandler.Transport.valueOf(protocol.getPathElement().getValue().toUpperCase(Locale.ENGLISH));
        handler.setTransport(transport);
        try {
            handler.setSyslogServerAddress(
                    InetAddress.getByName(
                            SyslogAuditLogProtocolResourceDefinition.HOST.resolveModelAttribute(context, protocol.getModel()).asString()));
        } catch (UnknownHostException e) {
            throw new OperationFailedException(e);
        }
        handler.setPort(SyslogAuditLogProtocolResourceDefinition.PORT.resolveModelAttribute(context, protocol.getModel()).asInt());
        if (transport != SyslogAuditLogHandler.Transport.UDP) {
            handler.setMessageTransfer(
                    SyslogAuditLogHandler.MessageTransfer.valueOf(
                            SyslogAuditLogProtocolResourceDefinition.Tcp.MESSAGE_TRANSFER.resolveModelAttribute(context, protocol.getModel()).asString()));
            handler.setReconnectTimeout(SyslogAuditLogProtocolResourceDefinition.Tcp.RECONNECT_TIMEOUT.resolveModelAttribute(context, protocol.getModel()).asInt());
        }
        if (transport == SyslogAuditLogHandler.Transport.TLS) {
            final Set<ResourceEntry> tlsStores = protocol.getChildren(AUTHENTICATION);
            for (ResourceEntry storeEntry : tlsStores) {
                final ModelNode storeModel = storeEntry.getModel();
                String type = storeEntry.getPathElement().getValue();
                if (type.equals(CLIENT_CERT_STORE)) {
                    handler.setTlsClientCertStorePassword(
                            resolveUndefinableAttribute(context,
                                    SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD, storeModel));
                    handler.setTlsClientCertStorePath(
                            SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.resolveModelAttribute(context, storeModel).asString());
                    handler.setTlsClientCertStoreRelativeTo(
                            resolveUndefinableAttribute(context,
                                    SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_RELATIVE_TO, storeModel));
                    handler.setTlsClientCertStoreKeyPassword(
                            resolveUndefinableAttribute(context,
                                    SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEY_PASSWORD, storeModel));
                } else if (type.equals(TRUSTSTORE)) {
                    handler.setTlsTruststorePassword(
                            resolveUndefinableAttribute(context,
                                    SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD, storeModel));
                    handler.setTlsTrustStorePath(
                            SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.resolveModelAttribute(context, storeModel).asString());
                    handler.setTlsTrustStoreRelativeTo(
                            resolveUndefinableAttribute(context,
                                    SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_RELATIVE_TO, storeModel));
                }
            }
        }
        return handler;
    }

    private static String resolveUndefinableAttribute(OperationContext context, AttributeDefinition attr, ModelNode model) throws OperationFailedException {
        if (model.hasDefined(attr.getName())){
            return attr.resolveModelAttribute(context, model).asString();
        }
        return null;
    }

    private static String resolveAppName(OperationContext context, ModelNode appName, EnvironmentNameReader environmentReader) throws OperationFailedException {
        if (appName.isDefined()) {
            return context.resolveExpressions(appName).asString();
        }
        if (environmentReader.getProductName() != null) {
            return environmentReader.getProductName();
        }
        return DEFAULT_APP_NAME_IF_NOT_A_PRODUCT;
    }

    static class SyslogAuditLogHandlerRemoveHandler extends AbstractRemoveStepHandler {

        private final ManagedAuditLogger auditLogger;

        SyslogAuditLogHandlerRemoveHandler(ManagedAuditLogger auditLogger) {
            this.auditLogger = auditLogger;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            String name = Util.getNameFromAddress(operation.require(OP_ADDR));
            auditLogger.getUpdater().removeHandler(name);
            context.removeService(SYSLOG_AUDIT_HANDLER.append(name));
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            auditLogger.getUpdater().rollbackChanges();
            String name = Util.getNameFromAddress(operation.require(OP_ADDR));
            final Resource handlerResource = context.readResourceFromRoot(getAffectedHandlerAddress(context));
            SyslogAuditLogHandlerService.installService(context, SYSLOG_AUDIT_HANDLER.append(name), handlerResource);
        }
    }

    static class SyslogAuditLogHandlerAddHandler extends AbstractAddStepHandler {

        private final PathManagerService pathManager;
        private final ManagedAuditLogger auditLogger;
        private final EnvironmentNameReader environmentReader;

        private SyslogAuditLogHandlerAddHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader) {
            super(ATTRIBUTES);
            this.pathManager = pathManager;
            this.auditLogger = auditLogger;
            this.environmentReader = environmentReader;
        }

        @Override
        protected void populateModel(OperationContext context, ModelNode operation, final Resource resource)
                throws OperationFailedException {

            super.populateModel(context, operation, resource);

            // Cross-resource model validation in a separate step to give
            // other ops included with this one a chance to execute before we validate
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    HandlerUtil.checkNoOtherHandlerWithTheSameName(context);
                    String formatterName = resource.getModel().get(FORMATTER.getName()).asString();
                    if (!HandlerUtil.lookForFormatter(context, context.getCurrentAddress(), formatterName)) {
                        throw DomainManagementLogger.ROOT_LOGGER.noFormatterCalled(formatterName);
                    }
                }
            }, OperationContext.Stage.MODEL);

        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            auditLogger.getUpdater().addHandler(createHandler(pathManager, context, environmentReader));
        }
        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            auditLogger.getUpdater().rollbackChanges();
        }
    }

    /**
     * Attribute writes on the handler or sub-resources, as well as protocol removes might/should be done as a composite,
     * which means that we might get to the runtime part several times. So if this is an update validate what we have
     * with the original handler to make sure that only the first update takes effect
     */
    static class HandlerWriteAttributeHandler extends AuditLogHandlerResourceDefinition.HandlerWriteAttributeHandler {
        private final EnvironmentNameReader environmentReader;

        public HandlerWriteAttributeHandler(ManagedAuditLogger auditLogger, PathManagerService pathManager, EnvironmentNameReader environmentReader, AttributeDefinition... attributeDefinition) {
            super(auditLogger, pathManager, attributeDefinition);
            this.environmentReader = environmentReader;
        }

        @Override
        protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                        ModelNode oldValue, Resource resource) throws OperationFailedException {
            super.finishModelStage(context, operation, attributeName, newValue, oldValue, resource);
            if (attributeName.equals(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) || attributeName.equals(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                handleCredentialReferenceUpdate(context, resource.getModel().get(attributeName), attributeName);
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context){
            return true;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            if (super.handleApplyAttributeRuntime(context, operation, attributeName, resolvedValue, currentValue, handbackHolder)) {
                return false;
            }

            if (attributeName.equals(APP_NAME.getName())) {
                auditLogger.updateSyslogHandlerAppName(Util.getNameFromAddress(operation.require(OP_ADDR)), resolveAppName(context, operation.get(VALUE), environmentReader));
            } else if (attributeName.equals(FACILITY.getName())) {
                auditLogger.updateSyslogHandlerFacility(Util.getNameFromAddress(operation.require(OP_ADDR)), Facility.valueOf(resolvedValue.asString()));
            } else if (attributeName.equals(RECONNECT_TIMEOUT)) {
                PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
                addr = addr.subAddress(0, addr.size() - 1);
                auditLogger.updateSyslogHandlerReconnectTimeout(Util.getNameFromAddress(addr), resolvedValue.asInt());
            } else if (attributeName.equals(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) || attributeName.equals(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                return applyCredentialReferenceUpdateToRuntime(context, operation, resolvedValue, currentValue, attributeName);
            } else {
                auditLogger.getUpdater().updateHandler(createHandler(pathManager, context, environmentReader));
            }
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            if (super.handlerRevertUpdateToRuntime(context, operation, attributeName, valueToRestore, valueToRevert, handback)) {
                return;
            }

            if (attributeName.equals(APP_NAME.getName())) {
                auditLogger.updateSyslogHandlerAppName(Util.getNameFromAddress(operation.require(OP_ADDR)), resolveAppName(context, valueToRestore, environmentReader));
            } else if (attributeName.equals(FACILITY.getName())) {
                auditLogger.updateSyslogHandlerFacility(Util.getNameFromAddress(operation.require(OP_ADDR)), Facility.valueOf(valueToRestore.asString()));
            } else if (attributeName.equals(RECONNECT_TIMEOUT)) {
                PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
                addr = addr.subAddress(0, addr.size() - 1);
                auditLogger.updateSyslogHandlerReconnectTimeout(Util.getNameFromAddress(addr), valueToRestore.asInt());
            } else if (attributeName.equals(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME) || attributeName.equals(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                rollbackCredentialStoreUpdate(getAttributeDefinition(attributeName), context, valueToRevert);
            } else {
                auditLogger.getUpdater().rollbackChanges();
            }
        }
    }

}
