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
 * Analogous to {@link org.jboss.as.controller.PersistentSubsystemSchema}, but using {@link ResourceXMLElement} instead of {@link org.jboss.as.controller.PersistentResourceXMLDescription}.
 * @author Paul Ferraro
 */
public interface SubsystemResourceXMLSchema<S extends SubsystemResourceXMLSchema<S>> extends SubsystemSchema<S> {

    ResourceXMLElement getSubsystemResourceXMLElement();

    @Override
    default void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        new SubsystemResourceXMLElementReader(this.getSubsystemResourceXMLElement()).readElement(reader, operations);
    }
}
