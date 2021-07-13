/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.validation;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

public class IntAllowedValuesValidatorTest {
    @Test
    public void testAllInts() {
        IntAllowedValuesValidator validator = new IntAllowedValuesValidator(1, 2, 3);
        try {
            validator.validateParameter("foo", new ModelNode(3));
        } catch (OperationFailedException e) {
            Assert.fail("Validation should have succeeded");
        }
        Assert.assertThrows(OperationFailedException.class,
                () -> validator.validateParameter("foo", new ModelNode(4)));
    }

    @Test
    public void testCoercedType() {
        IntAllowedValuesValidator validator = new IntAllowedValuesValidator(1, 2, 3);
        try {
            validator.validateParameter("foo", new ModelNode("1"));
            validator.validateParameter("foo", new ModelNode("2"));
            validator.validateParameter("foo", new ModelNode("3"));
        } catch (OperationFailedException e) {
            Assert.fail("Validation should have succeeded");
        }
        Assert.assertThrows(OperationFailedException.class,
                () -> validator.validateParameter("foo", new ModelNode("4")));
        Assert.assertThrows(OperationFailedException.class,
                () -> validator.validateParameter("foo", new ModelNode("bar")));
    }
}
