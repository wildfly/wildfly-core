/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLContentWriter;
import org.jboss.as.controller.xml.XMLElementReader;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Defines a versioned schema for a subsystem defined via a {@link PersistentResourceXMLDescription}.
 * @author Paul Ferraro
 * @param <S> the schema type
 */
public interface PersistentSubsystemSchema<S extends PersistentSubsystemSchema<S>> extends SubsystemResourceXMLSchema<S> {

    PersistentResourceXMLDescription getXMLDescription();

    @Override
    default SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        PersistentResourceXMLDescription description = this.getXMLDescription();
        return new SubsystemResourceRegistrationXMLElement() {
            @Override
            public XMLCardinality getCardinality() {
                return XMLCardinality.Single.REQUIRED;
            }

            @Override
            public XMLElementReader<Map.Entry<PathAddress, Map<PathAddress, ModelNode>>> getReader() {
                return new XMLElementReader<>() {
                    @Override
                    public void readElement(XMLExtendedStreamReader reader, Map.Entry<PathAddress, Map<PathAddress, ModelNode>> context) throws XMLStreamException {
                        description.parse(reader, context.getKey(), new OperationList(context.getValue()));
                    }
                };
            }

            @Override
            public XMLContentWriter<ModelNode> getWriter() {
                return new XMLContentWriter<>() {
                    @Override
                    public void writeContent(XMLExtendedStreamWriter streamWriter, ModelNode model) throws XMLStreamException {
                        description.persist(streamWriter, model);
                    }

                    @Override
                    public boolean isEmpty(ModelNode content) {
                        return false;
                    }
                };
            }

            @Override
            public QName getName() {
                return new QName(description.getNamespaceURI(), ModelDescriptionConstants.SUBSYSTEM);
            }

            @Override
            public PathElement getPathElement() {
                return description.getPathElement();
            }
        };
    }

    class OperationList extends AbstractCollection<ModelNode> implements List<ModelNode> {
        private final Map<PathAddress, ModelNode> operations;
        private final List<PathAddress> keys;

        OperationList(Map<PathAddress, ModelNode> operations) {
            this(operations, new LinkedList<>(operations.keySet()));
        }

        private OperationList(Map<PathAddress, ModelNode> operations, List<PathAddress> keys) {
            this.operations = operations;
            this.keys = keys;
        }

        @Override
        public boolean add(ModelNode operation) {
            PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
            if (this.operations.put(address, operation) == null) {
                this.keys.add(address);
            }
            return true;
        }

        @Override
        public boolean remove(Object object) {
            ModelNode operation = (ModelNode) object;
            PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
            if (this.operations.remove(address) != null) {
                return this.keys.remove(address);
            }
            return false;
        }

        @Override
        public int size() {
            return this.keys.size();
        }

        @Override
        public Iterator<ModelNode> iterator() {
            return new OperationListIterator(this.operations, this.keys.listIterator());
        }

        @Override
        public boolean addAll(int index, Collection<? extends ModelNode> operations) {
            int start = index;
            for (ModelNode operation : operations) {
                PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
                if (this.operations.put(address, operation) == null) {
                    this.keys.add(start++, address);
                }
            }
            return !operations.isEmpty();
        }

        @Override
        public ModelNode get(int index) {
            return this.operations.get(this.keys.get(index));
        }

        @Override
        public ModelNode set(int index, ModelNode operation) {
            return this.operations.put(this.keys.get(index), operation);
        }

        @Override
        public void add(int index, ModelNode operation) {
            PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
            this.keys.add(index, address);
            this.operations.put(address, operation);
        }

        @Override
        public ModelNode remove(int index) {
            return this.operations.remove(this.keys.remove(index));
        }

        @Override
        public int indexOf(Object object) {
            int result = -1;
            for (Map.Entry<PathAddress, ModelNode> entry : this.operations.entrySet()) {
                if (entry.getValue().equals(object)) {
                    return this.keys.indexOf(entry.getKey());
                }
            }
            return result;
        }

        @Override
        public int lastIndexOf(Object object) {
            return this.indexOf(object);
        }

        @Override
        public ListIterator<ModelNode> listIterator() {
            return new OperationListIterator(this.operations, this.keys.listIterator());
        }

        @Override
        public ListIterator<ModelNode> listIterator(int index) {
            return new OperationListIterator(this.operations, this.keys.listIterator(index));
        }

        @Override
        public List<ModelNode> subList(int fromIndex, int toIndex) {
            return new OperationList(this.operations, this.keys.subList(fromIndex, toIndex));
        }

        private static class OperationListIterator implements ListIterator<ModelNode> {
            private final Map<PathAddress, ModelNode> operations;
            private final ListIterator<PathAddress> keys;

            OperationListIterator(Map<PathAddress, ModelNode> operations, ListIterator<PathAddress> keys) {
                this.operations = operations;
                this.keys = keys;
            }

            @Override
            public boolean hasNext() {
                return this.keys.hasNext();
            }

            @Override
            public ModelNode next() {
                return this.operations.get(this.keys.next());
            }

            @Override
            public boolean hasPrevious() {
                return this.keys.hasPrevious();
            }

            @Override
            public ModelNode previous() {
                return this.operations.get(this.keys.previous());
            }

            @Override
            public int nextIndex() {
                return this.keys.nextIndex();
            }

            @Override
            public int previousIndex() {
                return this.keys.previousIndex();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(ModelNode operation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(ModelNode operation) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
