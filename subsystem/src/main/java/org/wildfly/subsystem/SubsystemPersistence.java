/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescriptionReader;
import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.xml.Schema;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * Encapsulates the persistence configuration of a subsystem.
 * @author Paul Ferraro
 * @param <S> the schema type
 */
public interface SubsystemPersistence<S extends Schema> {

    /**
     * Returns the supported schemas of this subsystem.
     * @return a set of schemas
     */
    Set<S> getSchemas();

    /**
     * Returns the appropriate StAX reader for the version of the specified schema that parses subsystem XML into a set of management operations.
     * @return a StAX reader for the version of the specified schema
     */
    XMLElementReader<List<ModelNode>> getReader(S schema);

    /**
     * Returns a StAX writer that writes the subsystem model to XML according to the current schema version.
     * @return a StAX writer
     */
    XMLElementWriter<SubsystemMarshallingContext> getWriter();

    /**
     * Creates the subsystem persistence configuration for the current version of the schema.
     * @param <S> the schema type
     * @param currentSchema the current schema version
     * @return a subsystem persistence configuration
     */
    static <S extends Enum<S> & PersistentSubsystemSchema<S>> SubsystemPersistence<S> of(S currentSchema) {
        PersistentResourceXMLDescription currentXMLDescription = currentSchema.getXMLDescription();
        return of(currentSchema, new PersistentResourceXMLDescriptionReader(currentXMLDescription), new PersistentResourceXMLDescriptionWriter(currentXMLDescription));
    }

    /**
     * Creates the subsystem persistence configuration for the current version of the schema and specified writer
     * @param <S> the schema type
     * @param currentSchema the current schema version
     * @return a subsystem persistence configuration
     */
    static <S extends Enum<S> & SubsystemSchema<S>> SubsystemPersistence<S> of(S currentSchema, XMLElementWriter<SubsystemMarshallingContext> writer) {
        return of(currentSchema, currentSchema, writer);
    }

    private static <S extends Enum<S> & SubsystemSchema<S>> SubsystemPersistence<S> of(S currentSchema, XMLElementReader<List<ModelNode>> currentReader, XMLElementWriter<SubsystemMarshallingContext> writer) {
        return of(EnumSet.allOf(currentSchema.getDeclaringClass()), Function.identity(), currentSchema, currentSchema, writer);
    }

    /**
     * Creates the subsystem persistence configuration for the specified set of schemas, reader factory, and writer.
     * @param <S> the schema type
     * @param <R> the schema reader type
     * @param schemas a set of schemas
     * @param readerFactory a factory for creating an XML reader for a specific schema
     * @param writer an XML reader
     * @return a subsystem persistence configuration
     */
    static <S extends Schema, R extends XMLElementReader<List<ModelNode>>> SubsystemPersistence<S> of(Set<S> schemas, Function<S, R> readerFactory, XMLElementWriter<SubsystemMarshallingContext> writer) {
        return new DefaultSubsystemPersistence<>(schemas, readerFactory, writer);
    }

    /**
     * Creates the subsystem persistence configuration for the current version of the schema and specified writer
     * @param <S> the schema type
     * @param <R> the schema reader type
     * @param schemas a set of schemas
     * @param readerFactory a factory for creating an XML reader for a specific schema
     * @param currentSchema the current schema version
     * @param currentReader the XML reader for the current schema version
     * @param writer an XML reader
     * @return a subsystem persistence configuration
     */
    static <S extends Schema, R extends XMLElementReader<List<ModelNode>>> SubsystemPersistence<S> of(Set<S> schemas, Function<S, R> readerFactory, S currentSchema, XMLElementReader<List<ModelNode>> currentReader, XMLElementWriter<SubsystemMarshallingContext> writer) {
        return new DefaultSubsystemPersistence<>(schemas, readerFactory, writer) {
            @Override
            public Set<S> getSchemas() {
                return schemas;
            }

            @Override
            public XMLElementReader<List<ModelNode>> getReader(S schema) {
                return (schema == currentSchema) ? currentReader : readerFactory.apply(schema);
            }

            @Override
            public XMLElementWriter<SubsystemMarshallingContext> getWriter() {
                return writer;
            }
        };
    }

    class DefaultSubsystemPersistence<S extends Schema, R extends XMLElementReader<List<ModelNode>>> implements SubsystemPersistence<S> {
        private final Set<S> schemas;
        private final Function<S, R> readerFactory;
        private final XMLElementWriter<SubsystemMarshallingContext> writer;

        DefaultSubsystemPersistence(Set<S> schemas, Function<S, R> readerFactory, XMLElementWriter<SubsystemMarshallingContext> writer) {
            this.schemas = schemas;
            this.readerFactory = readerFactory;
            this.writer = writer;
        }

        @Override
        public Set<S> getSchemas() {
            return this.schemas;
        }

        @Override
        public XMLElementReader<List<ModelNode>> getReader(S schema) {
            return this.readerFactory.apply(schema);
        }

        @Override
        public XMLElementWriter<SubsystemMarshallingContext> getWriter() {
            return this.writer;
        }
    }
}
