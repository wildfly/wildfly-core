/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test.otherservices.subsystem;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SubsystemAddWithOtherService extends AbstractBoottimeAddStepHandler {

    public static final SubsystemAddWithOtherService INSTANCE = new SubsystemAddWithOtherService();

    private SubsystemAddWithOtherService() {
    }

    /** {@inheritDoc} */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        model.setEmptyObject();
    }

    /** {@inheritDoc} */
    @Override
    public void performBoottime(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {

        MyService mine = new MyService();
        context.getServiceTarget().addService(MyService.NAME, mine)
            .addDependency(OtherService.NAME, OtherService.class, mine.otherValue)
            .install();

    }
}
