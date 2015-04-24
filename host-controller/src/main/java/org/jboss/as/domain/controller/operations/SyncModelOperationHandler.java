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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.dmr.ModelNode;

/**
 * Internal {@code OperationStepHandler} which synchronizes the model based on a comparison of local and remote operations.
 *
 * Basically it compares the current state of the model to the one from the master. Where the initial connection to the
 * master tries to sync the whole model, fetching missing configuration only looks at server-groups and it's references,
 * ignoring all other resources.
 *
 * @author Emanuel Muckenhuber
 */
class SyncModelOperationHandler implements OperationStepHandler {

    static final String ORDERED_CHILD_TYPES_HEADER = ModelDescriptionConstants.ORDERED_CHILD_TYPES_HEADER;

    private final Resource remoteModel;
    private final List<ModelNode> localOperations;
    private final Set<String> missingExtensions;
    private final SyncModelParameters parameters;

    SyncModelOperationHandler(List<ModelNode> localOperations, Resource remoteModel, Set<String> missingExtensions,
                              SyncModelParameters parameters) {
        this.localOperations = localOperations;
        this.remoteModel = remoteModel;
        this.missingExtensions = missingExtensions;
        this.parameters = parameters;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // In case we want to automatically ignore extensions we would need to add them before describing the operations
        // This is also required for resolving the corresponding OperationStepHandler here
//        final ManagementResourceRegistration registration = context.getResourceRegistrationForUpdate();
//        for (String extension : missingExtensions) {
//            final PathElement element = PathElement.pathElement(EXTENSION, extension);
//            if (ignoredResourceRegistry.isResourceExcluded(PathAddress.pathAddress(element))) {
//                continue;
//            }
//            context.addResource(PathAddress.pathAddress(element), new ExtensionResource(extension, extensionRegistry));
//            initializeExtension(extension, registration);
//        }
        // There should be no missing extensions for now, unless they are manually ignored
        if (!missingExtensions.isEmpty()) {
            throw DomainControllerLogger.HOST_CONTROLLER_LOGGER.missingExtensions(missingExtensions);
        }

        final ModelNode readOp = new ModelNode();
        readOp.get(OP).set(ReadMasterDomainOperationsHandler.OPERATION_NAME);
        readOp.get(OP_ADDR).setEmptyList();

        // Describe the operations based on the remote model
        final ReadMasterDomainOperationsHandler readOperationsHandler = new ReadMasterDomainOperationsHandler();
        final HostControllerRegistrationHandler.OperationExecutor operationExecutor = parameters.getOperationExecutor();
        final ModelNode result = operationExecutor.executeReadOnly(readOp, remoteModel, readOperationsHandler, ModelController.OperationTransactionControl.COMMIT);
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            context.getFailureDescription().set(result.get(FAILURE_DESCRIPTION));
            return;
        }

        final List<ModelNode> remoteOperations = result.get(RESULT).asList();

        // Create the node models based on the operations
        final Node currentRoot = new Node(null, PathAddress.EMPTY_ADDRESS);
        final Node remoteRoot = new Node(null, PathAddress.EMPTY_ADDRESS);

        // Process the local and remote operations
        process(currentRoot, localOperations);
        process(remoteRoot, remoteOperations);

        // Compare the nodes and create the operations to sync the model
        final List<ModelNode> operations = new ArrayList<>();
        processAttributes(currentRoot, remoteRoot, operations, context.getRootResourceRegistration());
        processChildren(currentRoot, remoteRoot, operations, context.getRootResourceRegistration());

        // Reverse, since we are adding the steps on top of the queue
        final List<ModelNode> ops = new ArrayList<>();
        ops.addAll(operations);
        Collections.reverse(ops);

