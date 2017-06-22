/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;

import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Attributes used by {@link ResourceDefinition} instances that need a common home.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CommonAttributes {

    // TODO - Check we really want this and not other suitable common location is available.

    static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTIES, true)
            .setAllowExpression(true)
            .setRestartAllServices().build();

    static class AggregateAttributeParser extends AttributeParser {
        private final String elementName;

        AggregateAttributeParser(String elementName) {
            this.elementName = elementName;
        }

        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public String getXmlName(AttributeDefinition attribute) {
            return elementName;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            ModelNode listValue = operation.get(attribute.getName());
            if (!listValue.isDefined()) {
                listValue.setEmptyList();
            }
            String xmlName = getXmlName(attribute);
            if (xmlName.equals(reader.getLocalName())) {
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    String attributeName = reader.getAttributeLocalName(i);
                    if (attributeName.equals(ModelDescriptionConstants.NAME)) {
                        String value = reader.getAttributeValue(i);
                        listValue.add(value);
                        break;
                    } else {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    }

                }
            } else {
                throw ParseUtils.unexpectedElement(reader, Collections.singleton(xmlName));
            }
            if (!reader.isEndElement()) {
                ParseUtils.requireNoContent(reader);
            }
        }

    }

    static class AggregateAttributeMarshaller extends AttributeMarshaller {
        private final String elementName;

        AggregateAttributeMarshaller(String elementName) {
            this.elementName = elementName;
        }

        @Override
        public void marshall(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            List<ModelNode> serverFactoryReferences = resourceModel.get(attribute.getName()).asList();
            for (ModelNode currentReference : serverFactoryReferences) {
                writer.writeStartElement(elementName);
                writer.writeAttribute(NAME, currentReference.asString());
                writer.writeEndElement();
            }
        }
    }
}
