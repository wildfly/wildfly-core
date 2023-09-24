/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;
import org.projectodd.vdx.core.XMLStreamValidationException;
import org.projectodd.vdx.wildfly.WildFlyErrorReporter;
import org.wildfly.common.xml.XMLInputFactoryUtil;

/**
 * A configuration persister which uses an XML file for backing storage.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class XmlConfigurationPersister extends AbstractConfigurationPersister {

    private final File fileName;
    private final QName rootElement;
    private final XMLElementReader<List<ModelNode>> rootParser;
    private final Map<QName, XMLElementReader<List<ModelNode>>> additionalParsers;
    private final boolean suppressLoad;

    /**
     * Construct a new instance.
     *
     * @param fileName the configuration base file name
     * @param rootElement the root element of the configuration file
     * @param rootParser the root model parser
     * @param rootDeparser the root model deparser
     */
    public XmlConfigurationPersister(final File fileName, final QName rootElement, final XMLElementReader<List<ModelNode>> rootParser, final XMLElementWriter<ModelMarshallingContext> rootDeparser) {
        this(fileName, rootElement, rootParser, rootDeparser, false);
    }

    /**
     * Construct a new instance.
     *
     * @param fileName the configuration base file name
     * @param rootElement the root element of the configuration file
     * @param rootParser the root model parser
     * @param rootDeparser the root model deparser
     */
    public XmlConfigurationPersister(final File fileName, final QName rootElement, final XMLElementReader<List<ModelNode>> rootParser,
                                     final XMLElementWriter<ModelMarshallingContext> rootDeparser, final boolean suppressLoad) {
        super(rootDeparser);
        this.fileName = fileName;
        this.rootElement = rootElement;
        this.rootParser = rootParser;
        this.additionalParsers = new HashMap<QName, XMLElementReader<List<ModelNode>>>();
        this.suppressLoad = suppressLoad;
    }

    public void registerAdditionalRootElement(final QName anotherRoot, final XMLElementReader<List<ModelNode>> parser){
        synchronized (additionalParsers) {
            additionalParsers.put(anotherRoot, parser);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        return new FilePersistenceResource(model, fileName, this);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        if (suppressLoad) {
            return new ArrayList<>();
        }

        final XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(rootElement, rootParser);
        synchronized (additionalParsers) {
            for (Map.Entry<QName, XMLElementReader<List<ModelNode>>> entry : additionalParsers.entrySet()) {
                mapper.registerRootElement(entry.getKey(), entry.getValue());
            }
        }
        final List<ModelNode> updates = new ArrayList<ModelNode>();
        BufferedInputStream input = null;
        XMLStreamReader streamReader = null;
        try {
            input = new BufferedInputStream(new FileInputStream(fileName));
            streamReader = XMLInputFactoryUtil.create().createXMLStreamReader(input);
            mapper.parseDocument(updates, streamReader);
        } catch (XMLStreamException e) {
            final boolean reported = reportValidationError(e);
            Throwable cause = null;
            if (!reported) {
                if (e instanceof XMLStreamValidationException) {
                    cause = e.getNestedException();
                } else {
                    cause = e;
                }
            }
            throw ControllerLogger.ROOT_LOGGER.failedToParseConfiguration(cause);
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToParseConfiguration(e);
        } finally {
            safeClose(streamReader);
            safeClose(input);
        }

        return updates;
    }

    private boolean reportValidationError(final XMLStreamException exception) {
        return new WildFlyErrorReporter(this.fileName,
                                        ControllerLogger.ROOT_LOGGER)
                .report(exception);
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            ROOT_LOGGER.failedToCloseResource(t, closeable);
        }
    }

    private static void safeClose(final XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Throwable t) {
                ROOT_LOGGER.failedToCloseResource(t, reader);
            }
        }
    }

    protected void successfulBoot(File file) throws ConfigurationPersistenceException {

    }

}
