/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

 package org.jboss.as.controller.parsing;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.xml.IntVersionSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Base representation of a schema for the management model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementSchema implements ManagementXmlSchema {

    private final ManagementXmlReaderWriter readerWriterDelegate;
    private final VersionedNamespace<IntVersion, ManagementXmlSchema> namespace;
    private final String localName;

    private ManagementSchema(ManagementXmlReaderWriter readerWriterDelegate,
        VersionedNamespace<IntVersion, ManagementXmlSchema> namespace, String localName) {
        this.readerWriterDelegate = readerWriterDelegate;
        this.namespace = namespace;
        this.localName = localName;
    }

    @Override
    public VersionedNamespace<IntVersion, ManagementXmlSchema> getNamespace() {
        return namespace;
    }

    @Override
    public String getLocalName() {
        return localName;
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
        readerWriterDelegate.readElement(reader, namespace, value);
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter streamWriter, ModelMarshallingContext value)
            throws XMLStreamException {
        readerWriterDelegate.writeContent(streamWriter, namespace, value);
    }

    public static ManagementSchema create(ManagementXmlReaderWriter readerWriterDelegate,
        Stability stability, int majorVersion, int minorVersion, String localName) {
        VersionedNamespace<IntVersion, ManagementXmlSchema> namespace =
            IntVersionSchema.createURN(List.of(IntVersionSchema.JBOSS_IDENTIFIER, DOMAIN), stability, new IntVersion(majorVersion, minorVersion));
        return new ManagementSchema(readerWriterDelegate, namespace, localName);
    }
}