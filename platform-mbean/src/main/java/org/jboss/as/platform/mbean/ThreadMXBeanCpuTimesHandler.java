/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ListValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link com.sun.management.ThreadMXBean#getThreadCpuTime(long[])} method.
 *
 */
public class ThreadMXBeanCpuTimesHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GET_THREAD_CPU_TIMES, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.IDS)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.LONG)
            .setRuntimeOnly()
            .setReadOnly()
            .build();

    public static final ThreadMXBeanCpuTimesHandler INSTANCE = new ThreadMXBeanCpuTimesHandler();

    private final ParametersValidator idsValidator = new ParametersValidator();

    private ThreadMXBeanCpuTimesHandler() {
        idsValidator.registerValidator(PlatformMBeanConstants.IDS, new ListValidator(LongRangeValidator.POSITIVE));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        idsValidator.validate(operation);
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        if (mbean instanceof com.sun.management.ThreadMXBean) {
            com.sun.management.ThreadMXBean extThread = (com.sun.management.ThreadMXBean) mbean;
            try {
                long[] ids = getIds(operation);
                long[] times = extThread.getThreadCpuTime(ids);
                context.getResult().setEmptyList();
                for (Long time : times) {
                    context.getResult().add(time);
                }
            } catch (UnsupportedOperationException e) {
                throw new OperationFailedException(e.toString());
            }
        } else {
            throw PlatformMBeanLogger.ROOT_LOGGER.unsupportedOperation(PlatformMBeanConstants.GET_THREAD_CPU_TIMES);
        }
    }

    private long[] getIds(final ModelNode operation) throws OperationFailedException {
        //todo use PlatformMBeanDescriptions.IDS.unwrap()
        idsValidator.validate(operation);
        final List<ModelNode> idNodes = operation.require(PlatformMBeanConstants.IDS).asList();
        final long[] ids = new long[idNodes.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idNodes.get(i).asLong();
        }
        return ids;
    }
}
