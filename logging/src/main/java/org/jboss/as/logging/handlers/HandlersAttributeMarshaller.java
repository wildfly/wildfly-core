/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class HandlersAttributeMarshaller extends DefaultAttributeMarshaller {

    private final AttributeDefinition valueType;

    HandlersAttributeMarshaller(final AttributeDefinition valueType) {
        this.valueType = valueType;
    }

    @Override
    public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallable(attribute, resourceModel, marshallDefault)) {
            writer.writeStartElement(attribute.getXmlName());
            final ModelNode handlers = resourceModel.get(attribute.getName());
            for (ModelNode handler : handlers.asList()) {
                if (handler.isDefined()) {
                    writer.writeStartElement(valueType.getXmlName());
                    writer.writeAttribute(CommonAttributes.HANDLER_NAME.getXmlName(), handler.asString());
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();
        }
    }
}
