/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
