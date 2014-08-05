/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class SyncMasterDomainDomainHandler implements OperationStepHandler {

    private final List<ModelNode> localOperations;
    private final IgnoredDomainResourceRegistry ignoredResourceRegistry;

    public SyncMasterDomainDomainHandler(List<ModelNode> localOperations, IgnoredDomainResourceRegistry ignoredResourceRegistry) {
        this.localOperations = localOperations;
        this.ignoredResourceRegistry = ignoredResourceRegistry;
    }

    @Override
    public void execute(OperationContext context, ModelNode original) throws OperationFailedException {
        try {
            internalExecute(context, original);
        } catch (OperationFailedException e) {
            e.printStackTrace();
            throw e;
        }
    }


    public void internalExecute(OperationContext context, ModelNode original) throws OperationFailedException {

        assert !context.isBooting();
        final List<ModelNode> remoteOperations = original.require(DOMAIN_MODEL).asList();

        final Node currentRoot = new Node(null, PathAddress.EMPTY_ADDRESS);
        final Node remoteRoot = new Node(null, PathAddress.EMPTY_ADDRESS);

        // Process the local and remote operations
        process(currentRoot, localOperations);
        process(remoteRoot, remoteOperations);

        final ModelNode operations = new ModelNode();
        operations.setEmptyList();

        // Compare the nodes
        compare(currentRoot, remoteRoot, operations, context.getRootResourceRegistration());

        for (final ModelNode operation : operations.asList()) {

            final String operationName = operation.require(OP).asString();
            final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
            if (ignoredResourceRegistry.isResourceExcluded(address)) {
                continue;
            }

            final ImmutableManagementResourceRegistration rootRegistration = context.getRootResourceRegistration();
            final OperationStepHandler stepHandler = rootRegistration.getOperationHandler(address, operationName);
            if(stepHandler != null) {
                context.addStep(operation, stepHandler, OperationContext.Stage.MODEL);
            } else {
                final ImmutableManagementResourceRegistration child = rootRegistration.getSubModel(address);
                if (child == null) {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noSuchResourceType(address));
                } else {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, address));
                }
            }
        }

        context.stepCompleted();
    }

    void compare(Node current, Node remote, ModelNode operations, ImmutableManagementResourceRegistration registration) {

        if (current != null && remote != null) {

            // If the current:add() and remote:add() don't match
            if (current.add != null && remote.add != null) {
                if (!current.add.equals(remote.add)) {
                    // Iterate through all local attribute names
                    for (String attribute : registration.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
                        final AttributeAccess access = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                        // If they are configuration and write attributes (what about others?)
                        if (access.getStorageType() == AttributeAccess.Storage.CONFIGURATION &&
                                access.getAccessType() == AttributeAccess.AccessType.READ_WRITE) {
                            // Compare each attribute
                            boolean hasCurrent = current.add.hasDefined(attribute);
                            boolean hasRemote = remote.add.hasDefined(attribute);
                            if (hasCurrent && hasRemote) {
                                // If they are not equals add the remote one
                                if (!remote.add.get(attribute).equals(current.add.get(attribute))) {
                                    final ModelNode op = Operations.createWriteAttributeOperation(current.address.toModelNode(), attribute, remote.add.get(attribute));
                                    if (remote.attributes.containsKey(attribute)) {
                                        throw new IllegalStateException();
                                    }
                                    remote.attributes.put(attribute, op);
                                }
                            } else if (hasRemote) {
                                final ModelNode op = Operations.createWriteAttributeOperation(current.address.toModelNode(), attribute, remote.add.get(attribute));
                                if (remote.attributes.containsKey(attribute)) {
                                    throw new IllegalStateException();
                                }
                                remote.attributes.put(attribute, op);
                            } else if (hasCurrent) {
                                // If there is no remote equivalent undefine the operation
                                final ModelNode op = Operations.createUndefineAttributeOperation(current.address.toModelNode(), attribute);
                                if (remote.attributes.containsKey(attribute)) {
                                    throw new IllegalStateException();
                                }
                                remote.attributes.put(attribute, op);
                            }
                        }
                    }
                }
                // Process the attributes
                processAttributes(current, remote, operations, registration);
                // Process the children
                processChildren(current, remote, operations, registration);
            }
        } else if (current == null && remote != null) {
            // Just add all the operations
            if (remote.add != null) {
                operations.add(remote.add);
            }
            for (final ModelNode operation : remote.attributes.values()) {
                operations.add(operation);
            }
        } else if (current != null && remote == null) {

            // Remove the children first
            for (final Node child : current.children.values()) {
                compare(child, null, operations, registration.getSubModel(PathAddress.pathAddress(child.element)));
            }

            //
            final ModelNode op = Operations.createRemoveOperation(current.address.toModelNode());
            operations.add(op);

        } else {
            throw new IllegalStateException();
        }

    }

    void processAttributes(final Node current, final Node remote, ModelNode operations, final ImmutableManagementResourceRegistration registration) {

        for (final String attribute : remote.attributes.keySet()) {
            // Remove from current model
            final ModelNode currentOp = current.attributes.remove(attribute);
            if (currentOp == null) {
                operations.add(remote.attributes.get(attribute));
            } else {
                final ModelNode remoteOp = remote.attributes.get(attribute);
                if (!remoteOp.equals(currentOp)) {
                    operations.add(remoteOp);
                }
            }
        }

        // Undefine operations if the remote write-attribute operation does not exist
        for (final String attribute : current.attributes.keySet()) {
            final ModelNode op = Operations.createUndefineAttributeOperation(current.address.toModelNode(), attribute);
            operations.add(op);
        }
    }

    void processChildren(final Node current, final Node remote, ModelNode operations, final ImmutableManagementResourceRegistration registration) {
        for (final Node child : remote.children.values()) {
            final Node currentChild = current.children.remove(child.element);
            compare(currentChild, current, operations, registration.getSubModel(PathAddress.pathAddress(child.element)));
        }

        for (final Node currentChild : current.children.values()) {
            compare(current, null, operations, registration.getSubModel(PathAddress.pathAddress(currentChild.element)));
        }
    }


    void process(Node rootNode, final List<ModelNode> operations) {

        for (final ModelNode operation : operations) {
            final String operationName = operation.get(OP).asString();
            final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
            final Node node;
            if (address.size() == 0) {
                node = rootNode;
            } else {
                node = rootNode.getOrCreate(null, address.iterator(), PathAddress.EMPTY_ADDRESS);
            }
            if (operationName.equals(ADD)) {
                node.add = operation;
            } else if (operationName.equals(WRITE_ATTRIBUTE_OPERATION)) {
                final String name = operation.get(NAME).asString();
                node.attributes.put(name, operation);
            } else {
                // TODO So what should we do with those !?
                node.operations.add(operation);
            }
        }
    }

    static class Node {

        private final PathElement element;
        private final PathAddress address;
        private ModelNode add;
        private Map<String, ModelNode> attributes = new HashMap<>();
        private final List<ModelNode> operations = new ArrayList<>();
        private final Map<PathElement, Node> children = new LinkedHashMap<>();

        private Node(PathElement element, PathAddress address) {
            this.element = element;
            this.address = address;
        }

        Node getOrCreate(final PathElement element, final Iterator<PathElement> i, PathAddress current) {
            if (i.hasNext()) {
                final PathElement next = i.next();
                final PathAddress addr = current.append(next);
                Node node = children.get(next);
                if (node == null) {
                    node = new Node(next, addr);
                    children.put(element, node);
                }
                return node.getOrCreate(next, i, addr);
            } else if (element == null) {
                throw new IllegalStateException();
            } else {
                Node node = children.get(element);
                if (node == null) {
                    node = new Node(element, current);
                    children.put(element, node);
                }
                return node;
            }
        }

    }

}
