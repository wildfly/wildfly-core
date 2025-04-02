/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import javax.xml.namespace.QName;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Stability;

/**
 * A factory for creating builders of XML components.
 * N.B. All particles created by this factory have a default cardinality of {@link org.jboss.as.controller.xml.XMLCardinality.Single#REQUIRED}.
 * N.B. All attributes created by this factory have a default usage of {@link org.jboss.as.controller.xml.XMLAttribute.Use#OPTIONAL}.
 * @author Paul Ferraro
 * @param <RC> the reader context
 * @param <WC> the writer context
 */
public interface XMLComponentFactory<RC, WC> extends FeatureRegistry, QNameResolver {

    /**
     * Creates a new particle factory for the specified schema.
     * @param <S> the subsystem schema type
     * @param schema a subsystem schema
     * @return a particle factory
     */
    static <S extends IntVersionSchema<S>, RC, WC> XMLComponentFactory<RC, WC> newInstance(S schema) {
        return newInstance(schema, schema);
    }

    /**
     * Creates a new particle factory for the specified feature registry and qualified name resolver.
     * @param registry a feature registry
     * @param resolver a qualified name resolver
     * @return a particle factory
     */
    static <RC, WC> XMLComponentFactory<RC, WC> newInstance(FeatureRegistry registry, QNameResolver resolver) {
        return new DefaultXMLComponentFactory<>(registry, resolver);
    }

    /**
     * Returns a builder of an XML attribute, using the specified name.
     * @param name the qualified attribute name
     * @return a builder of an XML attribute.
     */
    default XMLAttribute.Builder<RC, WC> attribute(QName name) {
        return this.attribute(name, Stability.DEFAULT);
    }

    /**
     * Returns a builder of an XML attribute, using the specified name and stability.
     * @param name the qualified attribute name
     * @param stability the stability of this attribute
     * @return a builder of an XML element.
     */
    XMLAttribute.Builder<RC, WC> attribute(QName name, Stability stability);

    /**
     * Returns a builder of an XML element, using the specified name.
     * @param name the element name
     * @return a builder of an XML element.
     */
    default XMLElement.Builder<RC, WC> element(QName name) {
        return this.element(name, this.getStability());
    }

    /**
     * Returns a builder of an XML element, using the specified name.
     * @param name the element name
     * @param stability the stability of this element
     * @return a builder of an XML element.
     */
    XMLElement.Builder<RC, WC> element(QName name, Stability stability);

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

    class DefaultXMLComponentFactory<RC, WC> implements XMLComponentFactory<RC, WC> {
        private final FeatureRegistry registry;
        private final QNameResolver resolver;

        DefaultXMLComponentFactory(FeatureRegistry registry, QNameResolver resolver) {
            this.registry = registry;
            this.resolver = resolver;
        }

        @Override
        public QName resolve(String localName) {
            return this.resolver.resolve(localName);
        }

        @Override
        public Stability getStability() {
            return this.registry.getStability();
        }

        @Override
        public XMLAttribute.Builder<RC, WC> attribute(QName name, Stability stability) {
            return new XMLAttribute.DefaultBuilder<>(name, stability);
        }

        @Override
        public XMLElement.Builder<RC, WC> element(QName name, Stability stability) {
            return new XMLElement.DefaultBuilder<>(name, stability);
        }

        @Override
        public XMLChoice.Builder<RC, WC> choice() {
            return new XMLChoice.DefaultBuilder<>(this.registry);
        }

        @Override
        public XMLAll.Builder<RC, WC> all() {
            return new XMLAll.DefaultBuilder<>(this.registry);
        }

        @Override
        public XMLSequence.Builder<RC, WC> sequence() {
            return new XMLSequence.DefaultBuilder<>(this.registry);
        }
    }
}
