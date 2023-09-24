/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.domain.management.audit.AccessAuditResourceDefinition;
import org.jboss.as.domain.management.audit.AuditLogLoggerResourceDefinition;
import org.jboss.as.domain.management.audit.FileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.InMemoryAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.JsonAuditLogFormatterResourceDefinition;
import org.jboss.as.domain.management.audit.KeystoreAttributes;
import org.jboss.as.domain.management.audit.PeriodicRotatingFileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.SizeRotatingFileAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.SyslogAuditLogHandlerResourceDefinition;
import org.jboss.as.domain.management.audit.SyslogAuditLogProtocolResourceDefinition;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
final class AuditLogXml_5 implements AuditLogXml {
    final boolean host;

    AuditLogXml_5(boolean host) {
        this.host = host;
    }

    private void parseFileAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        // added ROTATE_AT_STARTUP attribute

        final ModelNode add = Util.createAddOperation();
        list.add(add);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.FILE_HANDLER, value);
                    break;
                }
                case MAX_FAILURE_COUNT: {
                    FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.parseAndSetParameter(value, add, reader);
                    break;
                }
                case FORMATTER:{
                    FileAuditLogHandlerResourceDefinition.FORMATTER.parseAndSetParameter(value, add, reader);
                    break;
                }
                case PATH: {
                    FileAuditLogHandlerResourceDefinition.PATH.parseAndSetParameter(value, add, reader);
                    break;
                }
                case RELATIVE_TO: {
                    FileAuditLogHandlerResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, add, reader);
                    break;
                }
                case ROTATE_AT_STARTUP: {
                    FileAuditLogHandlerResourceDefinition.ROTATE_AT_STARTUP.parseAndSetParameter(value, add, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);
    }

    private void writeFileAuditLogHandler(XMLExtendedStreamWriter writer, ModelNode auditLog, String name) throws XMLStreamException {
        // added ROTATE_AT_STARTUP attribute

        if (auditLog.hasDefined(ModelDescriptionConstants.FILE_HANDLER, name)) {
            writer.writeStartElement(Element.FILE_HANDLER.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), name);
            ModelNode handler = auditLog.get(ModelDescriptionConstants.FILE_HANDLER, name);
            FileAuditLogHandlerResourceDefinition.FORMATTER.marshallAsAttribute(handler, writer);
            FileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.marshallAsAttribute(handler, writer);
            FileAuditLogHandlerResourceDefinition.PATH.marshallAsAttribute(handler, writer);
            FileAuditLogHandlerResourceDefinition.RELATIVE_TO.marshallAsAttribute(handler, writer);
            FileAuditLogHandlerResourceDefinition.ROTATE_AT_STARTUP.marshallAsAttribute(handler, writer);
            writer.writeEndElement();
        }
    }

    @Override
    public void parseAuditLog(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        requireNamespace(reader, expectedNs);

        final ModelNode auditLogAddress = address.clone().add(AccessAuditResourceDefinition.PATH_ELEMENT.getKey(), AccessAuditResourceDefinition.PATH_ELEMENT.getValue());

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        add.get(OP_ADDR).set(auditLogAddress);
        list.add(add);

        requireNoAttributes(reader);


        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
            case FORMATTERS:
                parseAuditLogFormatters(reader, auditLogAddress, expectedNs, list);
                break;
            case HANDLERS:{
                parseAuditLogHandlers(reader, auditLogAddress, expectedNs, list);
                break;
            }
            case LOGGER:{
                parseAuditLogConfig(reader, auditLogAddress, expectedNs, AuditLogLoggerResourceDefinition.PATH_ELEMENT, list);
                break;
            }
            case SERVER_LOGGER:{
                if (host){
                    parseAuditLogConfig(reader, auditLogAddress, expectedNs, AuditLogLoggerResourceDefinition.HOST_SERVER_PATH_ELEMENT, list);
                    break;
                }
                //Otherwise fallback to server-logger not recognised in standalone.xml
            }
            default:
                throw unexpectedElement(reader);
            }
        }
    }

    private void parseAuditLogFormatters(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNamespace(reader, expectedNs);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
            case JSON_FORMATTER:{
                parseFileAuditLogFormatter(reader, address, list);
                break;
            }
            default:
                throw unexpectedElement(reader);
            }
        }
    }

    private void parseFileAuditLogFormatter(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = Util.createAddOperation();
        list.add(add);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.JSON_FORMATTER, value);
                    break;
                }
                case COMPACT:{
                    JsonAuditLogFormatterResourceDefinition.COMPACT.parseAndSetParameter(value, add, reader);
                    break;
                }
                case DATE_FORMAT:{
                    JsonAuditLogFormatterResourceDefinition.DATE_FORMAT.parseAndSetParameter(value, add, reader);
                    break;
                }
                case DATE_SEPARATOR:{
                    JsonAuditLogFormatterResourceDefinition.DATE_SEPARATOR.parseAndSetParameter(value, add, reader);
                    break;
                }
                case ESCAPE_CONTROL_CHARACTERS:{
                    JsonAuditLogFormatterResourceDefinition.ESCAPE_CONTROL_CHARACTERS.parseAndSetParameter(value, add, reader);
                    break;
                }
                case ESCAPE_NEW_LINE:{
                    JsonAuditLogFormatterResourceDefinition.ESCAPE_NEW_LINE.parseAndSetParameter(value, add, reader);
                    break;
                }
                case INCLUDE_DATE:{
                    JsonAuditLogFormatterResourceDefinition.INCLUDE_DATE.parseAndSetParameter(value, add, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);
    }

    private void parseAuditLogHandlers(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {

        requireNamespace(reader, expectedNs);   //FIXME is this needed? what it does?
        boolean configurationChangesConfigured = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
            case IN_MEMORY_HANDLER:
                if(configurationChangesConfigured) {
                    throw unexpectedElement(reader);
                }
                parseConfigurationChangesAuditLogHandler(reader, address, list);
                configurationChangesConfigured = true;
                break;
            case FILE_HANDLER:
                parseFileAuditLogHandler(reader, address, list);
                break;
            case PERIODIC_ROTATING_FILE_HANDLER:
                parsePeriodicRotatingFileAuditLogHandler(reader, address, list);
                break;
            case SIZE_ROTATING_FILE_HANDLER:
                parseSizeRotatingFileAuditLogHandler(reader, address, list);
                break;
            case SYSLOG_HANDLER:
                parseSyslogAuditLogHandler(reader, address, expectedNs, list);
                break;
            default:
                throw unexpectedElement(reader);
            }
        }
    }

    private void parseConfigurationChangesAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = Util.createAddOperation();
        list.add(add);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.IN_MEMORY_HANDLER, value);
                    break;
                }
                case MAX_HISTORY: {
                    InMemoryAuditLogHandlerResourceDefinition.MAX_OPERATION_COUNT.parseAndSetParameter(value, add, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }
        requireNoContent(reader);
    }

    private void parseSizeRotatingFileAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = Util.createAddOperation();
        list.add(add);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME:
                    add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER, value);
                    break;
                case MAX_FAILURE_COUNT:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.parseAndSetParameter(value, add, reader);
                    break;
                case FORMATTER:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.FORMATTER.parseAndSetParameter(value, add, reader);
                    break;
                case PATH:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.PATH.parseAndSetParameter(value, add, reader);
                    break;
                case RELATIVE_TO:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, add, reader);
                    break;
                case ROTATE_SIZE:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.ROTATE_SIZE.parseAndSetParameter(value, add, reader);
                    break;
                case MAX_BACKUP_INDEX:
                    SizeRotatingFileAuditLogHandlerResourceDefinition.MAX_BACKUP_INDEX.parseAndSetParameter(value, add, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        requireNoContent(reader);
    }

    private void parsePeriodicRotatingFileAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = Util.createAddOperation();
        list.add(add);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME:
                    add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, value);
                    break;
                case MAX_FAILURE_COUNT:
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.parseAndSetParameter(value, add, reader);
                    break;
                case FORMATTER:
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.FORMATTER.parseAndSetParameter(value, add, reader);
                    break;
                case PATH:
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.PATH.parseAndSetParameter(value, add, reader);
                    break;
                case RELATIVE_TO:
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, add, reader);
                    break;
                case SUFFIX:
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.SUFFIX.parseAndSetParameter(value, add, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        requireNoContent(reader);
    }

    private void parseSyslogAuditLogHandlerAttributes(final XMLExtendedStreamReader reader, final ModelNode address, final ModelNode addOp) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    addOp.get(OP_ADDR).set(address).add(ModelDescriptionConstants.SYSLOG_HANDLER, value);
                    break;
                }
                case MAX_FAILURE_COUNT: {
                    SyslogAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case FORMATTER:{
                    SyslogAuditLogHandlerResourceDefinition.FORMATTER.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case MAX_LENGTH: {
                    SyslogAuditLogHandlerResourceDefinition.MAX_LENGTH.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case TRUNCATE: {
                    SyslogAuditLogHandlerResourceDefinition.TRUNCATE.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case FACILITY: {
                    SyslogAuditLogHandlerResourceDefinition.FACILITY.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case APP_NAME: {
                    SyslogAuditLogHandlerResourceDefinition.APP_NAME.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                case SYSLOG_FORMAT: {
                    SyslogAuditLogHandlerResourceDefinition.SYSLOG_FORMAT.parseAndSetParameter(value, addOp, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }
    }

    private void parseSyslogAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = Util.createAddOperation();
        list.add(add);

        parseSyslogAuditLogHandlerAttributes(reader, address, add);

        if (!add.get(OP_ADDR).isDefined()) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        boolean protocolSet = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());

            //Check there is only one protocol
            if (protocolSet) {
                throw DomainManagementLogger.ROOT_LOGGER.onlyOneSyslogHandlerProtocol(reader.getLocation());
            }
            protocolSet = true;

            switch (element) {
                case UDP:
                case TCP:
                case TLS: {
                    parseSyslogAuditLogHandlerProtocol(reader, add.get(OP_ADDR), expectedNs, list, element);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseSyslogAuditLogHandlerProtocol(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list, final Element protocolElement) throws XMLStreamException {
        PathAddress protocolAddress = PathAddress.pathAddress(address.clone().add(PROTOCOL, protocolElement.getLocalName()));
        ModelNode add = Util.createAddOperation(protocolAddress);
        list.add(add);
        final int tcpCount = reader.getAttributeCount();
        for (int i = 0; i < tcpCount; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case HOST: {
                    SyslogAuditLogProtocolResourceDefinition.Udp.HOST.parseAndSetParameter(value, add, reader);
                    break;
                }
                case PORT: {
                    SyslogAuditLogProtocolResourceDefinition.Udp.PORT.parseAndSetParameter(value, add, reader);
                    break;
                }
                case MESSAGE_TRANSFER : {
                    if (protocolElement != Element.UDP) {
                        SyslogAuditLogProtocolResourceDefinition.Tcp.MESSAGE_TRANSFER.parseAndSetParameter(value, add, reader);
                        break;
                    }
                }
                case RECONNECT_TIMEOUT:
                    if (protocolElement != Element.UDP) {
                        SyslogAuditLogProtocolResourceDefinition.Tcp.RECONNECT_TIMEOUT.parseAndSetParameter(value, add, reader);
                        break;
                    }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (protocolElement != Element.TLS) {
            requireNoContent(reader);
        } else {
            boolean seenTrustStore = false;
            boolean seenClientCertStore = false;
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, expectedNs);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                case TRUSTSTORE:{
                    if (seenTrustStore) {
                        throw duplicateNamedElement(reader, Element.TRUSTSTORE.getLocalName());
                    }
                    seenTrustStore = true;
                    parseSyslogTlsKeystore(reader, protocolAddress, expectedNs, list, SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.TRUSTSTORE_ELEMENT, false);
                    break;
                }
                case CLIENT_CERT_STORE : {
                    if (seenClientCertStore) {
                        throw duplicateNamedElement(reader, Element.CLIENT_CERT_STORE.getLocalName());
                    }
                    seenClientCertStore = true;
                    parseSyslogTlsKeystore(reader, protocolAddress, expectedNs, list, SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.CLIENT_CERT_ELEMENT, true);
                    break;
                }
                default:
                    throw unexpectedElement(reader);
                }
            }
        }
    }
    private void parseSyslogTlsKeystore(final XMLExtendedStreamReader reader, final PathAddress address, final Namespace expectedNs, final List<ModelNode> list, final PathElement storeAddress, final boolean hasKeyPassword) throws XMLStreamException {
        ModelNode add = Util.createAddOperation(address.append(storeAddress));
        list.add(add);
        final int tcpCount = reader.getAttributeCount();
        for (int i = 0; i < tcpCount; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case KEYSTORE_PASSWORD: {
                    SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_PASSWORD.parseAndSetParameter(value, add, reader);
                    break;
                }
                case PATH: {
                    SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_PATH.parseAndSetParameter(value, add, reader);
                    break;
                }
                case RELATIVE_TO : {
                    SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_RELATIVE_TO.parseAndSetParameter(value, add, reader);
                    break;
                }
                case KEY_PASSWORD: {
                    if (hasKeyPassword){
                        SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEY_PASSWORD.parseAndSetParameter(value, add, reader);
                        break;
                    }
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE: {
                    KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getParser().parseElement(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, reader, add);
                    break;
                }
                case KEY_PASSWORD_CREDENTIAL_REFERENCE: {
                    KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE.getParser().parseElement(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE, reader, add);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseAuditLogConfig(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final PathElement pathElement, final List<ModelNode> list) throws XMLStreamException {

        requireNamespace(reader, expectedNs);

        final ModelNode configAddress = address.clone().add(pathElement.getKey(), pathElement.getValue());

        final ModelNode add = new ModelNode();
        add.get(OP).set(ADD);
        add.get(OP_ADDR).set(configAddress);

        list.add(add);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case LOG_READ_ONLY: {
                    AuditLogLoggerResourceDefinition.LOG_READ_ONLY.parseAndSetParameter(value, add, reader);
                    break;
                }
                case LOG_BOOT: {
                    AuditLogLoggerResourceDefinition.LOG_BOOT.parseAndSetParameter(value, add, reader);
                    break;
                }
                case ENABLED: {
                    AuditLogLoggerResourceDefinition.ENABLED.parseAndSetParameter(value, add, reader);
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
            case HANDLERS:{
                parseAuditLogHandlersReference(reader, configAddress, expectedNs, list);
                break;
            }
            default:
                throw unexpectedElement(reader);
            }
        }
    }

    private void parseAuditLogHandlersReference(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        requireNamespace(reader, expectedNs);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
            case HANDLER:{
                requireNamespace(reader, expectedNs);
                final ModelNode add = new ModelNode();
                add.get(OP).set(ADD);
                list.add(add);

                final int count = reader.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    final String value = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw unexpectedAttribute(reader, i);
                    }
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    switch (attribute) {
                        case NAME: {
                            add.get(OP_ADDR).set(address).add(ModelDescriptionConstants.HANDLER, value);
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    requireNoContent(reader);
                }
                break;
            }
            default:
                throw unexpectedElement(reader);
            }
        }
    }

    @Override
    public void writeAuditLog(XMLExtendedStreamWriter writer, ModelNode auditLog) throws XMLStreamException {
        writer.writeStartElement(Element.AUDIT_LOG.getLocalName());

        if (auditLog.hasDefined(ModelDescriptionConstants.JSON_FORMATTER) && !auditLog.get(ModelDescriptionConstants.JSON_FORMATTER).keys().isEmpty()) {
            writer.writeStartElement(Element.FORMATTERS.getLocalName());
            for (Property prop : auditLog.get(ModelDescriptionConstants.JSON_FORMATTER).asPropertyList()) {
                writer.writeStartElement(Element.JSON_FORMATTER.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), prop.getName());
                JsonAuditLogFormatterResourceDefinition.COMPACT.marshallAsAttribute(prop.getValue(), writer);
                JsonAuditLogFormatterResourceDefinition.DATE_FORMAT.marshallAsAttribute(prop.getValue(), writer);
                JsonAuditLogFormatterResourceDefinition.DATE_SEPARATOR.marshallAsAttribute(prop.getValue(), writer);
                JsonAuditLogFormatterResourceDefinition.ESCAPE_CONTROL_CHARACTERS.marshallAsAttribute(prop.getValue(), writer);
                JsonAuditLogFormatterResourceDefinition.ESCAPE_NEW_LINE.marshallAsAttribute(prop.getValue(), writer);
                JsonAuditLogFormatterResourceDefinition.INCLUDE_DATE.marshallAsAttribute(prop.getValue(), writer);
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }


        if ((auditLog.hasDefined(ModelDescriptionConstants.FILE_HANDLER) && !auditLog.get(ModelDescriptionConstants.FILE_HANDLER).keys().isEmpty()) ||
                (auditLog.hasDefined(ModelDescriptionConstants.SYSLOG_HANDLER) && !auditLog.get(ModelDescriptionConstants.SYSLOG_HANDLER).keys().isEmpty()) ||
                (auditLog.hasDefined(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER) && !auditLog.get(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER).keys().isEmpty()) ||
                (auditLog.hasDefined(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER) && !auditLog.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER).keys().isEmpty()) ||
                (auditLog.hasDefined(ModelDescriptionConstants.IN_MEMORY_HANDLER) && !auditLog.get(ModelDescriptionConstants.IN_MEMORY_HANDLER).keys().isEmpty())) {
            writer.writeStartElement(Element.HANDLERS.getLocalName());
            if (auditLog.hasDefined(ModelDescriptionConstants.FILE_HANDLER)) {
                for (String name : auditLog.get(ModelDescriptionConstants.FILE_HANDLER).keys()) {
                    writeFileAuditLogHandler(writer, auditLog, name);
                }
            }
            if (auditLog.hasDefined(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER)) {
                for (String name : auditLog.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER).keys()) {
                    writer.writeStartElement(Element.PERIODIC_ROTATING_FILE_HANDLER.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    ModelNode handler = auditLog.get(ModelDescriptionConstants.PERIODIC_ROTATING_FILE_HANDLER, name);
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.FORMATTER.marshallAsAttribute(handler, writer);
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.marshallAsAttribute(handler, writer);
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.PATH.marshallAsAttribute(handler, writer);
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.RELATIVE_TO.marshallAsAttribute(handler, writer);
                    PeriodicRotatingFileAuditLogHandlerResourceDefinition.SUFFIX.marshallAsAttribute(handler, writer);
                    writer.writeEndElement();
                }
            }
            if (auditLog.hasDefined(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER)) {
                for (String name : auditLog.get(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER).keys()) {
                    writer.writeStartElement(Element.SIZE_ROTATING_FILE_HANDLER.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    ModelNode handler = auditLog.get(ModelDescriptionConstants.SIZE_ROTATING_FILE_HANDLER, name);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.FORMATTER.marshallAsAttribute(handler, writer);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.marshallAsAttribute(handler, writer);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.PATH.marshallAsAttribute(handler, writer);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.RELATIVE_TO.marshallAsAttribute(handler, writer);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.ROTATE_SIZE.marshallAsAttribute(handler, writer);
                    SizeRotatingFileAuditLogHandlerResourceDefinition.MAX_BACKUP_INDEX.marshallAsAttribute(handler, writer);
                    writer.writeEndElement();
                }
            }
            if (auditLog.hasDefined(ModelDescriptionConstants.SYSLOG_HANDLER)) {
                for (String name : auditLog.get(ModelDescriptionConstants.SYSLOG_HANDLER).keys()) {
                    writer.writeStartElement(Element.SYSLOG_HANDLER.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    ModelNode handler = auditLog.get(ModelDescriptionConstants.SYSLOG_HANDLER, name);
                    SyslogAuditLogHandlerResourceDefinition.FORMATTER.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.MAX_FAILURE_COUNT.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.SYSLOG_FORMAT.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.MAX_LENGTH.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.TRUNCATE.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.FACILITY.marshallAsAttribute(handler, writer);
                    SyslogAuditLogHandlerResourceDefinition.APP_NAME.marshallAsAttribute(handler, writer);
                    if (handler.hasDefined(PROTOCOL)) {
                        writeAuditLogSyslogProtocol(writer, handler.get(PROTOCOL));
                    }
                    writer.writeEndElement();
                }
            }
            if (auditLog.hasDefined(ModelDescriptionConstants.IN_MEMORY_HANDLER)) {
                for (String name : auditLog.get(ModelDescriptionConstants.IN_MEMORY_HANDLER).keys()) {
                    writer.writeStartElement(Element.IN_MEMORY_HANDLER.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    ModelNode handler = auditLog.get(ModelDescriptionConstants.IN_MEMORY_HANDLER, name);
                    InMemoryAuditLogHandlerResourceDefinition.MAX_OPERATION_COUNT.marshallAsAttribute(handler, writer);
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();
        }
        writeAuditLogger(writer, auditLog, Element.LOGGER.getLocalName());
        writeAuditLogger(writer, auditLog, Element.SERVER_LOGGER.getLocalName());
        writer.writeEndElement();
    }

    private void writeAuditLogger(XMLExtendedStreamWriter writer, ModelNode auditLog, String element) throws XMLStreamException {
        if (auditLog.hasDefined(element) && auditLog.get(element).hasDefined(ModelDescriptionConstants.AUDIT_LOG)){
            ModelNode config = auditLog.get(element, ModelDescriptionConstants.AUDIT_LOG);
            writer.writeStartElement(element);
            AuditLogLoggerResourceDefinition.LOG_BOOT.marshallAsAttribute(config, writer);
            AuditLogLoggerResourceDefinition.LOG_READ_ONLY.marshallAsAttribute(config, writer);
            AuditLogLoggerResourceDefinition.ENABLED.marshallAsAttribute(config, writer);
            if (config.hasDefined(ModelDescriptionConstants.HANDLER) && !config.get(ModelDescriptionConstants.HANDLER).keys().isEmpty()) {
                writer.writeStartElement(Element.HANDLERS.getLocalName());
                for (String name : config.get(ModelDescriptionConstants.HANDLER).keys()) {
                    writer.writeStartElement(Element.HANDLER.getLocalName());
                    writer.writeAttribute(Attribute.NAME.getLocalName(), name);
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }

            writer.writeEndElement();
        }
    }


    private void writeAuditLogSyslogProtocol(XMLExtendedStreamWriter writer, ModelNode protocol) throws XMLStreamException {
        String type = protocol.keys().iterator().next();
        ModelNode protocolContents = protocol.get(type);
        if (type.equals(ModelDescriptionConstants.UDP)) {
            writer.writeStartElement(type);
            SyslogAuditLogProtocolResourceDefinition.Udp.HOST.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Udp.PORT.marshallAsAttribute(protocolContents, writer);
            writer.writeEndElement();
        } else if (type.equals(ModelDescriptionConstants.TCP)) {
            writer.writeStartElement(type);
            SyslogAuditLogProtocolResourceDefinition.Tcp.HOST.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tcp.PORT.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tcp.MESSAGE_TRANSFER.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tcp.RECONNECT_TIMEOUT.marshallAsAttribute(protocolContents, writer);
            writer.writeEndElement();
        } else if (type.equals(ModelDescriptionConstants.TLS)) {
            writer.writeStartElement(type);
            SyslogAuditLogProtocolResourceDefinition.Tls.HOST.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.PORT.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.MESSAGE_TRANSFER.marshallAsAttribute(protocolContents, writer);
            SyslogAuditLogProtocolResourceDefinition.Tcp.RECONNECT_TIMEOUT.marshallAsAttribute(protocolContents, writer);

            if (protocolContents.hasDefined(AUTHENTICATION)) {
                writeAuditLogSyslogTlsProtocolKeyStore(writer, protocolContents.get(AUTHENTICATION), TRUSTSTORE);
                writeAuditLogSyslogTlsProtocolKeyStore(writer, protocolContents.get(AUTHENTICATION), CLIENT_CERT_STORE);
            }

            writer.writeEndElement();
        }
    }

    private void writeAuditLogSyslogTlsProtocolKeyStore(XMLExtendedStreamWriter writer, ModelNode keystoreParent, String name) throws XMLStreamException {
        if (keystoreParent.hasDefined(name)) {
            ModelNode keystore = keystoreParent.get(name);
            writer.writeStartElement(name);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_PATH.marshallAsAttribute(keystore, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_RELATIVE_TO.marshallAsAttribute(keystore, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_PASSWORD.marshallAsAttribute(keystore, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEY_PASSWORD.marshallAsAttribute(keystore, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.marshallAsElement(keystore, writer);
            SyslogAuditLogProtocolResourceDefinition.Tls.TlsKeyStore.KEY_PASSWORD_CREDENTIAL_REFERENCE.marshallAsElement(keystore, writer);
            writer.writeEndElement();
        }
    }
}
