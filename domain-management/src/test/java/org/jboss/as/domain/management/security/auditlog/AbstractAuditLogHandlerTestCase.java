/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_LENGTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MESSAGE_TRANSFER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSLOG_FORMAT;
import static org.jboss.as.controller.security.CredentialReference.CLEAR_TEXT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLoggerImpl;
import org.jboss.as.controller.audit.SyslogAuditLogHandler;
import org.jboss.as.controller.audit.SyslogAuditLogHandler.Transport;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.as.domain.management.audit.FileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.SyslogAuditLogProtocolResourceDefinition;
import org.jboss.as.domain.management.security.util.ManagementControllerTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.junit.After;
import org.junit.Assert;

/**
 * Don't use core-model test for this. It does not support runtime, and more importantly for backwards compatibility the audit logger cannot be used
 *
 * @author: Kabir Khan
 */
public class AbstractAuditLogHandlerTestCase extends ManagementControllerTestBase {
    protected static final PathAddress AUDIT_ADDR = PathAddress.pathAddress(CoreManagementResourceDefinition.PATH_ELEMENT, AccessAuditResourceDefinition.PATH_ELEMENT);
    protected static final int SYSLOG_PORT = 6666;
    protected static final int SYSLOG_PORT2 = 6667;

    protected final List<ModelNode> bootOperations = new ArrayList<ModelNode>();

    public AbstractAuditLogHandlerTestCase(boolean enabled, boolean addFile) {

        bootOperations.add(Util.createAddOperation(AUDIT_ADDR));
        ModelNode add = Util.createAddOperation(createJsonFormatterAddress("test-formatter"));
        bootOperations.add(add);

        if (addFile) {
            add = createAddFileHandlerOperation("test-file", "test-formatter", "test-file.log");
            add.get(FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).set(3);
            bootOperations.add(add);
        }

        add = Util.createAddOperation(
                AUDIT_ADDR.append(AuditLogLoggerResourceDefinition.PATH_ELEMENT));
        add.get(ModelDescriptionConstants.ENABLED).set(enabled);
        add.get(ModelDescriptionConstants.LOG_READ_ONLY).set(true);
        bootOperations.add(add);
        if (addFile) {
            bootOperations.add(createAddHandlerReferenceOperation("test-file"));
        }
    }


    @After
    public void clearDependencies(){
        auditLogger = null;
        logDir = null;
    }

    /**
     * Override base method to clone the operation so any mutation of it by the controller
     * does not get noticed in comparisons of log output to op input.
     * {@inheritDoc}
     */
    @Override
    public ModelNode executeForResult(ModelNode operation) throws OperationFailedException {
        return super.executeForResult(operation.clone());
    }

    protected ManagedAuditLogger getAuditLogger(){
        if (auditLogger == null){
            auditLogger = new ManagedAuditLoggerImpl("8.0.0", true);
        }
        return auditLogger;
    }

    protected void checkHandlerRuntimeFailureMetrics(ModelNode handler, int maxFailureCount, int failureCount, boolean disabled) {
        Assert.assertEquals(maxFailureCount, handler.get(AuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.getName()).asInt());
        Assert.assertEquals(failureCount, handler.get(AuditLogHandlerResourceDefinition.FAILURE_COUNT.getName()).asInt());
        Assert.assertEquals(disabled, handler.get(AuditLogHandlerResourceDefinition.DISABLED_DUE_TO_FAILURE.getName()).asBoolean());
    }

