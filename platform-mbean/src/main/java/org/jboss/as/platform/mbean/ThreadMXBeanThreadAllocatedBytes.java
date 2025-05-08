/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link com.sun.management.ThreadMXBean#getThreadCpuTime(long[])} method.
 *
 */
public class ThreadMXBeanThreadAllocatedBytes implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GET_THREAD_ALLOCATED_BYTES, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.ID)
            .setReplyType(ModelType.LONG)
            .setRuntimeOnly()
            .setReadOnly()
            .build();

    public static final ThreadMXBeanThreadAllocatedBytes INSTANCE = new ThreadMXBeanThreadAllocatedBytes();

    private final ParametersValidator idValidator = new ParametersValidator();

    private ThreadMXBeanThreadAllocatedBytes() {
        idValidator.registerValidator(PlatformMBeanConstants.ID, LongRangeValidator.POSITIVE);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        idValidator.validate(operation);
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        if (mbean instanceof com.sun.management.ThreadMXBean) {
            com.sun.management.ThreadMXBean extThread = (com.sun.management.ThreadMXBean) mbean;
            try {
                long id = operation.require(PlatformMBeanConstants.ID).asLong();
                context.getResult().set(extThread.getThreadAllocatedBytes(id));
            } catch (UnsupportedOperationException e) {
                throw new OperationFailedException(e.toString());
            }
        } else {
            throw PlatformMBeanLogger.ROOT_LOGGER.unsupportedOperation(PlatformMBeanConstants.GET_THREAD_CPU_TIMES);
        }
    }
}
