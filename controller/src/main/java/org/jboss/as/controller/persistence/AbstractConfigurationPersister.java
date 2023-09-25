/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;

/**
 * Abstract superclass for {@link ExtensibleConfigurationPersister} implementations.
 *
 * @author Brian Stansberry
 */
public abstract class AbstractConfigurationPersister implements ExtensibleConfigurationPersister {

    private final XMLElementWriter<ModelMarshallingContext> rootDeparser;
    private final ConcurrentHashMap<String, Supplier<XMLElementWriter<SubsystemMarshallingContext>>> subsystemWriterSuppliers = new ConcurrentHashMap<>();

    /**
     * Construct a new instance.
     *
     * @param rootDeparser the root model deparser
     */
    public AbstractConfigurationPersister(final XMLElementWriter<ModelMarshallingContext> rootDeparser) {
        this.rootDeparser = rootDeparser;
    }

    @Override
    public void registerSubsystemWriter(String name, Supplier<XMLElementWriter<SubsystemMarshallingContext>> writer) {
        subsystemWriterSuppliers.putIfAbsent(name, writer);
    }

    @Override
    public void unregisterSubsystemWriter(String name) {
        subsystemWriterSuppliers.remove(name);
    }

    /** {@inheritDoc} */
    @Override
    public void marshallAsXml(final ModelNode model, final OutputStream output) throws ConfigurationPersistenceException {
        final XMLMapper mapper = XMLMapper.Factory.create();
        final Map<String, XMLElementWriter<SubsystemMarshallingContext>> localSubsystemWriters = new HashMap<>();
        try {
            XMLStreamWriter streamWriter = null;
            try {
                streamWriter = new UTF8XmlStringWriterDelegate(XMLOutputFactory.newInstance().createXMLStreamWriter(output, StandardCharsets.UTF_8.name()));
                final ModelMarshallingContext extensibleModel = new ModelMarshallingContext() {

                    @Override
                    public ModelNode getModelNode() {
                        return model;
                    }

                    @Override
                    public XMLElementWriter<SubsystemMarshallingContext> getSubsystemWriter(String extensionName) {
                        //lazy create writer, but only once per config serialization
                        XMLElementWriter<SubsystemMarshallingContext> result = localSubsystemWriters.get(extensionName);
                        if (result == null) {
                            Supplier<XMLElementWriter<SubsystemMarshallingContext>> supplier = subsystemWriterSuppliers.get(extensionName);
                            if (supplier != null) {
                                result = supplier.get();
                                localSubsystemWriters.put(extensionName, result);
                            }
                        }
                        return result;
                    }
                };
                mapper.deparseDocument(rootDeparser, extensibleModel, streamWriter);
                streamWriter.close();
            } finally {
                safeClose(streamWriter);
            }
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToWriteConfiguration(e);
        }
    }

    @Override
    public void successfulBoot() throws ConfigurationPersistenceException {
    }

    @Override
    public SnapshotInfo listSnapshots() {
        return NULL_SNAPSHOT_INFO;
    }

    @Override
    public void deleteSnapshot(String name) {
    }

    private static void safeClose(final XMLStreamWriter streamWriter) {
        if (streamWriter != null) try {
            streamWriter.close();
        } catch (Throwable t) {
            ROOT_LOGGER.failedToCloseResource(t, streamWriter);
        }
    }
}
