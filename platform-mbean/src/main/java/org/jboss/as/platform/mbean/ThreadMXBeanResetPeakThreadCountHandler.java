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
 * Executes the {@link java.lang.management.ThreadMXBean#resetPeakThreadCount()} method.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanResetPeakThreadCountHandler implements OperationStepHandler {
    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.RESET_PEAK_THREAD_COUNT, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setRuntimeOnly()
            .build();


    public static final ThreadMXBeanResetPeakThreadCountHandler INSTANCE = new ThreadMXBeanResetPeakThreadCountHandler();

    private ThreadMXBeanResetPeakThreadCountHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        try {
            ManagementFactory.getThreadMXBean().resetPeakThreadCount();
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }


}
