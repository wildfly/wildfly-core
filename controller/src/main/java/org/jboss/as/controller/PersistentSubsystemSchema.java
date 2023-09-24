/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Defines a versioned schema for a subsystem defined via a {@link PersistentResourceXMLDescription}.
 * @author Paul Ferraro
 * @param <S> the schema type
 */
public interface PersistentSubsystemSchema<S extends PersistentSubsystemSchema<S>> extends SubsystemSchema<S> {

    PersistentResourceXMLDescription getXMLDescription();

    @Override
    default void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
        new PersistentResourceXMLDescriptionReader(this.getXMLDescription()).readElement(reader, value);
    }
}
