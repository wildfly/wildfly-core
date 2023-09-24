/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Emanuel Muckenhuber
 */
class PatchXml_1_0 extends PatchXmlUtils implements XMLStreamConstants, XMLElementReader<PatchXml.Result<PatchMetadataResolver>>, XMLElementWriter<Patch> {

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final Patch patch) throws XMLStreamException {

        // Get started ...
        writer.writeStartDocument();
        writer.writeStartElement(Element.PATCH.name);
        writer.writeDefaultNamespace(PatchXml.Namespace.PATCH_1_2.getNamespace());

        writePatch(writer, patch);

        // Done
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, PatchXml.Result<PatchMetadataResolver> factory) throws XMLStreamException {
        final PatchBuilder builder = new PatchBuilder();
        doReadElement(reader, builder, factory.getOriginalIdentity());
        factory.setResult(builder);
    }

}
