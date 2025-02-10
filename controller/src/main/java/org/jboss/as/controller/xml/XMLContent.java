/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

/**
 * Encapsulates the content of an XML element.
 * @param <RC> the reader context
 * @param <WC> the writer content
 * @author Paul Ferraro
 */
public interface XMLContent<RC, WC> extends XMLParticle<RC, WC> {
    /**
     * Returns an empty content
     * @param <RC> the reader context
     * @param <WC> the writer context
     * @return an empty content
     */
    static <RC, WC> XMLContent<RC, WC> empty() {
        return new DefaultXMLContent<>(XMLCardinality.Unbounded.OPTIONAL, XMLContentReader.empty(), XMLContentWriter.empty());
    }

    class DefaultXMLContent<RC, WC> extends XMLParticle.DefaultXMLParticle<RC, WC> implements XMLContent<RC, WC> {

        DefaultXMLContent(XMLCardinality cardinality, XMLContentReader<RC> reader, XMLContentWriter<WC> writer) {
            super(cardinality, reader, writer);
        }
    }
}
