/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SystemPropertyReferencesTestCase extends AbstractCoreModelTest {

    @Before
    public void clearProperties() {
        System.clearProperty("test.one");
        System.clearProperty("test.two");
        System.clearProperty("test.referencing");


    }

    @Test
    public void testSystemPropertyReferences() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.STANDALONE).build();
        Assert.assertNull(System.getProperty("test.one"));
        Assert.assertNull(System.getProperty("test.two"));
        Assert.assertNull(System.getProperty("test.referencing"));

        ModelNode op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add("system-property", "test.one");
        op.get(VALUE).set("ONE");
        kernelServices.executeForResult(op);

        op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add("system-property", "test.two");
        op.get(VALUE).set("TWO");
        kernelServices.executeForResult(op);

        op = new ModelNode();
        op.get(OP).set(ADD);
        op.get(OP_ADDR).add("system-property", "test.referencing");
        op.get(VALUE).set(new ValueExpression("${test.one} ${test.two}"));
        kernelServices.executeForResult(op);

        Assert.assertEquals("ONE", System.getProperty("test.one"));
        Assert.assertEquals("TWO", System.getProperty("test.two"));
        Assert.assertEquals("ONE TWO", System.getProperty("test.referencing"));

        op = new ModelNode();
        op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
        op.get(OP_ADDR).add("system-property", "test.referencing");
        op.get(NAME).set(VALUE);
        op.get(VALUE).set(new ValueExpression("${test.one}---${test.two}"));
        kernelServices.executeForResult(op);

        Assert.assertEquals("ONE---TWO", System.getProperty("test.referencing"));
    }
}