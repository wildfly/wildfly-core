/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Map;

import javax.xml.namespace.QName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLParticleFactory;
import org.jboss.dmr.ModelNode;

/**
 * A factory for building XML particles for a subsystem resource.
 * @author Paul Ferraro
 */
public interface ResourceXMLParticleFactory extends XMLParticleFactory<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode>, QNameResolver {

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
    default ResourceXMLElement.Builder element(SubsystemResourceRegistration registration) {
        return this.element((ResourceRegistration) registration).withElementLocalName(ResourceXMLElementLocalName.KEY);
    }

    /**
     * Returns a builder of a XML element for the resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the resource with the specified registration.
     */
    ResourceXMLElement.Builder element(ResourceRegistration registration);

    /**
     * Returns a builder of a XML element for the resource with the specified registration.
     * @param registration a subsystem resource registration
     * @return a builder of a XML element for the resource with the specified registration.
     */
    AttributeDefinitionXMLElement.Builder element(AttributeDefinition attribute);

    @Override
    ResourceModelXMLElement.Builder element(QName name);

    /**
     * Returns a builder of an XML choice for override resource registrations of the specified resource XML element.
     * @param element the XML element of a wildcard resource.
     * @return a builder of an XML choice for override resource registrations of the specified resource XML element.
     */
    ResourceXMLChoice.Builder choice(ResourceXMLElement element);

    @Override
    ResourceXMLAll.Builder all();

    @Override
    ResourceXMLSequence.Builder sequence();

    @Override
    ResourceModelXMLChoice.Builder choice();

    class DefaultSubsystemXMLParticleFactory extends XMLParticleFactory.DefaultXMLParticleFactory<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> implements ResourceXMLParticleFactory {
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
        public ResourceXMLElement.Builder element(ResourceRegistration registration) {
            return new ResourceXMLElement.DefaultBuilder(registration, this.filter, this.resolver);
        }

        @Override
        public AttributeDefinitionXMLElement.Builder element(AttributeDefinition attribute) {
            return new AttributeDefinitionXMLElement.DefaultBuilder(attribute, this.resolver);
        }

        @Override
        public ResourceModelXMLElement.Builder element(QName name) {
            return new ResourceModelXMLElement.DefaultBuilder(name, this.filter, this.resolver);
        }

        @Override
        public ResourceXMLChoice.Builder choice(ResourceXMLElement element) {
            return new ResourceXMLChoice.DefaultBuilder(element);
        }

        @Override
        public ResourceModelXMLChoice.Builder choice() {
            return new ResourceModelXMLChoice.DefaultBuilder(this.filter, this.resolver);
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
