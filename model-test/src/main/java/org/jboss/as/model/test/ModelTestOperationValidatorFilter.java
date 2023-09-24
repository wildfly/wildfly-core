/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelTestOperationValidatorFilter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean validateNone;
    private final List<OperationEntry> entries;

    private ModelTestOperationValidatorFilter(List<OperationEntry> entries) {
        this.entries = createStandardEntries(entries);
        validateNone = false;
    }

    private ModelTestOperationValidatorFilter(boolean validateNone) {
        this.validateNone = validateNone;
        entries = validateNone ? null : createStandardEntries(null);
    }

    private static List<OperationEntry> createStandardEntries(List<OperationEntry> provided) {
        if (provided == null) {
            provided = new ArrayList<>();
        }
        // Don't check the private internal op that deregisters itself before we get a chance to validate it
        OperationEntry oe = new OperationEntry(PathAddress.EMPTY_ADDRESS, "boottime-controller-initializer-step", Action.NOCHECK, null);
        provided.add(oe);
        return provided;
    }

    public static ModelTestOperationValidatorFilter createValidateNone() {
        return new ModelTestOperationValidatorFilter(true);
    }

    public static ModelTestOperationValidatorFilter createValidateAll() {
        return new ModelTestOperationValidatorFilter(false);
    }

    public static Builder createBuilder() {
        return new Builder();
    }

    public ModelNode adjustForValidation(ModelNode op) {
        if (validateNone) {
            return null;
        } else if (entries == null) {
            return op;
        }

        //TODO handle composites

        ModelNode addr = op.get(OP_ADDR);
        PathAddress address = PathAddress.pathAddress(addr);

        String name = op.get(OP).asString();

        for (OperationEntry entry : entries) {
            if (nameMatch(name, entry) && addressMatch(address, entry)) {
                if (entry.action == Action.NOCHECK) {
                    return null;
                } else if (entry.action == Action.RESOLVE){
                    op = resolve(op);
                } else if (entry.operationFixer != null){
                    op = entry.operationFixer.fixOperation(op);
                }
            }
        }
        return op;
    }

    private static ModelNode resolve(ModelNode unresolved) {
        try {
            return ExpressionResolver.TEST_RESOLVER.resolveExpressions(unresolved);
        } catch (OperationFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean nameMatch(String opName, OperationEntry entry) {
        if (entry.name.equals("*")) {
            return true;
        }
        return opName.equals(entry.name);
    }

    private boolean addressMatch(PathAddress opAddr, OperationEntry entry) {
        boolean match = entry.address.size() == opAddr.size();
        if (match) {
            for (int i = 0; i < opAddr.size(); i++) {
                if (!pathElementMatch(opAddr.getElement(i), entry.address.getElement(i))) {
                    match = false;
                    break;
                }
            }
        }
        return match;
    }

    private boolean pathElementMatch(PathElement element, PathElement operationEntryElement) {
        if (operationEntryElement.getKey().equals("*")) {
        } else if (!operationEntryElement.getKey().equals(element.getKey())) {
            return false;
        }

        if (operationEntryElement.getValue().equals("*")) {
            return true;
        }
        return operationEntryElement.getValue().equals(element.getValue());
    }

    public static class Builder {
        List<OperationEntry> entries = new ArrayList<ModelTestOperationValidatorFilter.OperationEntry>();

        private Builder() {
        }

        public Builder addOperation(PathAddress pathAddress, String name, Action action, OperationFixer operationFixer) {
            entries.add(new OperationEntry(pathAddress, name, action, operationFixer));
            return this;
        }

        public ModelTestOperationValidatorFilter build() {
            return new ModelTestOperationValidatorFilter(entries);
        }
    }

    public static class OperationEntry implements Externalizable {
        private static final long serialVersionUID = 1L;
        private volatile PathAddress address;
        private volatile String name;
        private volatile Action action;
        private volatile OperationFixer operationFixer;

        public OperationEntry(PathAddress address, String name, Action action, OperationFixer operationFixer) {
            if (operationFixer != null && operationFixer instanceof Serializable == false){
                throw new IllegalArgumentException("operationFixer must be serializable");
            }
            this.address = address;
            this.name = name;
            this.action = action;
            this.operationFixer = operationFixer;
        }

        public OperationEntry() {
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(name);
            out.writeObject(address.toModelNode());
            out.writeObject(action);
            out.writeObject(operationFixer);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            name = (String)in.readObject();
            address = PathAddress.pathAddress((ModelNode)in.readObject());
            action = (Action)in.readObject();
            operationFixer = (OperationFixer)in.readObject();
        }
    }

    public static enum Action {
        NOCHECK,
        RESOLVE
    }

    public static void main(String[] args) {
        System.out.println(PathAddress.pathAddress(PathElement.pathElement("*", "*"), PathElement.pathElement("x", "*")));
    }
}