    protected String stripSyslogHeader(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8);
        int i = s.indexOf(" - - ");
        return s.substring(i + 6);
    }

    protected ModelNode getSyslogRecord(byte[] bytes) {
        Assert.assertNotNull("bytes to create syslog record cannot be null.", bytes);
        String msg = new String(bytes, StandardCharsets.UTF_8);
        return getSyslogRecord(msg);
    }

    protected ModelNode getSyslogRecord(String msg) {
        msg = msg.substring(msg.indexOf('{')).replace("#012", "\n");
        return ModelNode.fromJSONString(msg);
    }

    protected void checkOpsEqual(ModelNode rawDmr, ModelNode fromLog) {
        ModelNode expected = ModelNode.fromJSONString(rawDmr.toJSONString(true));
        Assert.assertEquals(expected, fromLog);

    }

    private final Pattern DATE_STAMP_PATTERN = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{");

    protected List<ModelNode> readFile(File file, int expectedRecords) throws IOException {
        List<ModelNode> list = new ArrayList<ModelNode>();

        try (final BufferedReader reader = Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){
            StringWriter writer = null;
            String line = reader.readLine();
            while (line != null) {
                if (DATE_STAMP_PATTERN.matcher(line).matches()) {
                    if (writer != null) {
                        list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
                    }
                    writer = new StringWriter();
                    writer.append("{");
                } else {
                    writer.append("\n" + line);
                }
                line = reader.readLine();
            }
            if (writer != null) {
                list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
            }
        }
        Assert.assertEquals(list.toString(), expectedRecords, list.size());
        return list;
    }

    protected String readFullFileRecord(File file) throws IOException {
        try (final BufferedReader reader = Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){
            boolean firstLine = true;
            StringWriter writer = new StringWriter();
            String line = reader.readLine();
            while (line != null) {
                if (!firstLine) {
                    writer.append("\n");
                } else {
                    firstLine = false;
                }
                writer.append(line);
                line = reader.readLine();
            }
            return writer.toString();
        }
    }

    protected ModelNode createAuditLogWriteAttributeOperation(String attr, boolean value) {
        return Util.getWriteAttributeOperation(AUDIT_ADDR.append(AuditLogLoggerResourceDefinition.PATH_ELEMENT), attr, new ModelNode(value));
    }

    protected ModelNode createAddFileHandlerOperation(String handlerName, String formatterName, String fileName) {
        ModelNode op = Util.createAddOperation(createFileHandlerAddress(handlerName));
        op.get(ModelDescriptionConstants.RELATIVE_TO).set("log.dir");
        op.get(ModelDescriptionConstants.PATH).set(fileName);
        op.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        return op;
    }

    protected ModelNode createAddPeriodicRotatingFileHandlerOperation(String handlerName, String formatterName, String fileName, String suffix) {
        ModelNode op = Util.createAddOperation(createPeriodicRotatingFileHandlerAddress(handlerName));
        op.get(ModelDescriptionConstants.RELATIVE_TO).set("log.dir");
        op.get(ModelDescriptionConstants.PATH).set(fileName);
        op.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        op.get(ModelDescriptionConstants.SUFFIX).set(suffix);
        return op;
    }

    protected ModelNode createAddSizeRotatingFileHandlerOperation(String handlerName, String formatterName, String fileName, String rotateSize, int maxBackupIndex) {
        ModelNode op = Util.createAddOperation(createSizeRotatingFileHandlerAddress(handlerName));
        op.get(ModelDescriptionConstants.RELATIVE_TO).set("log.dir");
        op.get(ModelDescriptionConstants.PATH).set(fileName);
        op.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        if (rotateSize != null) {
            op.get(ModelDescriptionConstants.ROTATE_SIZE).set(rotateSize);
        }
        op.get(ModelDescriptionConstants.MAX_BACKUP_INDEX).set(maxBackupIndex);
        return op;
    }

    protected ModelNode createRemoveFileHandlerOperation(String handlerName) {
        return Util.createRemoveOperation(createFileHandlerAddress(handlerName));
    }

    protected ModelNode createRemovePeriodicRotatingFileHandlerOperation(String handlerName) {
        return Util.createRemoveOperation(createPeriodicRotatingFileHandlerAddress(handlerName));
    }

    protected ModelNode createRemoveSizeRotatingFileHandlerOperation(String handlerName) {
        return Util.createRemoveOperation(createSizeRotatingFileHandlerAddress(handlerName));
    }

    protected PathAddress createFileHandlerAddress(String handlerName){
        return createHandlerAddress(ModelDescriptionConstants.FILE_HANDLER, handlerName);
    }

    protected PathAddress createPeriodicRotatingFileHandlerAddress(String handlerName){
        return createHandlerAddress(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, handlerName);
    }

    protected PathAddress createSizeRotatingFileHandlerAddress(String handlerName){
        return createHandlerAddress(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER, handlerName);
    }

    protected PathAddress createHandlerAddress(String handlerType, String handlerName){
        return AUDIT_ADDR.append(PathElement.pathElement(handlerType, handlerName));
    }

    protected ModelNode createRemoveJsonFormatterOperation(String formatterName) {
        return Util.createRemoveOperation(createJsonFormatterAddress(formatterName));
    }

    protected PathAddress createJsonFormatterAddress(String formatterName) {
        return AUDIT_ADDR.append(
                PathElement.pathElement(ModelDescriptionConstants.JSON_FORMATTER, formatterName));
    }


    protected ModelNode createAddSyslogHandlerUdpOperation(String handlerName, String formatterName, InetAddress addr, int port, SyslogHandler.SyslogType syslogFormat, int maxLength){
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(STEPS).setEmptyList();

        ModelNode handler = Util.createAddOperation(createSyslogHandlerAddress(handlerName));
        if (syslogFormat != null){
            handler.get(SYSLOG_FORMAT).set(syslogFormat.toString());
        }
        if (maxLength > 0) {
            handler.get(MAX_LENGTH).set(maxLength);
        }
        handler.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        composite.get(STEPS).add(handler);

        ModelNode protocol = Util.createAddOperation(createSyslogHandlerProtocolAddress(handlerName, SyslogAuditLogHandler.Transport.UDP));
        protocol.get(HOST).set(InetAddressUtil.canonize(addr.getHostName()));
        protocol.get(PORT).set(port);
        composite.get(STEPS).add(protocol);

        return composite;
    }

    protected ModelNode createAddSyslogHandlerTcpOperation(String handlerName, String formatterName, InetAddress addr, int port, SyslogHandler.SyslogType syslogFormat, SyslogAuditLogHandler.MessageTransfer transfer){
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(STEPS).setEmptyList();

        ModelNode handler = Util.createAddOperation(createSyslogHandlerAddress(handlerName));
        if (syslogFormat != null){
            handler.get(SYSLOG_FORMAT).set(syslogFormat.toString());
        }
        handler.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        composite.get(STEPS).add(handler);

        ModelNode protocol = Util.createAddOperation(createSyslogHandlerProtocolAddress(handlerName, SyslogAuditLogHandler.Transport.TCP));
        protocol.get(HOST).set(InetAddressUtil.canonize(addr.getHostName()));
        protocol.get(PORT).set(port);
        if (transfer != null) {
            protocol.get(MESSAGE_TRANSFER).set(transfer.name());
        }
        composite.get(STEPS).add(protocol);

        return composite;
    }

    protected ModelNode createAddSyslogHandlerTlsOperation(String handlerName, String formatterName, InetAddress addr, int port, SyslogHandler.SyslogType syslogFormat,
            SyslogAuditLogHandler.MessageTransfer transfer, File truststorePath, String trustPwd, File clientCertPath, String clientCertPwd){
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(STEPS).setEmptyList();

        ModelNode handler = Util.createAddOperation(createSyslogHandlerAddress(handlerName));
        if (syslogFormat != null){
            handler.get(SYSLOG_FORMAT).set(syslogFormat.toString());
        }
        handler.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        composite.get(STEPS).add(handler);

        ModelNode protocol = Util.createAddOperation(createSyslogHandlerProtocolAddress(handlerName, SyslogAuditLogHandler.Transport.TLS));
        protocol.get(HOST).set(InetAddressUtil.canonize(addr.getHostName()));
        protocol.get(PORT).set(port);
        if (transfer != null) {
            protocol.get(MESSAGE_TRANSFER).set(transfer.name());
        }
        composite.get(STEPS).add(protocol);

        ModelNode truststore = Util.createAddOperation(
                createSyslogHandlerProtocolAddress("syslog-test", Transport.TLS).append(
                        PathElement.pathElement(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.TRUSTSTORE)));
        truststore.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.getName()).set(truststorePath.getAbsolutePath());
        truststore.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD.getName()).set(trustPwd);
        composite.get(STEPS).add(truststore);

        if (clientCertPath != null) {
            ModelNode clientCert = Util.createAddOperation(createSyslogHandlerProtocolAddress("syslog-test", Transport.TLS).append(
                    PathElement.pathElement(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.CLIENT_CERT_STORE)));
            clientCert.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.getName()).set(clientCertPath.getAbsolutePath());
            clientCert.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD.getName()).set(clientCertPwd);
            composite.get(STEPS).add(clientCert);
        }
        return composite;
    }

    protected ModelNode createAddSyslogHandlerCredentialReferenceTlsOperation(String handlerName, String formatterName, InetAddress addr, int port, SyslogHandler.SyslogType syslogFormat,
            SyslogAuditLogHandler.MessageTransfer transfer, File truststorePath, String trustPwd, File clientCertPath, String clientCertPwd){
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        composite.get(OP_ADDR).setEmptyList();
        composite.get(STEPS).setEmptyList();

        ModelNode handler = Util.createAddOperation(createSyslogHandlerAddress(handlerName));
        if (syslogFormat != null){
            handler.get(SYSLOG_FORMAT).set(syslogFormat.toString());
        }
        handler.get(ModelDescriptionConstants.FORMATTER).set(formatterName);
        composite.get(STEPS).add(handler);

        ModelNode protocol = Util.createAddOperation(createSyslogHandlerProtocolAddress(handlerName, SyslogAuditLogHandler.Transport.TLS));
        protocol.get(HOST).set(InetAddressUtil.canonize(addr.getHostName()));
        protocol.get(PORT).set(port);
        if (transfer != null) {
            protocol.get(MESSAGE_TRANSFER).set(transfer.name());
        }
        composite.get(STEPS).add(protocol);

        ModelNode truststore = Util.createAddOperation(
                createSyslogHandlerProtocolAddress("syslog-test", Transport.TLS).append(
                        PathElement.pathElement(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.TRUSTSTORE)));
        truststore.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.getName()).set(truststorePath.getAbsolutePath());
        ModelNode trustStoreCredentialRef = new ModelNode();
        trustStoreCredentialRef.get(CLEAR_TEXT).set(trustPwd);
        truststore.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName()).set(trustStoreCredentialRef);
        composite.get(STEPS).add(truststore);

        if (clientCertPath != null) {
            ModelNode clientCert = Util.createAddOperation(createSyslogHandlerProtocolAddress("syslog-test", Transport.TLS).append(
                    PathElement.pathElement(ModelDescriptionConstants.AUTHENTICATION, ModelDescriptionConstants.CLIENT_CERT_STORE)));
            clientCert.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PATH.getName()).set(clientCertPath.getAbsolutePath());
            ModelNode clientCertCredentialRef = new ModelNode();
            clientCertCredentialRef.get(CLEAR_TEXT).set(clientCertPwd);
            clientCert.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName()).set(clientCertCredentialRef);
            composite.get(STEPS).add(clientCert);
        }
        return composite;
    }


    protected PathAddress createSyslogHandlerAddress(String handlerName){
        return AUDIT_ADDR.append(PathElement.pathElement(ModelDescriptionConstants.SYSLOG_HANDLER, handlerName));
    }

    protected PathAddress createSyslogHandlerProtocolAddress(String handlerName, SyslogAuditLogHandler.Transport transport){
        return AUDIT_ADDR.append(
                PathElement.pathElement(ModelDescriptionConstants.SYSLOG_HANDLER, handlerName),
                PathElement.pathElement(PROTOCOL, transport.name().toLowerCase()));
    }

    protected ModelNode createAddHandlerReferenceOperation(String name){
        return Util.createAddOperation(createHandlerReferenceAddress(name));
    }

    protected ModelNode createRemoveHandlerReferenceOperation(String name){
        return Util.createRemoveOperation(createHandlerReferenceAddress(name));
    }

    protected PathAddress createHandlerReferenceAddress(String name){
        return AUDIT_ADDR.append(
                        AuditLogLoggerResourceDefinition.PATH_ELEMENT,
                        PathElement.pathElement(ModelDescriptionConstants.HANDLER, name));
    }

    protected List<ModelNode> checkBootRecordHeader(ModelNode bootRecord, int ops, String type, boolean readOnly, boolean booting, boolean success) {
        Assert.assertEquals(type, bootRecord.get("type").asString());
        Assert.assertEquals(readOnly, bootRecord.get("r/o").asBoolean());
        Assert.assertEquals(booting, bootRecord.get("booting").asBoolean());
        Assert.assertEquals("anonymous", bootRecord.get("user").asString());
        Assert.assertFalse(bootRecord.get("domainUUID").isDefined());
        Assert.assertFalse(bootRecord.get("access").isDefined());
        Assert.assertFalse(bootRecord.get("remote-address").isDefined());
        Assert.assertEquals(success, bootRecord.get("success").asBoolean());
        List<ModelNode> operations = bootRecord.get("ops").asList();
        Assert.assertEquals(ops, operations.size());
        return operations;
    }

    protected FilenameFilter createLogFilenameFilter(final String filenamePrefix) {
        return new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(filenamePrefix);
            }
        };
    }

    @Override
    protected void addBootOperations(List<ModelNode> bootOperations) {
        for (ModelNode bootOp : this.bootOperations) {
            bootOperations.add(bootOp.clone()); // clone so we don't have to worry about mutated ops when we compare
        }
    }

}
