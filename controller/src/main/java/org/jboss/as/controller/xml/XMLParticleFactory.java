/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import javax.xml.namespace.QName;

/**
 * A factory for creating builders of XML particles.
 */
public interface XMLParticleFactory<RC, WC> {

    /**
     * Returns a builder of an XML element, using the specified name.
     * @param name the element name
     * @return a builder of an XML element.
     */
    XMLElement.Builder<RC, WC> element(QName name);

    /**
     * Returns a builder of an XML choice.
     * @return a builder of an XML choice.
     */
    XMLChoice.Builder<RC, WC> choice();

    /**
     * Returns a builder of XML content.
     * @return a builder of XML content.
     */
    XMLAll.Builder<RC, WC> all();

    /**
     * Returns a builder of XML content.
     * @return a builder of XML content.
     */
    XMLSequence.Builder<RC, WC> sequence();

    static <RC, WC> XMLParticleFactory<RC, WC> newInstance() {
        return new DefaultXMLParticleFactory<>();
    }

    class DefaultXMLParticleFactory<RC, WC> implements XMLParticleFactory<RC, WC> {
        DefaultXMLParticleFactory() {
        }

        @Override
        public XMLElement.Builder<RC, WC> element(QName name) {
            return new XMLElement.DefaultBuilder<>(name);
        }

        @Override
        public XMLChoice.Builder<RC, WC> choice() {
            return new XMLChoice.DefaultBuilder<>();
        }

        @Override
        public XMLAll.Builder<RC, WC> all() {
            return new XMLAll.DefaultBuilder<>();
        }

        @Override
        public XMLSequence.Builder<RC, WC> sequence() {
            return new XMLSequence.DefaultBuilder<>();
        }
    }
}
