/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller.operations.global;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.global.ReportAttacher.AbstractReportAttacher;
import org.jboss.dmr.ModelNode;

/**
 * Allow to produce a XML file to be attached to the response.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
class XMLReportAttacher extends AbstractReportAttacher {
    private XMLStreamWriter streamWriter = null;
    private final AttributeDefinition attributeDefinition;
    private final ByteArrayOutputStream buffer;

    XMLReportAttacher(AttributeDefinition attributeDefinition, boolean record, String namespaceURI, String rootNode) throws OperationFailedException {
        super(record);
        this.attributeDefinition = attributeDefinition;
        buffer = new ByteArrayOutputStream();
        if(record) {
            try {
                streamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(buffer, "UTF-8");
                streamWriter.writeStartDocument();
                streamWriter.writeStartElement(rootNode);
                streamWriter.writeDefaultNamespace(namespaceURI);
            } catch (XMLStreamException ex) {
                throw ControllerLogger.MGMT_OP_LOGGER.failedToBuildReport(ex);
            }
        }
    }

    @Override
    public void addReport(ModelNode report) throws OperationFailedException {
        if (record && streamWriter != null) {
            try {
                attributeDefinition.getMarshaller().marshallAsElement(attributeDefinition, report, true, streamWriter);
            } catch (XMLStreamException ex) {
                throw ControllerLogger.MGMT_OP_LOGGER.failedToBuildReport(ex);
            }
        }
    }

    @Override
    public InputStream getContent() {
        if (record && streamWriter != null) {
            try {
                streamWriter.writeEndElement();
                streamWriter.writeEndDocument();
                streamWriter.flush();
                return new ByteArrayInputStream(buffer.toByteArray());
            } catch (XMLStreamException ex) {
                Logger.getLogger(XMLReportAttacher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return new ByteArrayInputStream(EMPTY);
    }

    @Override
    protected String getMimeType() {
        return "application/xml";
    }
}
