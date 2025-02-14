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

    interface Builder<RC, WC, T extends XMLParticleGroup<RC, WC>, B extends Builder<RC, WC, T, B>> extends XMLElementGroup.Builder<RC, WC, T, B> {
        /**
         * Adds an XML choice to this group.
         * @param choice a choice of elements.
         * @return a reference to this builder
         */
        B addChoice(XMLChoice<RC, WC> choice);
    }

    abstract class AbstractBuilder<RC, WC, T extends XMLParticleGroup<RC, WC>, B extends Builder<RC, WC, T, B>> extends XMLParticle.AbstractBuilder<RC, WC, T, B> implements Builder<RC, WC, T, B> {
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
