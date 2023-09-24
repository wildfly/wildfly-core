/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Set;

import org.jboss.staxmapper.XMLAttributeReader;
import org.jboss.staxmapper.XMLMapper;

/**
 * A versioned schema for an XML attribute.
 * @author Paul Ferraro
 * @param S the schema type
 * @param T the type upon which this XML reader operates
 */
public interface XMLAttributeSchema<S extends XMLAttributeSchema<S, T>, T> extends IntVersionSchema<S>, XMLAttributeReader<T> {

    /**
     * Creates a StAX mapper from a set of schemas.
     * @param <T> the xml reader context type
     * @param <S> the schema type
     * @param schemas a set of XML attribute schemas
     * @return a StAX mapper
     */
    static <T, S extends XMLAttributeSchema<S, T>> XMLMapper createXMLMapper(Set<S> schemas) {
        XMLMapper mapper = XMLMapper.Factory.create();
        for (S schema : schemas) {
            mapper.registerRootAttribute(schema.getQualifiedName(), schema);
        }
        return mapper;
    }
}
