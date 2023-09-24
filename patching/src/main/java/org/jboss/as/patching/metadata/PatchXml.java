/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchXml {

    public static final String PATCH_XML = "patch.xml";
    public static final String ROLLBACK_XML = "rollback.xml";

    private static final XMLMapper MAPPER = XMLMapper.Factory.create();
    private static final PatchXml_1_0 XML1_0 = new PatchXml_1_0();
    private static final RollbackPatchXml_1_0 ROLLBACK_1_0 = new RollbackPatchXml_1_0();
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    static {
        MAPPER.registerRootElement(new QName(Namespace.PATCH_1_0.getNamespace(), PatchXml_1_0.Element.PATCH.name), XML1_0);
        MAPPER.registerRootElement(new QName(Namespace.PATCH_1_1.getNamespace(), PatchXml_1_0.Element.PATCH.name), XML1_0);
        MAPPER.registerRootElement(new QName(Namespace.PATCH_1_2.getNamespace(), PatchXml_1_0.Element.PATCH.name), XML1_0);
        MAPPER.registerRootElement(new QName(Namespace.ROLLBACK_1_0.getNamespace(), PatchXml_1_0.Element.PATCH.name), ROLLBACK_1_0);
        MAPPER.registerRootElement(new QName(Namespace.ROLLBACK_1_1.getNamespace(), PatchXml_1_0.Element.PATCH.name), ROLLBACK_1_0);
        MAPPER.registerRootElement(new QName(Namespace.ROLLBACK_1_2.getNamespace(), PatchXml_1_0.Element.PATCH.name), ROLLBACK_1_0);
    }

    public enum Namespace {

        PATCH_1_0("urn:jboss:patch:1.0"),
        PATCH_1_1("urn:jboss:patch:1.1"),
        PATCH_1_2("urn:jboss:patch:1.2"),
        ROLLBACK_1_0("urn:jboss:patch:rollback:1.0"),
        ROLLBACK_1_1("urn:jboss:patch:rollback:1.1"),
        ROLLBACK_1_2("urn:jboss:patch:rollback:1.2"),
        PATCH_BUNDLE_1_0("urn:jboss:patch:bundle:1.0"),
        UNKNOWN(null),
        ;

        private final String namespace;
        Namespace(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }

        static Map<String, Namespace> elements = new HashMap<String, Namespace>();
        static {
            for(Namespace element : Namespace.values()) {
                if(element != UNKNOWN) {
                    elements.put(element.namespace, element);
                }
            }
        }

        static Namespace forUri(String name) {
            final Namespace element = elements.get(name);
            return element == null ? UNKNOWN : element;
        }

    }

    private PatchXml() {
        //
    }

    public static void marshal(final Writer writer, final Patch patch) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(writer);
        final XMLElementWriter<?> xmlWriter = XML1_0;
        MAPPER.deparseDocument(xmlWriter, patch, streamWriter);
        streamWriter.close();
    }

    public static void marshal(final OutputStream os, final Patch patch) throws XMLStreamException {
        marshal(os, patch, patch instanceof RollbackPatch ? ROLLBACK_1_0 : XML1_0);
    }

    public static void marshal(final OutputStream os, final RollbackPatch patch) throws XMLStreamException {
        marshal(os, patch, ROLLBACK_1_0);
    }

    protected static void marshal(final OutputStream os, final Patch patch, final XMLElementWriter<? extends Patch> xmlWriter) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(os);
        MAPPER.deparseDocument(xmlWriter, patch, streamWriter);
        streamWriter.close();
    }

    public static PatchMetadataResolver parse(final InputStream stream) throws XMLStreamException {
        return parse(stream, null);
    }

    public static PatchMetadataResolver parse(final InputStream stream, InstalledIdentity originalIdentity) throws XMLStreamException {
        return parse(getXMLInputFactory().createXMLStreamReader(stream), originalIdentity);
    }

    public static PatchMetadataResolver parse(final Reader stream) throws XMLStreamException {
        return parse(stream, null);
    }

    public static PatchMetadataResolver parse(final Reader reader, InstalledIdentity originalIdentity) throws XMLStreamException {
        return parse(getXMLInputFactory().createXMLStreamReader(reader), originalIdentity);
    }

    private static XMLInputFactory getXMLInputFactory() throws XMLStreamException {
        final XMLInputFactory inputFactory = INPUT_FACTORY;
        setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return inputFactory;
    }

    protected static PatchMetadataResolver parse(final XMLStreamReader reader, InstalledIdentity originalIdentity) throws XMLStreamException {
        try {
            final Result<PatchMetadataResolver> result = new Result<PatchMetadataResolver>(originalIdentity);
            MAPPER.parseDocument(result, reader);
            return result.getResult();
        } finally {
            reader.close();
        }
    }

    public static PatchMetadataResolver parse(final File patchXml) throws IOException, XMLStreamException {
        return parse(patchXml, null);
    }

    public static PatchMetadataResolver parse(final File patchXml, InstalledIdentity original) throws IOException, XMLStreamException {
        try (final InputStream is = new FileInputStream(patchXml)){
            return parse(is, original);
        }
    }

    private static void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    public static class Result<T> {
        private T result;
        private final InstalledIdentity originalIdentity;

        Result() {
            this(null);
        }

        Result(InstalledIdentity originalIdentity) {
            this.originalIdentity = originalIdentity;
        }

        public T getResult() {
            return result;
        }

        public void setResult(T result) {
            this.result = result;
        }

        public InstalledIdentity getOriginalIdentity() {
            return originalIdentity;
        }
    }

}
