/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * {@link org.jboss.as.controller.AttributesXMLDescription} implementation which ignores attributes groups. All non-property
 * attributes are persisted as root attributes.
 *
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class IgnoreGroupsAttributesXMLDescription extends AbstractAttributesXMLDescription {

    private final Map<String,AttributeDefinition> rootAttributes = new LinkedHashMap<>();

    @Override
    public void registerAttribute(final AttributeDefinition attribute) {
        if(attribute instanceof  PropertiesAttributeDefinition){
            registerPropertyAttribute(attribute);
        } else {
            rootAttributes.put(attribute.getXmlName(), attribute);
        }
    }

    @Override
    public Map<String, AttributeDefinition> getRootAttributes() {
        return rootAttributes;
    }

    @Override
    public void parseTag(String tag, XMLExtendedStreamReader reader, Map<String, AttributeParser> attributeParsers,
            ModelNode operation) throws XMLStreamException {
        parseProperty(tag, reader, operation);
    }

    @Override
    public boolean willCreateTags(final ModelNode resourceModel, final boolean marshallDefault){
        return willCreatePropertyTags(resourceModel);
    }

    @Override
    public void persist(XMLExtendedStreamWriter writer, ModelNode resourceModel,
            final Map<String, AttributeMarshaller> attributeMarshallers, boolean marshallDefault) throws XMLStreamException {
        final Collection<AttributeDefinition> attributes = rootAttributes.values();
        persistAttributes(attributes, writer, resourceModel, attributeMarshallers, marshallDefault);
        final Collection<PropertiesAttributeDefinition> properties = propertyAttributes.values();
        persistAttributes(properties, writer, resourceModel, attributeMarshallers, marshallDefault);
    }
}
