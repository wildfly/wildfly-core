/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operation.validation;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModuleNameValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for ModuleNameValidator
 */
public class ModuleNameValidatorTestCase {

    @Test
    public void test() throws OperationFailedException {
        ParameterValidator validator = ModuleNameValidator.INSTANCE;

        validator.validateParameter("valid", new ModelNode("org.jboss.modules"));
        validator.validateParameter("valid", new ModelNode("org.jboss.modules:main"));
        validator.validateParameter("valid", new ModelNode("org.jboss.modules:1.9"));
        validator.validateParameter("escaped", new ModelNode("org.jboss.modules.foo\\:bar:main"));
        validator.validateParameter("dash", new ModelNode("org.infinispan.hibernate-cache"));

        Assert.assertThrows(OperationFailedException.class, () -> validator.validateParameter("invalid", new ModelNode(".foo.bar")));
        Assert.assertThrows(OperationFailedException.class, () -> validator.validateParameter("invalid", new ModelNode("foo..bar")));
        Assert.assertThrows(OperationFailedException.class, () -> validator.validateParameter("invalid", new ModelNode("foo.bar.")));
        Assert.assertThrows(OperationFailedException.class, () -> validator.validateParameter("invalid", new ModelNode("foo.bar:")));
        Assert.assertThrows(OperationFailedException.class, () -> validator.validateParameter("invalid", new ModelNode("foo:bar:main")));
    }
}
