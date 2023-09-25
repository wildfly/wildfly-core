/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.jboss.as.patching.IoUtils.safeClose;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

import org.jboss.staxmapper.XMLMapper;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchBundleXml {

    public static final String MULTI_PATCH_XML = "patches.xml";

    private static final XMLMapper MAPPER = XMLMapper.Factory.create();
    private static final PatchBundleXml_1_0 XML1_0 = new PatchBundleXml_1_0();
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    static {
        MAPPER.registerRootElement(new QName(PatchXml.Namespace.PATCH_BUNDLE_1_0.getNamespace(), PatchBundleXml_1_0.Element.PATCHES.name), XML1_0);
    }

    public static BundledPatch parse(final InputStream stream) throws XMLStreamException {
        try {
            final XMLInputFactory inputFactory = INPUT_FACTORY;
            setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
            setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(stream);
            //
            final PatchXml.Result<BundledPatch> result = new PatchXml.Result<BundledPatch>();
            MAPPER.parseDocument(result, streamReader);
            return result.getResult();
        } finally {
            safeClose(stream);
        }
    }

    public static void marshal(final Writer writer, final BundledPatch patches) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(writer);
        MAPPER.deparseDocument(XML1_0, patches, streamWriter);
        streamWriter.close();
    }

    public static void marshal(final OutputStream os, final BundledPatch patches) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(os);
        MAPPER.deparseDocument(XML1_0, patches, streamWriter);
        streamWriter.close();
    }

    private static void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

}
