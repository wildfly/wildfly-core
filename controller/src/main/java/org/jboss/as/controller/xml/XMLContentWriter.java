/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Collection;
import java.util.function.Function;

import javax.xml.stream.XMLStreamException;

import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Adds empty content detection to an {@link XMLElementWriter}.
 * @author Paul Ferraro
 * @param <C> the writer context.
 */
public interface XMLContentWriter<C> extends XMLElementWriter<C> {

    /**
     * Indicates whether the specified content is empty.
     */
    boolean isEmpty(C content);

    /**
     * Returns a new writer whose content is mapped from the specified function.
     * @param mapper a content mapping function
     * @param <T> the mapped content type
     * @return a new writer whose content is mapped from the specified function.
     */
    default <T> XMLContentWriter<T> map(Function<T, C> mapper) {
        return new XMLContentWriter<>() {
            @Override
            public void writeContent(XMLExtendedStreamWriter writer, T content) throws XMLStreamException {
                XMLContentWriter.this.writeContent(writer, mapper.apply(content));
            }

            @Override
            public boolean isEmpty(T content) {
                return XMLContentWriter.this.isEmpty(mapper.apply(content));
            }
        };
    }

    /**
     * Returns a new writer that invokes {@link #writeContent(XMLExtendedStreamWriter, Object)} on the specified writer <em>after</em> invoking {@link #writeContent(XMLExtendedStreamWriter, Object)} on this writer.
     * @param writer a content writer
     * @return a new content writer combining this writer with the specified writer
     */
    default XMLContentWriter<C> andThen(XMLContentWriter<C> after) {
        return new XMLContentWriter<>() {
            @Override
            public void writeContent(XMLExtendedStreamWriter writer, C value) throws XMLStreamException {
                XMLContentWriter.this.writeContent(writer, value);
                after.writeContent(writer, value);
            }

            @Override
            public boolean isEmpty(C content) {
                return XMLContentWriter.this.isEmpty(content) && after.isEmpty(content);
            }
        };
    }

    /**
     * Returns a new writer that invokes {@link #writeContent(XMLExtendedStreamWriter, Object)} on the specified writer <em>before</em> invoking {@link #writeContent(XMLExtendedStreamWriter, Object)} on this writer.
     * @param writer a content writer
     * @return a new content writer combining this writer with the specified writer
     */
    default XMLContentWriter<C> compose(XMLContentWriter<C> before) {
        return new XMLContentWriter<>() {
            @Override
            public void writeContent(XMLExtendedStreamWriter writer, C value) throws XMLStreamException {
                before.writeContent(writer, value);
                XMLContentWriter.this.writeContent(writer, value);
            }

            @Override
            public boolean isEmpty(C content) {
                return before.isEmpty(content) && XMLContentWriter.this.isEmpty(content);
            }
        };
    }

    /**
     * Returns an empty content writer.
     * @param <C> the writer context.
     * @return an empty content writer.
     */
    static <C> XMLContentWriter<C> empty() {
        return new XMLContentWriter<>() {
            @Override
            public void writeContent(XMLExtendedStreamWriter streamWriter, C value) throws XMLStreamException {
                // Nothing to write
            }

            @Override
            public boolean isEmpty(C content) {
                return true;
            }
        };
    }

    static <RC, WC> XMLContentWriter<WC> composite(Collection<? extends XMLComponent<RC, WC>> components) {
        return new XMLContentWriter<>() {
            @Override
            public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
                for (XMLComponent<RC, WC> component : components) {
                    component.getWriter().writeContent(writer, content);
                }
            }

            @Override
            public boolean isEmpty(WC content) {
                for (XMLComponent<RC, WC> component : components) {
                    XMLContentWriter<WC> contentWriter = component.getWriter();
                    if (!contentWriter.isEmpty(content)) return false;
                }
                return true;
            }
        };
    }
}
