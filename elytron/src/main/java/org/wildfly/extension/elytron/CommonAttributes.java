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
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Attributes used by {@link ResourceDefinition} instances that need a common home.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CommonAttributes {

    // TODO - Check we really want this and not other suitable common location is available.

    static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTIES, ModelType.STRING, true)
        .setAllowExpression(true)
        .setAttributeMarshaller(new AttributeMarshaller() {

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault,
                XMLStreamWriter writer) throws XMLStreamException {
            resourceModel = resourceModel.get(attribute.getName());
            if (resourceModel.isDefined()) {
                writer.writeStartElement(attribute.getName());
                for (ModelNode property : resourceModel.asList()) {
                    writer.writeEmptyElement(PROPERTY);
                    writer.writeAttribute(NAME, property.asProperty().getName());
                    writer.writeAttribute(VALUE, property.asProperty().getValue().asString());
                }
                writer.writeEndElement();
            }
        }

    }).build();

}
