/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_1_3 extends LoggingSubsystemParser_1_2 {

    LoggingSubsystemParser_1_3() {
        //
    }

    @Override
    void parseSizeRotatingHandlerElement(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case ENABLED: {
                    ENABLED.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case ROTATE_ON_BOOT: {
                    ROTATE_ON_BOOT.parseAndSetParameter(value, operation, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation address
        addOperationAddress(operation, address, SizeRotatingHandlerResourceDefinition.NAME, name);

        final EnumSet<Element> requiredElem = EnumSet.of(Element.FILE);
        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            requiredElem.remove(element);
            switch (element) {
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case ENCODING: {
                    ENCODING.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FILTER_SPEC: {
                    SizeRotatingHandlerResourceDefinition.FILTER_SPEC.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FORMATTER: {
                    parseHandlerFormatterElement(reader, operation);
                    break;
                }
                case FILE: {
                    parseFileElement(operation.get(FILE.getName()), reader);
                    break;
                }
                case APPEND: {
                    APPEND.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case ROTATE_SIZE: {
                    ROTATE_SIZE.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case MAX_BACKUP_INDEX: {
                    MAX_BACKUP_INDEX.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }
}
