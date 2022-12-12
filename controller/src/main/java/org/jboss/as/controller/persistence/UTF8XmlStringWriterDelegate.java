/*
 * Copyright 2022 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.persistence;

import java.nio.charset.StandardCharsets;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * An {@link XMLStreamWriter} implementation delegate that always adds an XML encoding header with UTF-8 value
 */
final class UTF8XmlStringWriterDelegate implements XMLStreamWriter {
    private final XMLStreamWriter delegate;
    private static final String ENCODING_HEADER = StandardCharsets.UTF_8.name();
    private static final String VERSION_HEADER = "1.0";

    UTF8XmlStringWriterDelegate(XMLStreamWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        delegate.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        delegate.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        delegate.writeStartElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        delegate.writeEmptyElement(namespaceURI, localName);
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        delegate.writeEmptyElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        delegate.writeEmptyElement(localName);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        delegate.writeEndElement();
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        delegate.writeEndDocument();
    }

    @Override
    public void close() throws XMLStreamException {
        delegate.close();
    }

    @Override
    public void flush() throws XMLStreamException {
        delegate.flush();
    }

    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        delegate.writeAttribute(localName, value);
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        delegate.writeAttribute(prefix, namespaceURI, localName, value);
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        delegate.writeAttribute(namespaceURI, localName, value);
    }

    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        delegate.writeNamespace(prefix, namespaceURI);
    }

    @Override
    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        delegate.writeDefaultNamespace(namespaceURI);
    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
        delegate.writeComment(data);
    }

    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException {
        delegate.writeProcessingInstruction(target);
    }

    @Override
    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        delegate.writeProcessingInstruction(target, data);
    }

    @Override
    public void writeCData(String data) throws XMLStreamException {
        delegate.writeCData(data);
    }

    @Override
    public void writeDTD(String dtd) throws XMLStreamException {
        delegate.writeDTD(dtd);
    }

    @Override
    public void writeEntityRef(String name) throws XMLStreamException {
        delegate.writeEntityRef(name);
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        this.writeStartDocument(ENCODING_HEADER, VERSION_HEADER);
    }

    @Override
    public void writeStartDocument(String version) throws XMLStreamException {
        this.writeStartDocument(ENCODING_HEADER, version);
    }

    @Override
    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        delegate.writeStartDocument(encoding, version);
    }

    @Override
    public void writeCharacters(String text) throws XMLStreamException {
        delegate.writeCharacters(text);
    }

    @Override
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        delegate.writeCharacters(text, start, len);
    }

    @Override
    public String getPrefix(String uri) throws XMLStreamException {
        return delegate.getPrefix(uri);
    }

    @Override
    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        delegate.setPrefix(prefix, uri);
    }

    @Override
    public void setDefaultNamespace(String uri) throws XMLStreamException {
        delegate.setDefaultNamespace(uri);
    }

    @Override
    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        delegate.setNamespaceContext(context);
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return delegate.getNamespaceContext();
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        return delegate.getProperty(name);
    }
}
