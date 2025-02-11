/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Collection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Feature;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A writable model group of XML content.
 * @param <C> the writer context.
 * @author Paul Ferraro
 */
public interface XMLContentWriter<C> extends XMLElementWriter<C>, Feature {

    interface Provider<C> {
        XMLContentWriter<C> getWriter();
    }

    /**
     * Indicates whether the specified content is empty.
     */
    boolean isEmpty(C content);

    /**
     * Returns an empty content writer.
     * @param <C> the writer context.
     * @return an empty content writer.
     */
    static <C> XMLContentWriter<C> empty() {
        return new DefaultXMLContentWriter<>(List.of());
    }

    class DefaultXMLContentWriter<RC, WC> implements XMLContentWriter<WC> {
        private final Collection<? extends XMLContentWriter.Provider<WC>> providers;

        DefaultXMLContentWriter(Collection<? extends XMLContentWriter.Provider<WC>> providers) {
            this.providers = providers;
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
            for (XMLContentWriter.Provider<WC> provider : this.providers) {
                XMLContentWriter<WC> contentWriter = provider.getWriter();
                if (!contentWriter.isEmpty(content)) {
                    contentWriter.writeContent(writer, content);
                }
            }
        }

        @Override
        public boolean isEmpty(WC content) {
            for (XMLContentWriter.Provider<WC> provider : this.providers) {
                XMLContentWriter<WC> contentWriter = provider.getWriter();
                if (!contentWriter.isEmpty(content)) return false;
            }
            return true;
        }

        @Override
        public Stability getStability() {
            Stability stability = null;
            for (XMLContentWriter.Provider<WC> provider : this.providers) {
                XMLContentWriter<WC> contentWriter = provider.getWriter();
                if (stability == null || stability.enables(contentWriter.getStability())) {
                    stability = contentWriter.getStability();
                }
            }
            return (stability != null) ? stability : Stability.DEFAULT;
        }
    }
}
