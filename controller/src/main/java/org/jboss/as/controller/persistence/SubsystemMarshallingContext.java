/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.Element;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Context passed to {@link org.jboss.staxmapper.XMLElementWriter}s that marshal a subsystem.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SubsystemMarshallingContext {

    private final ModelNode modelNode;
    private final XMLExtendedStreamWriter writer;

    public SubsystemMarshallingContext(final ModelNode modelNode, final XMLExtendedStreamWriter writer) {
        this.modelNode = modelNode;
        this.writer = writer;
    }

    public ModelNode getModelNode() {
        return modelNode;
    }

    public void startSubsystemElement(String namespaceURI, boolean empty) throws XMLStreamException {

        if (writer.getNamespaceContext().getPrefix(namespaceURI) == null) {
            // Unknown namespace; it becomes default
            writer.setDefaultNamespace(namespaceURI);
            if (empty) {
                writer.writeEmptyElement(Element.SUBSYSTEM.getLocalName());
            }
            else {
                writer.writeStartElement(Element.SUBSYSTEM.getLocalName());
            }
            writer.writeNamespace(null, namespaceURI);
        }
        else {
            if (empty) {
                writer.writeEmptyElement(namespaceURI, Element.SUBSYSTEM.getLocalName());
            }
            else {
                writer.writeStartElement(namespaceURI, Element.SUBSYSTEM.getLocalName());
            }
        }

    }
}
