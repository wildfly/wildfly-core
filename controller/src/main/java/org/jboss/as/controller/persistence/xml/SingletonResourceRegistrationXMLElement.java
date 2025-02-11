/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence.xml;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.FeatureFilter;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.xml.QNameResolver;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContent;
import org.jboss.as.controller.xml.XMLContentReader;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates an XML element for a singleton (i.e. non-wildcard) resource registration.
 * @author Paul Ferraro
 */
public interface SingletonResourceRegistrationXMLElement extends ResourceRegistrationXMLElement {

    interface Builder extends ResourceRegistrationXMLElement.Builder<SingletonResourceRegistrationXMLElement, Builder> {
        /**
         * Overrides the key used to index the generated operation.
         * @param operationKey an operation key
         * @return a reference to this builder.
         */
        Builder withOperationKey(PathElement operationKey);

        /**
         * Indicates that this element can be omitted if all of its attributes are undefined and any child resources are also empty.
         * @return a reference to this builder.
         */
        Builder omitIfEmpty();
    }

    class DefaultBuilder extends ResourceRegistrationXMLElement.AbstractBuilder<SingletonResourceRegistrationXMLElement, Builder> implements Builder {
        private volatile PathElement operationKey;
        private volatile boolean omitIfEmpty = false;

        DefaultBuilder(ResourceRegistration registration, FeatureFilter filter, QNameResolver resolver) {
            super(registration, filter, resolver);
            PathElement path = registration.getPathElement();
            Assert.assertFalse(path.isWildcard());
            this.operationKey = path;
        }

        @Override
        public Builder withOperationKey(PathElement operationKey) {
            this.operationKey = operationKey;
            return this;
        }

        @Override
        public Builder omitIfEmpty() {
            this.omitIfEmpty = true;
            return this;
        }

        @Override
        protected Builder builder() {
            return this;
        }

        @Override
        public SingletonResourceRegistrationXMLElement build() {
            ResourceRegistration registration = this.getResourceRegistration();
            PathElement path = registration.getPathElement();
            PathElement pathKey = this.operationKey;
            QName name = this.getElementName().apply(path);

            Collection<AttributeDefinition> attributes = this.getAttributes();
            AttributeDefinitionXMLConfiguration configuration = this.getConfiguration();

            XMLCardinality cardinality = this.getCardinality();
            XMLContentReader<ModelNode> attributesReader = new ResourceAttributesXMLContentReader(attributes, configuration);
            XMLContentWriter<ModelNode> attributesWriter = new ResourceAttributesXMLContentWriter(attributes, configuration);
            XMLContent<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>, ModelNode> content = this.getContent();

            XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> resourceReader = new ResourceXMLContainerReader(name, attributesReader, content);
            XMLContentWriter<ModelNode> resourceWriter = new ResourceXMLContainerWriter<>(name, attributesWriter, Function.identity(), content);

            BiConsumer<Map<PathAddress, ModelNode>, PathAddress> operationTransformation = this.getOperationTransformation();
            XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> elementReader = new XMLContentReader<>() {
                @Override
                public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                    PathAddress parentOperationKey = context.getKey();
                    Map<PathAddress, ModelNode> operations = context.getValue();

                    ModelNode parentOperation = (parentOperationKey.size() > 0) ? operations.get(parentOperationKey) : null;
                    PathAddress parentAddress = (parentOperation != null) ? PathAddress.pathAddress(parentOperation.get(ModelDescriptionConstants.OP_ADDR)) : PathAddress.EMPTY_ADDRESS;
                    PathAddress operationAddress = parentAddress.append(path);
                    PathAddress operationKey = parentOperationKey.append(pathKey);
                    ModelNode operation = Util.createAddOperation(operationAddress);
                    operations.put(operationKey, operation);

                    resourceReader.readElement(reader, Map.entry(operationKey, operations));
                    operationTransformation.accept(operations, operationKey);
                }
            };
            boolean omitIfEmpty = this.omitIfEmpty;
            XMLContentWriter<ModelNode> elementWriter = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, ModelNode parentModel) throws XMLStreamException {
                    String[] pair = path.getKeyValuePair();
                    if (parentModel.hasDefined(pair)) {
                        ModelNode model = parentModel.get(pair);
                        if (!omitIfEmpty || !resourceWriter.isEmpty(model)) {
                            resourceWriter.writeContent(writer, model);
                        }
                    }
                }

                @Override
                public boolean isEmpty(ModelNode parentModel) {
                    String[] pair = path.getKeyValuePair();
                    return !parentModel.hasDefined(pair) || resourceWriter.isEmpty(parentModel.get(pair));
                }
            };
            return new DefaultSingletonResourceRegistrationXMLElement(registration, name, cardinality, elementReader, elementWriter);
        }
    }

    class DefaultSingletonResourceRegistrationXMLElement extends DefaultResourceRegistrationXMLElement implements SingletonResourceRegistrationXMLElement {

        DefaultSingletonResourceRegistrationXMLElement(ResourceRegistration registration, QName name, XMLCardinality cardinality, XMLContentReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> reader, XMLContentWriter<ModelNode> writer) {
            super(registration, name, cardinality, reader, writer);
        }
    }
}
