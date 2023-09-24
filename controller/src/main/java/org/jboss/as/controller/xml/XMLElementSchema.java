/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Set;

import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLMapper;

/**
 * A versioned schema for an XML element.
 * @author Paul Ferraro
 * @param S the schema type
 * @param T the type upon which this XML reader operates
 */
public interface XMLElementSchema<S extends XMLElementSchema<S, T>, T> extends IntVersionSchema<S>, XMLElementReader<T> {

    /**
     * Creates a StAX mapper from a set of schemas.
     * @param <T> the xml reader context type
     * @param <S> the schema type
     * @param schemas a set of XML element schemas
     * @return a StAX mapper
     */
    static <T, S extends XMLElementSchema<S, T>> XMLMapper createXMLMapper(Set<S> schemas) {
        XMLMapper mapper = XMLMapper.Factory.create();
        for (S schema : schemas) {
            mapper.registerRootElement(schema.getQualifiedName(), schema);
        }
        return mapper;
    }
}
