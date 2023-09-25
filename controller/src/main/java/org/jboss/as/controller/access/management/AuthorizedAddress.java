/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.management;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class AuthorizedAddress {

    private static final String HIDDEN = "<hidden>";
    private static final Set<Action.ActionEffect> ADDRESS_EFFECT = EnumSet.of(Action.ActionEffect.ADDRESS);

    private final ModelNode address;
    private final boolean elided;

    AuthorizedAddress(ModelNode address, boolean elided) {
        this.address = address;
        this.elided = elided;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.address);
        hash = 37 * hash + (this.elided ? 1 : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "AuthorizedAddress{" + "address=" + address + ", elided=" + elided + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AuthorizedAddress other = (AuthorizedAddress) obj;
        if (!Objects.equals(this.address, other.address)) {
            return false;
        }
        if (this.elided != other.elided) {
            return false;
        }
        return true;
    }

    public ModelNode getAddress() {
        return address;
    }

    public boolean isElided() {
        return elided;
    }

    public static AuthorizedAddress authorizeAddress(OperationContext context, ModelNode operation) {
        ModelNode address = operation.get(ModelDescriptionConstants.OP_ADDR);
        ModelNode testOp = new ModelNode();
        testOp.get(OP).set(READ_RESOURCE_OPERATION);
        testOp.get(OP_ADDR).set(address);

        AuthorizationResult authResult = context.authorize(testOp, ADDRESS_EFFECT);
        if (authResult.getDecision() == AuthorizationResult.Decision.PERMIT) {
            return new AuthorizedAddress(address, false);
        }

        // Failed. Now we need to see how far we can go
        ModelNode partialAddress = new ModelNode().setEmptyList();
        ModelNode elidedAddress = new ModelNode().setEmptyList();
        for (Property prop : address.asPropertyList()) {
            partialAddress.add(prop);
            testOp.get(OP_ADDR).set(partialAddress);
            authResult = context.authorize(testOp, ADDRESS_EFFECT);
            if (authResult.getDecision() == AuthorizationResult.Decision.DENY) {
                elidedAddress.add(prop.getName(), HIDDEN);
                return new AuthorizedAddress(elidedAddress, true);
            } else {
                elidedAddress.add(prop);
            }
        }

        // Should not be reachable, but in case of a bug, be conservative and hide data
        ModelNode strange = new ModelNode();
        strange.add(HIDDEN, HIDDEN);
        return new AuthorizedAddress(strange, true);
    }
}
