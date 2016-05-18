/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
 */

package org.jboss.as.domain.management.parsing;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.domain.management.audit.FileAuditLogHandlerResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class AuditLogXml_5 extends AuditLogXml_4 {

    public AuditLogXml_5(boolean host) {
        super(host);
    }

    protected void parseFileAuditLogHandler(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
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

    protected void writeFileAuditLogHandler(XMLExtendedStreamWriter writer, ModelNode auditLog, String name) throws XMLStreamException {
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

}
