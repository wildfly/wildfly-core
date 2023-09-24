/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.junit.Assert.fail;

import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.SubnetValidator;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Unit Tests of Interface resource
 *
 * @author wangc
 */
public class InterfaceAttributeDefinitionUnitTestCase {

    private static final String INVALID_STRING = "bad value";
    private static final String VALID_SUBSET = "192.168.1.1/16";
    private static final String INVALID_SUBSET = "192.168.1.1/";

    @Test
    public void testSubset() {
        SubnetValidator testee = new SubnetValidator(true, true);
        assertOk(testee, new ModelNode().set(VALID_SUBSET));
        assertFail(testee, new ModelNode().set(INVALID_STRING));
        assertFail(testee, new ModelNode().set(INVALID_SUBSET));
    }

    private static void assertOk(ModelTypeValidator validator, ModelNode toTest) {
        try {
            validator.validateParameter("test", toTest);
        } catch (OperationFailedException e) {
            fail("Validation should have passed but received " + e.getFailureDescription().toString());
        }
    }

    private static void assertFail(ModelTypeValidator validator, ModelNode toTest) {
        try {
            validator.validateParameter("test", toTest);
            fail("Validation should have failed ");
        } catch (OperationFailedException e) {
            // This is OK, expected exception.
        }
    }
}
