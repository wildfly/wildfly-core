/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for {@link ExtensionRegistry}.
 */
public class ExtensionRegistryTestCase {

    /**
     * Verify {@link ExtensionRegistryTestCase#initializeParsers()} for stable vs unstable extensions.
     */
    @Test
    public void initializeParsers() {
        // Validate combinations of registry vs extension stability
        for (Stability stability : EnumSet.allOf(Stability.class)) {
            ExtensionRegistry registry = ExtensionRegistry.builder(ProcessType.SELF_CONTAINED).withStability(stability).build();

            for (Stability extensionStability : EnumSet.allOf(Stability.class)) {
                TestExtension extension = new TestExtension(extensionStability.toString(), extensionStability);

                if (registry.enables(extension)) {
                    this.initializeStableExtensionParsers(registry, extension);
                } else {
                    this.initializeUnstableExtensionParsers(registry, extension);
                }
            }
        }
    }

    private void initializeStableExtensionParsers(ExtensionRegistry registry, TestExtension extension) {
        String subsystemName = extension.getSubsystemName();

        // Record stability per namespace for use during verification
        Map<String, Stability> namespaces = new HashMap<>();
        // Create subsystem schema per stability
        for (Stability stability : EnumSet.allOf(Stability.class)) {
            VersionedNamespace<IntVersion, TestSubsystemSchema> namespace = SubsystemSchema.createSubsystemURN(subsystemName, stability, new IntVersion(1));
            extension.addSchema(new TestSubsystemSchema(namespace));
            namespaces.put(namespace.getUri(), stability);
        }

        // Capture registered parser per namespace
        XMLMapper mapper = mock(XMLMapper.class);
        ArgumentCaptor<QName> capturedNames = ArgumentCaptor.forClass(QName.class);
        ArgumentCaptor<XMLElementReader<?>> capturedReaders = ArgumentCaptor.forClass(XMLElementReader.class);

        registry.initializeParsers(extension, subsystemName, mapper);

        // Extension should have registered a parser per stability
        verify(mapper, times(namespaces.size())).registerRootElement(capturedNames.capture(), capturedReaders.capture());

        List<QName> names = capturedNames.getAllValues();
        List<XMLElementReader<?>> readers = capturedReaders.getAllValues();
        assertEquals(namespaces.size(), names.size());
        assertEquals(namespaces.size(), readers.size());

        for (int i = 0; i < namespaces.size(); ++i) {
            QName name = names.get(i);
            XMLElementReader<?> reader = readers.get(i);

            Stability stability = namespaces.get(name.getNamespaceURI());
            assertNotNull(stability);

            XMLExtendedStreamReader mockReader = mock(XMLExtendedStreamReader.class);
            doReturn(name.getNamespaceURI()).when(mockReader).getNamespaceURI();

            if (registry.getStability().enables(stability)) {
                // Verify expected parsing if namespace is enabled by registry
                try {
                    reader.readElement(mockReader, null);
                    verify(mockReader).discardRemainder();
                } catch (XMLStreamException e) {
                    fail(name.getNamespaceURI());
                }
            } else {
                // Verify parsing of disabled namespace throws exception
                assertThrows(name.getNamespaceURI(), XMLStreamException.class, () -> reader.readElement(mockReader, null));
            }
        }
    }

    private void initializeUnstableExtensionParsers(ExtensionRegistry registry, TestExtension extension) {
        // Create 2 schema versions per stability:
        // * V1 uses traditional parser registration
        // * V2 uses versioned schema registration
        String subsystemName = extension.getSubsystemName();
        extension.addParser(SubsystemSchema.createSubsystemURN(subsystemName, new IntVersion(1)).getUri(), (reader, value) -> reader.discardRemainder());
        for (Stability stability : EnumSet.allOf(Stability.class)) {
            // Versioned namespace parsers
            extension.addSchema(new TestSubsystemSchema(SubsystemSchema.createSubsystemURN(subsystemName, stability, new IntVersion(2))));
        }
        // Expected number of registered parsers
        int namespaces = EnumSet.allOf(Stability.class).size() + 1;

        // Capture registered parser per namespace
        XMLMapper mapper = mock(XMLMapper.class);
        ArgumentCaptor<QName> capturedNames = ArgumentCaptor.forClass(QName.class);
        ArgumentCaptor<XMLElementReader<?>> capturedReaders = ArgumentCaptor.forClass(XMLElementReader.class);

        registry.initializeParsers(extension, subsystemName, mapper);

        // Extension should have registered 1 parser per stability + 1 traditional parser
        verify(mapper, times(namespaces)).registerRootElement(capturedNames.capture(), capturedReaders.capture());

        List<QName> names = capturedNames.getAllValues();
        List<XMLElementReader<?>> readers = capturedReaders.getAllValues();
        assertEquals(namespaces, names.size());
        assertEquals(namespaces, readers.size());

        // Verify that all parsers of an unstable extension throw an exception
        for (int i = 0; i < namespaces; ++i) {
            QName name = names.get(i);
            XMLElementReader<?> reader = readers.get(i);

            XMLExtendedStreamReader mockReader = mock(XMLExtendedStreamReader.class);
            doReturn(name.getNamespaceURI()).when(mockReader).getNamespaceURI();

            assertThrows(name.getNamespaceURI(), XMLStreamException.class, () -> reader.readElement(mockReader, null));
        }
    }

    class TestSubsystemSchema implements SubsystemSchema<TestSubsystemSchema> {
        private final VersionedNamespace<IntVersion, TestSubsystemSchema> namespace;

        TestSubsystemSchema(VersionedNamespace<IntVersion, TestSubsystemSchema> namespace) {
            this.namespace = namespace;
        }

        @Override
        public VersionedNamespace<IntVersion, TestSubsystemSchema> getNamespace() {
            return this.namespace;
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            reader.discardRemainder();
        }
    }

    class TestExtension implements Extension {
        private final String subsystemName;
        private final Stability stability;
        private final Map<String, XMLElementReader<List<ModelNode>>> parsers = new HashMap<>();
        private final Map<String, TestSubsystemSchema> schemas = new HashMap<>();

        TestExtension(String subsystemName, Stability stability) {
            this.subsystemName = subsystemName;
            this.stability = stability;
        }

        String getSubsystemName() {
            return this.subsystemName;
        }

        void addParser(String namespaceURI, XMLElementReader<List<ModelNode>> parser) {
            this.parsers.put(namespaceURI, parser);
        }

        void addSchema(TestSubsystemSchema schema) {
            this.schemas.put(schema.getNamespace().getUri(), schema);
        }

        @Override
        public void initialize(ExtensionContext context) {
        }

        @Override
        public void initializeParsers(ExtensionParsingContext context) {
            for (Map.Entry<String, XMLElementReader<List<ModelNode>>> entry : this.parsers.entrySet()) {
                context.setSubsystemXmlMapping(this.subsystemName, entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, TestSubsystemSchema> entry : this.schemas.entrySet()) {
                context.setSubsystemXmlMappings(this.subsystemName, Set.of(entry.getValue()));
            }
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }
    }
}
