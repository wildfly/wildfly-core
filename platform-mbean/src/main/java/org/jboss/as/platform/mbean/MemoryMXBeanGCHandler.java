/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * Executes the {@link java.lang.management.MemoryMXBean#gc()} method.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MemoryMXBeanGCHandler implements OperationStepHandler {

    public static final MemoryMXBeanGCHandler INSTANCE = new MemoryMXBeanGCHandler();
    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GC, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY))
            .setRuntimeOnly()
            .build();


    private MemoryMXBeanGCHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // Modifies state, so communicate that
                context.getServiceRegistry(true);

                ManagementFactory.getMemoryMXBean().gc();
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
