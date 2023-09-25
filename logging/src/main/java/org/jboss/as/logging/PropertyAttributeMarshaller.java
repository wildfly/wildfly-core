/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyAttributeMarshaller extends DefaultAttributeMarshaller {
    public static final PropertyAttributeMarshaller INSTANCE = new PropertyAttributeMarshaller();

    @Override
    public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
        resourceModel = resourceModel.get(attribute.getName());
        if (resourceModel.isDefined()) {
            writer.writeStartElement(attribute.getName());
            for (Property property : resourceModel.asPropertyList()) {
                writer.writeEmptyElement(Element.PROPERTY.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
                writer.writeAttribute(Attribute.VALUE.getLocalName(), property.getValue().asString());
            }
            writer.writeEndElement();
        }
    }
}
