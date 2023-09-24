/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChildAddOperationFinder {

    static Map<PathElement, ChildAddOperationEntry> findAddChildOperations(PathAddress parentAddress, MutabilityChecker mutabilityChecker, ImmutableManagementResourceRegistration resourceRegistration){
        Map<PathElement, ChildAddOperationEntry> operations = new HashMap<PathElement, ChildAddOperationEntry>();
        for(PathElement childElement : resourceRegistration.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
            if (!mutabilityChecker.mutable(parentAddress.append(childElement))) {
                continue;
            }
            final ImmutableManagementResourceRegistration childReg = resourceRegistration.getSubModel(PathAddress.pathAddress(childElement));
            final Map<String, OperationEntry> registeredOps = childReg.getOperationDescriptions(PathAddress.EMPTY_ADDRESS, false);
            final OperationEntry childAdd = registeredOps.get(ADD);
            if (childAdd != null) {
                operations.put(childElement, new ChildAddOperationEntry(childAdd, childElement));
            }
        }
        return operations;
    }

    static ChildAddOperationEntry findAddChildOperation(PathAddress parentAddress, MutabilityChecker mutabilityChecker, ImmutableManagementResourceRegistration resourceRegistration, String addName){
        for(PathElement childElement : resourceRegistration.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
            if (!mutabilityChecker.mutable(parentAddress.append(childElement))) {
                continue;
            }
            final ImmutableManagementResourceRegistration childReg = resourceRegistration.getSubModel(PathAddress.pathAddress(childElement));
            final Map<String, OperationEntry> registeredOps = childReg.getOperationDescriptions(PathAddress.EMPTY_ADDRESS, false);
            final OperationEntry childAdd = registeredOps.get(ADD);
            if (childAdd != null) {
                if (NameConverter.createValidAddOperationName(childElement).equals(addName)) {
                    return new ChildAddOperationEntry(childAdd, childElement);
                }
            }
        }
        return null;
    }

    static class ChildAddOperationEntry {
        private final OperationEntry op;
        private final PathElement element;

        public ChildAddOperationEntry(OperationEntry op, PathElement element) {
            this.op = op;
            this.element = element;
        }

        public OperationEntry getOperationEntry() {
            return op;
        }

        public PathElement getElement() {
            return element;
        }
    }
}
