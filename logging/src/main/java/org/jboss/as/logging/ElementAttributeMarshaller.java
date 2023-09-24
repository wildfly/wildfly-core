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

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ElementAttributeMarshaller extends DefaultAttributeMarshaller {

    public static final ElementAttributeMarshaller NAME_ATTRIBUTE_MARSHALLER = new ElementAttributeMarshaller("name");

    public static final ElementAttributeMarshaller VALUE_ATTRIBUTE_MARSHALLER = new ElementAttributeMarshaller("value");

    private final String attributeValueName;

    private ElementAttributeMarshaller(final String attributeValueName) {
        this.attributeValueName = attributeValueName;
    }

    @Override
    public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallable(attribute, resourceModel, marshallDefault)) {
            writer.writeStartElement(attribute.getXmlName());
            String content = resourceModel.get(attribute.getName()).asString();
            writer.writeAttribute(attributeValueName, content);
            writer.writeEndElement();
        }
    }
}
