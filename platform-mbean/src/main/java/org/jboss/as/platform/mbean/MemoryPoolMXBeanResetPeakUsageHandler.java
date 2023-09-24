/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Executes the {@link java.lang.management.MemoryMXBean#gc()} method.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MemoryPoolMXBeanResetPeakUsageHandler implements OperationStepHandler{

    static final MemoryPoolMXBeanResetPeakUsageHandler INSTANCE = new MemoryPoolMXBeanResetPeakUsageHandler();
    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("reset-peak-usage", PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY_POOL))
            .setRuntimeOnly()
            .build();

    private MemoryPoolMXBeanResetPeakUsageHandler() {

    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        try {
            getMemoryPoolMXBean(operation).resetPeakUsage();
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    private MemoryPoolMXBean getMemoryPoolMXBean(ModelNode operation) throws OperationFailedException {
        final String memPoolName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        MemoryPoolMXBean memoryPoolMXBean = null;

        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (memPoolName.equals(escapeMBeanName(mbean.getName()))) {
                memoryPoolMXBean = mbean;
            }
        }

        if (memoryPoolMXBean == null) {
            throw PlatformMBeanLogger.ROOT_LOGGER.unknownMemoryPool(memPoolName);
        }
        return memoryPoolMXBean;
    }
}
