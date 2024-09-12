/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ListValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link java.lang.management.ThreadMXBean} {@code getThreadInfo} methods that return an array
 * of thread ids.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanThreadInfosHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GET_THREAD_INFOS, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.IDS, CommonAttributes.MAX_DEPTH, CommonAttributes.LOCKED_MONITORS_FLAG, CommonAttributes.LOCKED_SYNCHRONIZERS_FLAG)
            .setReplyType(ModelType.LIST)
            .setReplyParameters(CommonAttributes.THREAD_INFO_ATTRIBUTES)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    public static final ThreadMXBeanThreadInfosHandler INSTANCE = new ThreadMXBeanThreadInfosHandler();

    private final ParametersValidator idsValidator = new ParametersValidator();
    private final ParametersValidator depthValidator = new ParametersValidator();
    private final ParametersValidator lockedValidator = new ParametersValidator();

    private ThreadMXBeanThreadInfosHandler() {
        idsValidator.registerValidator(PlatformMBeanConstants.IDS, new ListValidator(LongRangeValidator.POSITIVE));
        depthValidator.registerValidator(PlatformMBeanConstants.MAX_DEPTH, new IntRangeValidator(1, Integer.MAX_VALUE, false, false));
        lockedValidator.registerValidator(PlatformMBeanConstants.LOCKED_MONITORS, new ModelTypeValidator(ModelType.BOOLEAN));
        lockedValidator.registerValidator(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS, new ModelTypeValidator(ModelType.BOOLEAN));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        try {
            final long[] ids = getIds(operation);
            ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] infos;
            if (operation.hasDefined(PlatformMBeanConstants.LOCKED_MONITORS)) {
                lockedValidator.validate(operation);
                infos = mbean.getThreadInfo(ids,
                        operation.require(PlatformMBeanConstants.LOCKED_MONITORS).asBoolean(),
                        operation.require(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS).asBoolean());
            } else if (operation.hasDefined(PlatformMBeanConstants.MAX_DEPTH)) {
                depthValidator.validate(operation);
                infos = mbean.getThreadInfo(ids, operation.require(PlatformMBeanConstants.MAX_DEPTH).asInt());
            } else {
                infos = mbean.getThreadInfo(ids);
            }

            final ModelNode result = context.getResult();
            if (infos != null) {
                result.setEmptyList();
                for (ThreadInfo info : infos) {
                    if (info != null) {
                        result.add(PlatformMBeanUtil.getDetypedThreadInfo(info, mbean.isThreadCpuTimeSupported()));
                    } else {
                        // Add an undefined placeholder
                        result.add();
                    }
                }
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
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
