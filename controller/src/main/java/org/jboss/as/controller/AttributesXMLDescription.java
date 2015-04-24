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
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML description of attributes in a {@link org.jboss.as.controller.registry.Resource}. Allows parsing and persisting
 * Resource's attributes XML configuration.
 *
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
interface AttributesXMLDescription {


    /**
     * Registers {@link org.jboss.as.controller.AttributeDefinition} in the description.
     *
     * @param attributeDefinition Attribute to be registered.
     */
    void registerAttribute(AttributeDefinition attributeDefinition);

    /**
     * List of {@link org.jboss.as.controller.AttributeDefinition} that are part of root XML tag of the described
     * {@link org.jboss.as.controller.registry.Resource}.
     *
     * @return Map of root attributes.
     */
    Map<String, AttributeDefinition> getRootAttributes();

    /**
     * Collection of XML tags that can be created by this description.
     * {@link org.jboss.as.controller.PersistentResourceXMLDescription} uses this list to determine whether parsing of a given
     * tag can be delegated to {@link org.jboss.as.controller.AttributesXMLDescription} implementation.
     *
     * @return Tags collection.
     */
    Collection<String> getTags();

    /**
     * Parse XML tag. {@link org.jboss.as.controller.PersistentResourceXMLDescription} delegates parsing the tag to
     * {@link org.jboss.as.controller.AttributesXMLDescription} implementation based on Collection of tags returned by
     * {@link #getTags()} method.
     *
     * @param tag Tag to be parsed.
     * @param reader XML reader.
     * @param attributeParsers Map of attribute parsers. If attribute is no present in the map then it's default parser should be used.
     * @param operation Result {@link org.jboss.dmr.ModelNode} operation to which parsed attributes should be added.
     * @throws XMLStreamException
     */
    void parseTag(String tag, XMLExtendedStreamReader reader, Map<String, AttributeParser> attributeParsers, ModelNode operation) throws XMLStreamException;

    /**
     * {@link org.jboss.as.controller.PersistentResourceXMLDescription} uses this method to determine whether the description
     * will create nested xml tags in root {@link org.jboss.as.controller.registry.Resource} tag when persisting given model.
     *
     * @param resourceModel The model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value.
     * @return {@code true} if nested tags will be created when persisting given resourceModel
     */
    boolean willCreateTags(final ModelNode resourceModel, final boolean marshallDefault);

    /**
     * Persist attributes of a given {@link org.jboss.as.controller.registry.Resource}.
     *
     * @param writer XML writer.
     * @param resourceModel   The model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value.
     * @throws XMLStreamException
     */
    void persist(XMLExtendedStreamWriter writer, ModelNode resourceModel, final Map<String, AttributeMarshaller> attributeMarshallers, boolean marshallDefault) throws XMLStreamException;

}
