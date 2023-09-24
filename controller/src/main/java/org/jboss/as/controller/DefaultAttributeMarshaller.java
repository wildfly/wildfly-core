/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class DefaultAttributeMarshaller extends AttributeMarshaller {

    @Override
    public void marshallAsAttribute(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallable(attribute, resourceModel, marshallDefault)) {
            writer.writeAttribute(attribute.getXmlName(), this.asString(resourceModel.get(attribute.getName())));
        }
    }

    @Override
    public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallable(attribute, resourceModel, marshallDefault)) {
            writer.writeStartElement(attribute.getXmlName());
            marshallElementContent(this.asString(resourceModel.get(attribute.getName())), writer);
            writer.writeEndElement();
        }
    }

    @Override
    public boolean isMarshallableAsElement() {
        return false;
    }

    protected String asString(ModelNode value) {
        return value.asString();
    }
}
