/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.LinkedList;
import java.util.List;

/**
 * Encapsulates a group of XML particles, e.g. xs:choice, xs:sequence.
 * @author Paul Ferraro
 */
public interface XMLParticleGroup<RC, WC> extends XMLElementGroup<RC, WC> {

    interface Builder<RC, WC> extends XMLElementGroup.Builder<RC, WC> {
        @Override
        Builder<RC, WC> withCardinality(XMLCardinality cardinality);

        @Override
        Builder<RC, WC> addElement(XMLElement<RC, WC> element);

        /**
         * Adds an XML choice to this group.
         * @param choice a choice of elements.
         * @return a reference to this builder
         */
        Builder<RC, WC> addChoice(XMLChoice<RC, WC> choice);

        @Override
        XMLParticleGroup<RC, WC> build();
    }

    abstract class AbstractBuilder<RC, WC, B extends Builder<RC, WC>> extends XMLParticle.AbstractBuilder<RC, WC, B> implements Builder<RC, WC> {
        private final List<XMLChoice<RC, WC>> choices = new LinkedList<>();

        @Override
        public B addElement(XMLElement<RC, WC> element) {
            return this.addChoice(XMLChoice.singleton(element));
        }

        @Override
        public B addChoice(XMLChoice<RC, WC> choice) {
            this.choices.add(choice);
            return this.builder();
        }

        protected List<XMLChoice<RC, WC>> getChoices() {
            return this.choices;
        }
    }
}
