/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTY;

import java.util.Collections;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface AttributeParsers {

    public static class PropertiesParser extends AttributeParser {
        private final String wrapperElement;
        private final String elementName;
        private final boolean wrapElement;


        public PropertiesParser(String wrapperElement, String elementName, boolean wrapElement) {
            this.wrapperElement = wrapperElement;
            this.elementName = elementName == null?PROPERTY:elementName;
            this.wrapElement = wrapElement;
        }

        public PropertiesParser(String wrapperElement, boolean wrapElement) {
            this(wrapperElement, PROPERTY, wrapElement);
        }

        public PropertiesParser(boolean wrapElement) {
            this(null, null, wrapElement);
        }

        public PropertiesParser(String wrapperElement) {
            this(wrapperElement, null, true);
        }

        public PropertiesParser() {
            this(null, PROPERTY, true);
        }

        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public String getXmlName(AttributeDefinition attribute) {
            return wrapElement ? wrapperElement != null ? wrapperElement : attribute.getXmlName() : elementName;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof PropertiesAttributeDefinition;
            PropertiesAttributeDefinition property = (PropertiesAttributeDefinition) attribute;
            String wrapper = wrapperElement == null ? property.getName() : wrapperElement;

            if (wrapElement) {
                if (!reader.getLocalName().equals(wrapper)) {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(wrapper));
                } else {
                    reader.nextTag();
                }
            }
            do {
                if (elementName.equals(reader.getLocalName())) {
                    property.parse(reader, operation);
                } else {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(elementName));
                }

            } while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals(elementName));

            if (wrapElement) {
                if (!reader.getLocalName().equals(wrapperElement)) {
                    ParseUtils.requireNoContent(reader);
                }
            }
        }
    }


    AttributeParser PROPERTIES_WRAPPED = new PropertiesParser();
    AttributeParser PROPERTIES_UNWRAPPED = new PropertiesParser(false);


}
