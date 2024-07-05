/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A {@code ManagementXmlReaderWriter} implementation that will throw an exception if an attempt is made to
 * read at an unsupported stability level.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class UnstableManagementReaderWriter implements ManagementXmlReaderWriter {

    static final UnstableManagementReaderWriter INSTANCE = new UnstableManagementReaderWriter();

    private UnstableManagementReaderWriter() {
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, final String namespaceUri, List<ModelNode> value) throws XMLStreamException {
        throw ROOT_LOGGER.unstableManagementNamespace(reader.getNamespaceURI());
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter streamWriter, final String namespaceUri, ModelMarshallingContext value)
            throws XMLStreamException {
                throw ROOT_LOGGER.unstableManagementNamespace(namespaceUri);
    }

}
