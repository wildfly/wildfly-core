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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public abstract class AbstractAttributesXMLDescription implements AttributesXMLDescription{

    protected final Map<String, PropertiesAttributeDefinition> propertyAttributes =new LinkedHashMap<String, PropertiesAttributeDefinition>();
    protected final List<String> tags=new LinkedList<>();

    @Override
    public List<String> getTags() {
        return tags;
    }

    protected void registerPropertyAttribute(final AttributeDefinition attribute) {
        final PropertiesAttributeDefinition property = (PropertiesAttributeDefinition) attribute;
        if (!property.isWrapped()) {
            propertyAttributes.put(property.getXmlName(), property);
            tags.add(property.getXmlName());
        } else {
            propertyAttributes.put(property.getWrapperElement(), property);
            tags.add(property.getWrapperElement());
        }
    }

    protected void parseProperty(final String tag, final XMLExtendedStreamReader reader, final ModelNode operation)
            throws XMLStreamException {
        final PropertiesAttributeDefinition property = propertyAttributes.get(tag);
        if (!property.isWrapped()) {
            property.parse(reader, operation);
        } else {
            while (reader.hasNext()) {
                if (reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
                    if (property.getWrapperElement().equals(reader.getLocalName())) {
                        break;
                    }
                    // else continue to the next children
                    continue;
                }
                property.parse(reader, operation);
            }
        }
    }

    protected boolean willCreatePropertyTags(final ModelNode resourceModel) {
        for (final PropertiesAttributeDefinition property : propertyAttributes.values()) {
            if (resourceModel.hasDefined(property.getName())) {
                return true;
            }
        }
        return false;
    }

    protected void persistAttributes(final Collection<? extends AttributeDefinition> attributes,
            final XMLExtendedStreamWriter writer, final ModelNode resourceModel,
            final Map<String, AttributeMarshaller> attributeMarshallers, final boolean marshallDefault) throws XMLStreamException {
        for (final AttributeDefinition attribute : attributes) {
            final AttributeMarshaller marshaller = attributeMarshallers.containsKey(attribute.getName()) ? attributeMarshallers
                    .get(attribute.getName()) : attribute.getAttributeMarshaller();
            if (marshaller.isMarshallable(attribute, resourceModel, marshallDefault)) {
                marshaller.marshallAsAttribute(attribute, resourceModel, marshallDefault, writer);
            }
        }
    }

}
