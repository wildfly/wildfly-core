/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_SECURITY_EVENT_LISTENER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_EVENT_LISTENER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SYSLOG_AUDIT_LOG;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * XML Handling for the audit logging resources.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuditLoggingParser {

    private final PersistentResourceXMLDescription fileAuditLogParser = builder(PathElement.pathElement(FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT)
            .build();

    private final PersistentResourceXMLDescription fileAuditLogParser_5_0 = builder(PathElement.pathElement(FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT)
            .build();

    private final PersistentResourceXMLDescription fileAuditLogParser_18_0 = builder(PathElement.pathElement(FILE_AUDIT_LOG))
        .setUseElementsForGroups(false)
        .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.ENCODING)
        .build();

    private final PersistentResourceXMLDescription periodicRotatingFileAuditLogParser = builder(PathElement.pathElement(PERIODIC_ROTATING_FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.PERIODIC_SUFFIX)
            .build();

    private final PersistentResourceXMLDescription periodicRotatingFileAuditLogParser_5_0 = builder(PathElement.pathElement(PERIODIC_ROTATING_FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.PERIODIC_SUFFIX)
            .build();

    private final PersistentResourceXMLDescription periodicRotatingFileAuditLogParser_18_0 = builder(PathElement.pathElement(PERIODIC_ROTATING_FILE_AUDIT_LOG))
        .setUseElementsForGroups(false)
        .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.ENCODING, AuditResourceDefinitions.PERIODIC_SUFFIX)
        .build();

    private final PersistentResourceXMLDescription sizeRotatingFileAuditLogParser = builder(PathElement.pathElement(SIZE_ROTATING_FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.MAX_BACKUP_INDEX, AuditResourceDefinitions.ROTATE_ON_BOOT, AuditResourceDefinitions.ROTATE_SIZE, AuditResourceDefinitions.SIZE_SUFFIX)
            .build();

    private final PersistentResourceXMLDescription sizeRotatingFileAuditLogParser_5_0 = builder(PathElement.pathElement(SIZE_ROTATING_FILE_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.MAX_BACKUP_INDEX, AuditResourceDefinitions.ROTATE_ON_BOOT, AuditResourceDefinitions.ROTATE_SIZE, AuditResourceDefinitions.SIZE_SUFFIX)
            .build();

    private final PersistentResourceXMLDescription syslogAuditLogParser = builder(PathElement.pathElement(SYSLOG_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.SERVER_ADDRESS, AuditResourceDefinitions.PORT, AuditResourceDefinitions.TRANSPORT, AuditResourceDefinitions.HOST_NAME, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.SSL_CONTEXT)
            .build();

    private final PersistentResourceXMLDescription sizeRotatingFileAuditLogParser_18_0 = builder(PathElement.pathElement(SIZE_ROTATING_FILE_AUDIT_LOG))
        .setUseElementsForGroups(false)
        .addAttributes(AuditResourceDefinitions.PATH, FileAttributeDefinitions.RELATIVE_TO, AuditResourceDefinitions.AUTOFLUSH, AuditResourceDefinitions.SYNCHRONIZED, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.ENCODING, AuditResourceDefinitions.MAX_BACKUP_INDEX, AuditResourceDefinitions.ROTATE_ON_BOOT, AuditResourceDefinitions.ROTATE_SIZE, AuditResourceDefinitions.SIZE_SUFFIX)
        .build();

    private final PersistentResourceXMLDescription syslogAuditLogParser_8_0 = builder(PathElement.pathElement(SYSLOG_AUDIT_LOG))
            .setUseElementsForGroups(false)
            .addAttributes(AuditResourceDefinitions.SERVER_ADDRESS, AuditResourceDefinitions.PORT, AuditResourceDefinitions.TRANSPORT, AuditResourceDefinitions.HOST_NAME, AuditResourceDefinitions.FORMAT, AuditResourceDefinitions.SSL_CONTEXT, AuditResourceDefinitions.SYSLOG_FORMAT, AuditResourceDefinitions.RECONNECT_ATTEMPTS)
            .build();


    private final PersistentResourceXMLDescription aggregateSecurityEventParser = builder(PathElement.pathElement(AGGREGATE_SECURITY_EVENT_LISTENER))
            .addAttribute(AuditResourceDefinitions.REFERENCES, new AttributeParsers.NamedStringListParser(SECURITY_EVENT_LISTENER), new AttributeMarshallers.NamedStringListMarshaller(SECURITY_EVENT_LISTENER))
            .build();

    private final PersistentResourceXMLDescription customSecurityEventParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_SECURITY_EVENT_LISTENER))
            .addAttributes(CustomComponentDefinition.ATTRIBUTES)
            .setUseElementsForGroups(false)
            .build();

    final PersistentResourceXMLDescription parser = decorator(ElytronDescriptionConstants.AUDIT_LOGGING)
            .addChild(aggregateSecurityEventParser)
            .addChild(fileAuditLogParser)
            .addChild(periodicRotatingFileAuditLogParser)
            .addChild(sizeRotatingFileAuditLogParser)
            .addChild(syslogAuditLogParser)
            .build();

    final PersistentResourceXMLDescription parser4_0 = decorator(ElytronDescriptionConstants.AUDIT_LOGGING)
            .addChild(aggregateSecurityEventParser)
            .addChild(customSecurityEventParser) // new
            .addChild(fileAuditLogParser)
            .addChild(periodicRotatingFileAuditLogParser)
            .addChild(sizeRotatingFileAuditLogParser)
            .addChild(syslogAuditLogParser)
            .build();

    final PersistentResourceXMLDescription parser5_0 = decorator(ElytronDescriptionConstants.AUDIT_LOGGING)
            .addChild(aggregateSecurityEventParser)
            .addChild(customSecurityEventParser)
            .addChild(fileAuditLogParser_5_0)
            .addChild(periodicRotatingFileAuditLogParser_5_0)
            .addChild(sizeRotatingFileAuditLogParser_5_0)
            .addChild(syslogAuditLogParser)
            .build();

    final PersistentResourceXMLDescription parser8_0 = decorator(ElytronDescriptionConstants.AUDIT_LOGGING)
            .addChild(aggregateSecurityEventParser)
            .addChild(customSecurityEventParser)
            .addChild(fileAuditLogParser_5_0)
            .addChild(periodicRotatingFileAuditLogParser_5_0)
            .addChild(sizeRotatingFileAuditLogParser_5_0)
            .addChild(syslogAuditLogParser_8_0)
            .build();

    final PersistentResourceXMLDescription parser18_0 = decorator(ElytronDescriptionConstants.AUDIT_LOGGING)
        .addChild(aggregateSecurityEventParser)
        .addChild(customSecurityEventParser)
        .addChild(fileAuditLogParser_18_0)
        .addChild(periodicRotatingFileAuditLogParser_18_0)
        .addChild(sizeRotatingFileAuditLogParser_18_0)
        .addChild(syslogAuditLogParser_8_0)
        .build();
}
