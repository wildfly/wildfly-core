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
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link java.lang.management.ThreadMXBean#findMonitorDeadlockedThreads()} method.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanFindMonitorDeadlockedThreadsHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.FIND_MONITOR_DEADLOCKED_THREADS, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.LONG)
            .setRuntimeOnly()
            .setReadOnly()
            .allowReturnNull()
            .build();

    public static final ThreadMXBeanFindMonitorDeadlockedThreadsHandler INSTANCE = new ThreadMXBeanFindMonitorDeadlockedThreadsHandler();

    private ThreadMXBeanFindMonitorDeadlockedThreadsHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        try {
            long[] ids = ManagementFactory.getThreadMXBean().findMonitorDeadlockedThreads();
            final ModelNode result = context.getResult();
            if (ids != null) {
                result.setEmptyList();
                for (long id : ids) {
                    result.add(id);
                }
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        }
    }


}
