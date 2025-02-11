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

    interface Builder<RC, WC, T extends XMLElementGroup<RC, WC>, B extends Builder<RC, WC, T, B>> extends XMLParticle.Builder<RC, WC, T, B> {
        /**
         * Adds an element to this compositor.
         * @param element an element to add
         * @return a reference to this builder
         */
        B addElement(XMLElement<RC, WC> element);
    }

    abstract class AbstractBuilder<RC, WC, T extends XMLElementGroup<RC, WC>, B extends Builder<RC, WC, T, B>> extends XMLParticle.AbstractBuilder<RC, WC, T, B> implements Builder<RC, WC, T, B> {
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
