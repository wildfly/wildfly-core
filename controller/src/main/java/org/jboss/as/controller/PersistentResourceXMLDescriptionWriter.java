/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.function.Functions;

/**
 * An {@link XMLElementWriter} based on a {@link PersistentResourceXMLDescription}.
 * @author Paul Ferraro
 */
public class PersistentResourceXMLDescriptionWriter implements XMLElementWriter<SubsystemMarshallingContext> {
    private final Supplier<PersistentResourceXMLDescription> description;

    /**
     * Creates an {@link XMLElementWriter} using the specified {@link PersistentResourceXMLDescription} supplier.
     * @param description a {@link PersistentResourceXMLDescription} supplier
     */
    public PersistentResourceXMLDescriptionWriter(Supplier<PersistentResourceXMLDescription> description) {
        this.description = description;
    }

    /**
     * Creates an {@link XMLElementWriter} using the specified {@link PersistentResourceXMLDescription}.
     * @param description a {@link PersistentResourceXMLDescription}
     */
    public PersistentResourceXMLDescriptionWriter(PersistentResourceXMLDescription description) {
        this(Functions.constantSupplier(description));
    }

    /**
     * Creates an {@link XMLElementWriter} using the specified subsystem schema.
     * @param schema a subsystem schema
     */
    public <S extends PersistentSubsystemSchema<S>> PersistentResourceXMLDescriptionWriter(PersistentSubsystemSchema<S> schema) {
        this(schema::getXMLDescription);
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        PersistentResourceXMLDescription description = this.description.get();
        ModelNode model = new ModelNode();
        model.get(description.getPathElement().getKeyValuePair()).set(context.getModelNode());
        description.persist(writer, model);
    }
}
