/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence.xml;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * A {@link org.jboss.as.controller.SubsystemSchema} described by a {@link SubsystemResourceRegistrationXMLElement}.
 * @author Paul Ferraro
 */
public interface SubsystemResourceXMLSchema<S extends SubsystemResourceXMLSchema<S>> extends SubsystemSchema<S> {

    SubsystemResourceRegistrationXMLElement getSubsystemXMLElement();

    @Override
    default void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        new SubsystemResourceXMLElementReader(this.getSubsystemXMLElement()).readElement(reader, operations);
    }
}
