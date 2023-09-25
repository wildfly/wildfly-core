/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.common.function.Functions;

/**
 * An {@link XMLElementReader} based on a {@link PersistentResourceXMLDescription}.
 * @author Paul Ferraro
 */
public class PersistentResourceXMLDescriptionReader implements XMLElementReader<List<ModelNode>> {
    private final Supplier<PersistentResourceXMLDescription> description;

    /**
     * Creates an {@link XMLElementReader} using the specified {@link PersistentResourceXMLDescription} supplier.
     * @param description a {@link PersistentResourceXMLDescription} supplier
     */
    public PersistentResourceXMLDescriptionReader(Supplier<PersistentResourceXMLDescription> description) {
        this.description = description;
    }

    /**
     * Creates an {@link XMLElementReader} using the specified {@link PersistentResourceXMLDescription}.
     * @param description a {@link PersistentResourceXMLDescription}
     */
    public PersistentResourceXMLDescriptionReader(PersistentResourceXMLDescription description) {
        this(Functions.constantSupplier(description));
    }

    /**
     * Creates an {@link XMLElementReader} for the specified subsystem schema.
     * @param schema a subsystem schema
     */
    public <S extends PersistentSubsystemSchema<S>> PersistentResourceXMLDescriptionReader(PersistentSubsystemSchema<S> schema) {
        this(schema::getXMLDescription);
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        this.description.get().parse(reader, PathAddress.EMPTY_ADDRESS, operations);
    }
}