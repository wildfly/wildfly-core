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