        for (final ModelNode op : ops) {

            final String operationName = op.require(OP).asString();
            final PathAddress address = PathAddress.pathAddress(op.require(OP_ADDR));
            if (parameters.getIgnoredResourceRegistry().isResourceExcluded(address)) {
                continue;
            }
            // Ignore all extension:add operations, since we've added them before
//            if (address.size() == 1 && EXTENSION.equals(address.getElement(0).getKey()) && ADD.equals(operationName)) {
//                continue;
//            }

            final ImmutableManagementResourceRegistration rootRegistration = context.getRootResourceRegistration();
            final OperationStepHandler stepHandler = rootRegistration.getOperationHandler(address, operationName);
            if(stepHandler != null) {
                context.addStep(op, stepHandler, OperationContext.Stage.MODEL, true);
            } else {
                final ImmutableManagementResourceRegistration child = rootRegistration.getSubModel(address);
                if (child == null) {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noSuchResourceType(address));
                } else {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, address));
                }
            }

        }

        if (operations.size() > 0 && parameters.isFullModelTransfer() && !context.isBooting()) {
            //Only do this is if it is a full model transfer as a result of a _reconnect_ to the DC.
            //When fetching missing configuration while connected, the servers will get put into reload-required as a
            // result of changing the server-group, profile or the socket-binding-group
            context.addStep(new SyncServerStateOperationHandler(parameters, operations),
                    OperationContext.Stage.MODEL,
                    true);
        }
    }

    private void processAttributes(final Node current, final Node remote, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration) {

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

    private void processChildren(final Node current, final Node remote, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration) {
        ChildContext childContext = ChildContext.create(current, remote);

        for (String type : childContext.orderedInsertCapableTypes) {
            processOrderedChildrenOfType(childContext, type, operations, registration, true);
        }
        for (String type : childContext.orderedNotInsertCapableTypes) {
            processOrderedChildrenOfType(childContext, type, operations, registration, false);
        }
        for (String type : childContext.nonOrderedTypes) {
            processNonOrderedChildrenOfType(childContext, type, operations, registration);
        }
    }

    private void processOrderedChildrenOfType(final ChildContext childContext, final String type, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration, boolean attemptInsert) {
        final Map<PathElement, Node> remoteChildren = childContext.getRemoteChildrenOfType(type);
        final Map<PathElement, Node> currentChildren = childContext.getCurrentChildrenOfType(type);

        //Anything which is local only, we can delete right away
        removeCurrentOnlyChildren(currentChildren, remoteChildren, operations, registration);

        //Now figure out the merging strategy
        final Map<PathElement, Integer> currentIndexes = new HashMap<>();
        int i = 0;
        for (PathElement element : currentChildren.keySet()) {
            currentIndexes.put(element, i++);
        }
        Map<Integer, PathElement> addedIndexes = new LinkedHashMap<Integer, PathElement>();
        i = 0;
        int lastCurrent = -1;
        boolean differentOrder = false;
        boolean allAddsAtEnd = true;
        for (PathElement element : remoteChildren.keySet()) {
            Integer currentIndex = currentIndexes.get(element);
            if (currentIndex == null) {
                addedIndexes.put(i, element);
                if (allAddsAtEnd && i <= currentIndexes.size() - 1) {
                    //Some of the adds are in the middle, requiring an insert or a remove + readd
                    allAddsAtEnd = false;
                }
            } else {
                if (!differentOrder && currentIndex < lastCurrent) {
                    //We can't do inserts, the models have changed too much
                    differentOrder = true;
                }
                lastCurrent = currentIndex;
            }
            i++;
        }


        processOrderedChildModels(currentChildren, remoteChildren, addedIndexes, attemptInsert, differentOrder, allAddsAtEnd,
                operations, registration);
    }

    private void processOrderedChildModels(final Map<PathElement, Node> currentChildren,
            final Map<PathElement, Node> remoteChildren, Map<Integer, PathElement> addedIndexes,
            boolean attemptInsert, boolean differentOrder,
            boolean allAddsAtEnd, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration) {
        if (!differentOrder && (addedIndexes.size() == 0 || allAddsAtEnd)) {
            //Just 'compare' everything
            for (Node current : currentChildren.values()) {
                Node remote = remoteChildren.get(current.element);
                compareExistsInBothModels(current, remote, operations, registration.getSubModel(PathAddress.pathAddress(current.element)));
            }
            if (addedIndexes.size() > 0) {
                //Add the new ones to the end
                for (PathElement element : addedIndexes.values()) {
                    Node remote = remoteChildren.get(element);
                    addChildRecursive(remote, operations, registration.getSubModel(PathAddress.pathAddress(element)));
                }
            }
        } else {
            //We had some inserts, add them in order
            boolean added = false;
            if (attemptInsert && !differentOrder) {
                added = true;
                //Do the insert
                int i = 0;
                for (Node remote : remoteChildren.values()) {
                    if (addedIndexes.get(i) != null) {
                        //insert the node
                        remote.add.get(ADD_INDEX).set(i);
                        ImmutableManagementResourceRegistration childReg = registration.getSubModel(PathAddress.pathAddress(remote.element));
                        if (i == 0) {
                            DescriptionProvider desc = childReg.getOperationDescription(PathAddress.EMPTY_ADDRESS, ADD);
                            if (!desc.getModelDescription(Locale.ENGLISH).hasDefined(REQUEST_PROPERTIES, ADD_INDEX)) {
                                //Although the resource type supports ordering, the add handler was not set up to do an indexed add
                                //so we give up and go to remove + re-add
                                added = false;
                                break;
                            }
                        }
                        addChildRecursive(remote, operations, childReg);
                    } else {
                        //'compare' the nodes
                        Node current = currentChildren.get(remote.element);
                        compareExistsInBothModels(current, remote, operations, registration.getSubModel(PathAddress.pathAddress(current.element)));
                    }
                    i++;
                }
            }

            if (!added) {
                //Remove and re-add everything
                //We could do this more fine-grained, but for now let's just drop everything that has been added and readd
                for (Node current : currentChildren.values()) {
                    removeChildRecursive(current, operations, registration.getSubModel(PathAddress.pathAddress(current.element)));
                }
                for (Node remote : remoteChildren.values()) {
                    addChildRecursive(remote, operations, registration.getSubModel(PathAddress.pathAddress(remote.element)));
                }
            }
        }
    }

    private void processNonOrderedChildrenOfType(final ChildContext childContext, final String type, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration) {
        final Map<PathElement, Node> remoteChildren = childContext.getRemoteChildrenOfType(type);
        final Map<PathElement, Node> currentChildren = childContext.getCurrentChildrenOfType(type);
        for (final Node remoteChild : remoteChildren.values()) {
            final Node currentChild = currentChildren == null ? null : currentChildren.remove(remoteChild.element);
            if (currentChild != null && remoteChild != null) {
                compareExistsInBothModels(currentChild, remoteChild, operations, registration.getSubModel(PathAddress.pathAddress(currentChild.element)));
            } else if (currentChild == null && remoteChild != null) {
                addChildRecursive(remoteChild, operations, registration.getSubModel(PathAddress.pathAddress(remoteChild.element)));
            } else if (currentChild != null && remoteChild == null) {
                removeChildRecursive(currentChild, operations, registration.getSubModel(PathAddress.pathAddress(currentChild.element)));
            } else {
                throw new IllegalStateException();
            }
        }
        for (final Node currentChild : currentChildren.values()) {
            removeChildRecursive(currentChild, operations, registration.getSubModel(PathAddress.pathAddress(currentChild.element)));
        }
    }

    private void addChildRecursive(Node remote, List<ModelNode> operations, ImmutableManagementResourceRegistration registration) {
        assert remote != null : "remote cannot be null";
        // Just add all the remote operations
        if (remote.add != null) {
            operations.add(remote.add);
        }
        for (final ModelNode operation : remote.attributes.values()) {
            operations.add(operation);
        }
        for (final ModelNode operation : remote.operations) {
            operations.add(operation);
        }
        //
        for (final Map.Entry<String, Map<PathElement, Node>> childrenByType : remote.childrenByType.entrySet()) {
            for (final Node child : childrenByType.getValue().values()) {
                addChildRecursive(child, operations, registration.getSubModel(PathAddress.pathAddress(child.element)));
            }
        }
    }

    private void removeCurrentOnlyChildren(Map<PathElement, Node> currentChildren, Map<PathElement, Node> remoteChildren, final List<ModelNode> operations, final ImmutableManagementResourceRegistration registration) {
        //Remove everything which exists in current and not in the remote
        List<PathElement> removedElements = new ArrayList<PathElement>();
        for (Node node : currentChildren.values()) {
            if (!remoteChildren.containsKey(node.element)) {
                removedElements.add(node.element);
            }
        }
        for (PathElement removedElement : removedElements) {
            Node removedCurrent = currentChildren.remove(removedElement);
            removeChildRecursive(removedCurrent, operations, registration.getSubModel(PathAddress.pathAddress(removedElement)));
        }
    }

    private void compareExistsInBothModels(Node current, Node remote, List<ModelNode> operations, ImmutableManagementResourceRegistration registration) {
        assert current != null : "current cannot be null";
        assert remote != null : "remote cannot be null";

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
            // TODO process other operations maps, lists etc.
            // Process the children
            processChildren(current, remote, operations, registration);
        }
    }

    private void removeChildRecursive(Node current, List<ModelNode> operations, ImmutableManagementResourceRegistration registration) {
        // Remove the children first
        for (final Map.Entry<String, Map<PathElement, Node>> childrenByType : current.childrenByType.entrySet()) {
            for (final Node child : childrenByType.getValue().values()) {
                removeChildRecursive(child, operations, registration.getSubModel(PathAddress.pathAddress(child.element)));
            }
        }
        // Add the remove operation
        if (registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, REMOVE) != null) {
            final ModelNode op = Operations.createRemoveOperation(current.address.toModelNode());
            operations.add(op);
        }

    }

    private void process(Node rootNode, final List<ModelNode> operations) {

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
                node.operations.add(operation);
            }

            if (operation.hasDefined(OPERATION_HEADERS, SyncModelOperationHandler.ORDERED_CHILD_TYPES_HEADER)) {
                final ModelNode headers = operation.get(OPERATION_HEADERS);
                List<ModelNode> orderedChildTypes =
                        headers.remove(SyncModelOperationHandler.ORDERED_CHILD_TYPES_HEADER).asList();
                if (headers.keys().size() == 0) {
                    operation.remove(OPERATION_HEADERS);
                }
                for (ModelNode childType : orderedChildTypes ) {
                    node.orderedChildTypes.add(childType.asString());
                }
            }
        }
    }

    private static class Node {

        private final PathElement element;
        private final PathAddress address;
        private ModelNode add;
        private Map<String, ModelNode> attributes = new HashMap<>();
        private final List<ModelNode> operations = new ArrayList<>();
        private final Set<String> orderedChildTypes = new HashSet<>();
        private final Map<String, Map<PathElement, Node>> childrenByType = new LinkedHashMap<>();

        private Node(PathElement element, PathAddress address) {
            this.element = element;
            this.address = address;
        }

        Node getOrCreate(final PathElement element, final Iterator<PathElement> i, PathAddress current) {

            if (i.hasNext()) {
                final PathElement next = i.next();
                final PathAddress addr = current.append(next);
                Map<PathElement, Node> children = childrenByType.get(next.getKey());
                if (children == null) {
                    children = new LinkedHashMap<PathElement, SyncModelOperationHandler.Node>();
                    childrenByType.put(next.getKey(), children);
                }
                Node node = children.get(next);
                if (node == null) {
                    node = new Node(next, addr);
                    children.put(next, node);
                }
                return node.getOrCreate(next, i, addr);
            } else if (element == null) {
                throw new IllegalStateException();
            } else {
                if (address.equals(current)) {
                    return this;
                } else {
                    throw new IllegalStateException(current.toString());
                }
            }
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            toString(sb, 0);
            return sb.toString();
        }

        void toString(StringBuilder builder, int depth) {
            for (int i = 0; i < depth; i++) {
                builder.append(" ");
            }
            builder.append("Node: {").append(address).append("\n");
            for (Map<PathElement, Node> children : childrenByType.values()) {
                for (Node child : children.values()) {
                    child.toString(builder, depth + 1);
                }
            }
            for (int i = 0; i < depth; i++) {
                builder.append(" ");
            }
            builder.append("}\n");
        }
    }

    private static class ChildContext {
        private final Node current;
        private final Node remote;
        /** These can be inserted by doing an add with an index */
        private final Set<String> orderedInsertCapableTypes;
        /** These can not be adjusted by doing an insert with an index, instead it will need to remove everything after the index and re-add */
        private final Set<String> orderedNotInsertCapableTypes;
        /** These types are not ordered */
        private final Set<String> nonOrderedTypes;

        private ChildContext(
                Node current,
                Node remote,
                Set<String> orderedInsertCapableTypes,
                Set<String> orderedNotInsertCapableTypes,
                Set<String> nonOrderedTypes) {
            this.current = current;
            this.remote = remote;
            this.orderedInsertCapableTypes = orderedInsertCapableTypes;
            this.orderedNotInsertCapableTypes = orderedNotInsertCapableTypes;
            this.nonOrderedTypes = nonOrderedTypes;
        }

        static ChildContext create(Node current, Node remote) {
            final Set<String> orderedInsertCapableTypes = getOrderedInsertCapable(current);
            final Set<String> orderedNotInsertCapableTypes = getOrderedNotInsertCapable(current, remote);
            final Set<String> nonOrderedTypes = new HashSet<String>();
            if (current != null) {
                for (String type : current.childrenByType.keySet()) {
                    if (!orderedInsertCapableTypes.contains(type) && !orderedNotInsertCapableTypes.contains(type)) {
                        nonOrderedTypes.add(type);
                    }
                }
            }
            if (remote != null) {
                for (String type : remote.childrenByType.keySet()) {
                    if (!orderedInsertCapableTypes.contains(type) && !orderedNotInsertCapableTypes.contains(type)) {
                        nonOrderedTypes.add(type);
                    }
                }
            }
            return new ChildContext(current, remote, orderedInsertCapableTypes, orderedNotInsertCapableTypes, nonOrderedTypes);
        }

        private static Set<String> getOrderedInsertCapable(Node current) {
            if (current.orderedChildTypes.size() > 0) {
                return new HashSet<String>(current.orderedChildTypes);
            } else {
                return Collections.emptySet();
            }
        }

        private static Set<String> getOrderedNotInsertCapable(Node current, Node remote) {
            if (remote.orderedChildTypes.size() > 0) {
                HashSet<String> orderedNotInsertCapable = null;
                for (String type : remote.orderedChildTypes) {
                    if (!current.orderedChildTypes.contains(type)) {
                        if (orderedNotInsertCapable == null) {
                            orderedNotInsertCapable = new HashSet<String>();
                        }
                        orderedNotInsertCapable.add(type);
                    }
                }
                if (orderedNotInsertCapable != null) {
                    return orderedNotInsertCapable;
                }
            }
            return Collections.emptySet();
        }

        Map<PathElement, Node> getRemoteChildrenOfType(String type) {
            Map<PathElement, Node> map = remote.childrenByType.get(type);
            if (map != null) {
                return map;
            }
            return Collections.emptyMap();
        }

        Map<PathElement, Node> getCurrentChildrenOfType(String type) {
            Map<PathElement, Node> map = current.childrenByType.get(type);
            if (map != null) {
                return map;
            }
            return Collections.emptyMap();
        }
    }

}
