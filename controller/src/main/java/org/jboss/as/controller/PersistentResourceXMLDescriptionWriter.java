/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
