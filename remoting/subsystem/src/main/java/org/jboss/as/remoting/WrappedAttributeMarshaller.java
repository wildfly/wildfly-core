/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.dmr.ModelNode;

/**
 * <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012 Red Hat, inc
 */
public class WrappedAttributeMarshaller extends AttributeMarshaller {

    private final Attribute xmlAttribute;

    WrappedAttributeMarshaller(Attribute xmlAttribute) {
        this.xmlAttribute = xmlAttribute;
    }

    @Override
    public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallable(attribute, resourceModel)) {
            writer.writeEmptyElement(attribute.getXmlName());
            writer.writeAttribute(xmlAttribute.getLocalName(), resourceModel.get(attribute.getName()).asString());
        }
    }
}
