/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

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
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link com.sun.management.ThreadMXBean#getThreadUserTime(long[])} method.
 *
 */
public class ThreadMXBeanUserTimesHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GET_THREAD_USER_TIMES, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.IDS)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.LONG)
            .setRuntimeOnly()
            .setReadOnly()
            .setStability(Stability.COMMUNITY)
            .build();

    public static final ThreadMXBeanUserTimesHandler INSTANCE = new ThreadMXBeanUserTimesHandler();

    private final ParametersValidator idsValidator = new ParametersValidator();

    private ThreadMXBeanUserTimesHandler() {
        idsValidator.registerValidator(PlatformMBeanConstants.IDS, new ListValidator(LongRangeValidator.POSITIVE));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        idsValidator.validate(operation);
        ExtendedThreadMBean mbean = new ExtendedThreadMBean();
        if (mbean.isOperationDefined(ExtendedThreadMBean.GET_THREAD_USER_TIME, new String[]{long[].class.getName()})) {
            try {
                long[] ids = getIds(operation);
                long[] times = mbean.getThreadUserTime(ids);
                context.getResult().setEmptyList();
                for (Long time : times) {
                    context.getResult().add(time);
                }
            } catch (UnsupportedOperationException e) {
                throw new OperationFailedException(e.toString());
            }
        } else {
            throw PlatformMBeanLogger.ROOT_LOGGER.unsupportedOperation(PlatformMBeanConstants.GET_THREAD_USER_TIMES);
        }
    }

    private long[] getIds(final ModelNode operation) throws OperationFailedException {
        final List<ModelNode> idNodes = operation.require(PlatformMBeanConstants.IDS).asList();
        final long[] ids = new long[idNodes.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idNodes.get(i).asLong();
        }
        return ids;
    }
}
