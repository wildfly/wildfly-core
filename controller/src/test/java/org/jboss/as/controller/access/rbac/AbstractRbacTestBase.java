/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.test.AbstractControllerTestBase;
import org.jboss.dmr.ModelNode;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public abstract class AbstractRbacTestBase extends AbstractControllerTestBase {
    protected ModelNode executeWithRole(ModelNode operation, StandardRole role) {
        return executeWithRoles(operation, role);
    }

    protected ModelNode executeWithRoles(ModelNode operation, StandardRole... roles) {
        for (StandardRole role : roles) {
            operation.get(OPERATION_HEADERS, "roles").add(role.name());
        }
        return getController().execute(operation, null, null, null);
    }

    protected static void assertPermitted(ModelNode operationResult) {
        assertEquals(SUCCESS, operationResult.get(OUTCOME).asString());
    }

    protected static void assertDenied(ModelNode operationResult) {
        assertEquals(FAILED, operationResult.get(OUTCOME).asString());
        assertTrue(operationResult.get(FAILURE_DESCRIPTION).asString().contains("Permission denied"));
    }

    protected static void assertNoAddress(ModelNode operationResult) {
        assertEquals(FAILED, operationResult.get(OUTCOME).asString());
        assertTrue(operationResult.get(FAILURE_DESCRIPTION).asString().contains("not found"));
    }

    protected static enum ResultExpectation { PERMITTED, DENIED, NO_ACCESS }

    protected static void assertOperationResult(ModelNode operationResult, ResultExpectation resultExpectation) {
        switch (resultExpectation) {
            case PERMITTED: assertPermitted(operationResult); break;
            case DENIED:    assertDenied(operationResult); break;
            case NO_ACCESS: assertNoAddress(operationResult); break;
        }
    }

    protected void permitted(String operation, PathAddress pathAddress, StandardRole role) {
        assertPermitted(executeWithRole(Util.createOperation(operation, pathAddress), role));
    }

    protected void denied(String operation, PathAddress pathAddress, StandardRole role) {
        assertDenied(executeWithRole(Util.createOperation(operation, pathAddress), role));
    }

    protected void noAddress(String operation, PathAddress pathAddress, StandardRole role) {
        assertNoAddress(executeWithRole(Util.createOperation(operation, pathAddress), role));
    }
}
