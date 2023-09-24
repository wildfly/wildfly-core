/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.parsing;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.server.parsing.SocketBindingsXml;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Kabir Khan
 */
class DomainSocketBindingsXml extends SocketBindingsXml {
    @Override
    protected void writeExtraAttributes(XMLExtendedStreamWriter writer, ModelNode bindingGroup) throws XMLStreamException {
        SocketBindingGroupResourceDefinition.INCLUDES.marshallAsElement(bindingGroup, writer);
    }
}
