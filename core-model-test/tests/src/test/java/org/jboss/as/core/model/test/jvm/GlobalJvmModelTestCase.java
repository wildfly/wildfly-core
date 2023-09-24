/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.jvm;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class GlobalJvmModelTestCase extends AbstractJvmModelTest {

    public GlobalJvmModelTestCase(TestModelType type) {
        super(type, false);
    }

    @Test
    public void testWriteDebugEnabled() throws Exception {
        KernelServices kernelServices = doEmptyJvmAdd();
        ModelNode op = createOperation(WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set("debug-enabled");
        op.get(VALUE).set(true);
        kernelServices.executeForFailure(op);
    }

    @Test
    public void testWriteDebugOptions() throws Exception {
        KernelServices kernelServices = doEmptyJvmAdd();
        ModelNode op = createOperation(WRITE_ATTRIBUTE_OPERATION);
        op.get(NAME).set("debug-options");
        op.get(VALUE).set(true);
        kernelServices.executeForFailure(op);
    }

}
