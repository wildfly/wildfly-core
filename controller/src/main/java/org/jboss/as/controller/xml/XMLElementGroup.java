/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.LinkedList;
import java.util.List;

/**
 * Encapsulates a group of XML elements, e.g. xs:choice, xs:all, xs:sequence.
 * @author Paul Ferraro
 */
public interface XMLElementGroup<RC, WC> extends XMLParticle<RC, WC> {

    interface Builder<RC, WC> extends XMLParticle.Builder<RC, WC> {
        @Override
        Builder<RC, WC> withCardinality(XMLCardinality cardinality);

        /**
         * Adds an element to this compositor.
         * @param element an element to add
         * @return a reference to this builder
         */
        Builder<RC, WC> addElement(XMLElement<RC, WC> element);

        /**
         * Builds this element group.
         * @return a new element group.
         */
        @Override
        XMLElementGroup<RC, WC> build();
    }

    abstract class AbstractBuilder<RC, WC, B extends Builder<RC, WC>> extends XMLParticle.AbstractBuilder<RC, WC, B> implements Builder<RC, WC> {
        private final List<XMLElement<RC, WC>> elements = new LinkedList<>();

        @Override
        public B addElement(XMLElement<RC, WC> element) {
            this.elements.add(element);
            return this.builder();
        }

        protected List<XMLElement<RC, WC>> getElements() {
            return this.elements;
        }
    }
}
