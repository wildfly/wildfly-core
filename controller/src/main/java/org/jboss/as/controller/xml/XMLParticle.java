/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.function.Predicate;

/**
 * Encapsulates an XML particle.
 */
public interface XMLParticle<RC, WC> extends XMLContentWriter.Provider<WC> {

    XMLCardinality getCardinality();

    XMLContentReader<RC> getReader();

    interface Builder<RC, WC> {
        Builder<RC, WC> withCardinality(XMLCardinality cardinality);

        XMLParticle<RC, WC> build();
    }

    abstract class AbstractBuilder<RC, WC, B extends Builder<RC, WC>> implements Builder<RC, WC> {
        private volatile XMLCardinality cardinality = XMLCardinality.Single.REQUIRED;

        protected final Predicate<XMLCardinality> validator;

        protected AbstractBuilder() {
            this(cardinality -> true);
        }

        protected AbstractBuilder(Predicate<XMLCardinality> validator) {
            this.validator = validator;
        }

        @Override
        public B withCardinality(XMLCardinality cardinality) {
            if (!this.validator.test(cardinality)) {
                throw new IllegalArgumentException(cardinality.toString());
            }
            this.cardinality = cardinality;
            return this.builder();
        }

        protected XMLCardinality getCardinality() {
            return this.cardinality;
        }

        protected abstract B builder();
    }

    class DefaultXMLParticle<RC, WC> implements XMLParticle<RC, WC> {
        private final XMLCardinality cardinality;
        private final XMLContentReader<RC> reader;
        private final XMLContentWriter<WC> writer;

        protected DefaultXMLParticle(XMLCardinality cardinality, XMLContentReader<RC> reader, XMLContentWriter<WC> writer) {
            this.cardinality = cardinality;
            this.reader = reader;
            this.writer = writer;
        }

        @Override
        public XMLContentReader<RC> getReader() {
            return this.reader;
        }

        @Override
        public XMLContentWriter<WC> getWriter() {
            return this.writer;
        }

        @Override
        public XMLCardinality getCardinality() {
            return this.cardinality;
        }
    }
}
