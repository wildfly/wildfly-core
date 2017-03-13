/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the DirContext definition.
 *
 * <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
class DirContextParser {

    private final PersistentResourceXMLDescription dirContextParser = builder(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT), null)
            .addAttributes(DirContextDefinition.ATTRIBUTES)
            .build();

    void readDirContexts(ModelNode parentAddressNode, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            PathAddress parentAddress = PathAddress.pathAddress(parentAddressNode);
            if (DIR_CONTEXT.equals(localName)) {
                dirContextParser.parse(reader, parentAddress, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    void writeDirContexts(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(DIR_CONTEXT)) {
            writer.writeStartElement(DIR_CONTEXTS);

            dirContextParser.persist(writer, subsystem);

            writer.writeEndElement();
        }
    }

}
