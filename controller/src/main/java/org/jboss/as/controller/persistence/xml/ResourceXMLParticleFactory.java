/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import javax.xml.namespace.QName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.SingletonResourceRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.WildcardResourceRegistration;
import org.jboss.as.controller.xml.QNameResolver;
import org.wildfly.common.Assert;

/**
 * A factory for building XML particles for a subsystem resource.
 * @author Paul Ferraro
 */
public interface ResourceXMLParticleFactory extends QNameResolver {

    static <S extends SubsystemSchema<S>> ResourceXMLParticleFactory newInstance(S schema) {
        return newInstance(schema, schema);
    }

    static ResourceXMLParticleFactory newInstance(FeatureFilter filter, QNameResolver resolver) {
        return new DefaultSubsystemXMLParticleFactory(filter, resolver);
    }

    /**
     * Returns a builder of a XML element for the subsystem resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the subsystem resource with the specified registration.
     */
    SubsystemResourceRegistrationXMLElement.Builder element(SubsystemResourceRegistration registration);

    /**
     * Returns a builder of a XML element for the resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the resource with the specified registration.
     */
    SingletonResourceRegistrationXMLElement.Builder element(SingletonResourceRegistration registration);

    /**
     * Returns a builder of a XML element for the resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the resource with the specified registration.
     */
    WildcardResourceRegistrationXMLElement.Builder element(WildcardResourceRegistration registration);

    /**
     * Returns a builder of a XML element for the resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the resource with the specified registration.
     */
    AttributeDefinitionXMLElement.Builder element(AttributeDefinition attribute);

    ResourceXMLElement.Builder element(QName name);

    /**
     * Returns a builder of an XML choice for override resource registrations of the specified resource XML element.
     * @param element the XML element of a wildcard resource.
     * @return a builder of an XML choice for override resource registrations of the specified resource XML element.
     */
    ResourceRegistrationXMLChoice.Builder choice(WildcardResourceRegistrationXMLElement element);

    ResourceXMLAll.Builder all();

    ResourceXMLSequence.Builder sequence();

    ResourceXMLChoice.Builder choice();

    class DefaultSubsystemXMLParticleFactory implements ResourceXMLParticleFactory {
        private final FeatureFilter filter;
        private final QNameResolver resolver;

        DefaultSubsystemXMLParticleFactory(FeatureFilter filter, QNameResolver resolver) {
            this.filter = filter;
            this.resolver = resolver;
        }

        @Override
        public QName resolve(String localName) {
            return this.resolver.resolve(localName);
        }

        @Override
        public SubsystemResourceRegistrationXMLElement.Builder element(SubsystemResourceRegistration registration) {
            return new SubsystemResourceRegistrationXMLElement.DefaultBuilder(registration, this.filter, this.resolver);
        }

        @Override
        public SingletonResourceRegistrationXMLElement.Builder element(SingletonResourceRegistration registration) {
            Assert.assertFalse(registration.getPathElement().isWildcard());
            return new SingletonResourceRegistrationXMLElement.DefaultBuilder(registration, this.filter, this.resolver);
        }

        @Override
        public WildcardResourceRegistrationXMLElement.Builder element(WildcardResourceRegistration registration) {
            Assert.assertTrue(registration.getPathElement().isWildcard());
            return new WildcardResourceRegistrationXMLElement.DefaultBuilder(registration, this.filter, this.resolver);
        }

        @Override
        public AttributeDefinitionXMLElement.Builder element(AttributeDefinition attribute) {
            return new AttributeDefinitionXMLElement.DefaultBuilder(attribute, this.resolver);
        }

        @Override
        public ResourceXMLElement.Builder element(QName name) {
            return new ResourceXMLElement.DefaultBuilder(name, this.filter, this.resolver);
        }

        @Override
        public ResourceRegistrationXMLChoice.Builder choice(WildcardResourceRegistrationXMLElement element) {
            return new ResourceRegistrationXMLChoice.DefaultBuilder(element);
        }

        @Override
        public ResourceXMLChoice.Builder choice() {
            return new ResourceXMLChoice.DefaultBuilder(this.filter, this.resolver);
        }

        @Override
        public ResourceXMLAll.Builder all() {
            return new ResourceXMLAll.DefaultBuilder(this.filter, this.resolver);
        }

        @Override
        public ResourceXMLSequence.Builder sequence() {
            return new ResourceXMLSequence.DefaultBuilder(this.filter, this.resolver);
        }
    }
}
