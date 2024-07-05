/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Combination of the Reader and Writer interfaces for reading and writing the management model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface ManagementXmlReaderWriter {

    void readElement(XMLExtendedStreamReader reader, String namespaceUri, List<ModelNode> value) throws XMLStreamException;

    void writeContent(XMLExtendedStreamWriter streamWriter, String namespaceUri, ModelMarshallingContext value) throws XMLStreamException;
}
