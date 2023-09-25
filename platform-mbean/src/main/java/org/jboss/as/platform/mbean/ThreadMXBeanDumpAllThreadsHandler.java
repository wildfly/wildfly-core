/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link java.lang.management.ThreadMXBean#dumpAllThreads(boolean, boolean)} method.
 * of thread ids.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanDumpAllThreadsHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.DUMP_ALL_THREADS, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.LOCKED_MONITORS_FLAG, CommonAttributes.LOCKED_SYNCHRONIZERS_FLAG)
            .setReplyType(ModelType.LIST)
            .setReplyParameters(CommonAttributes.THREAD_INFO_ATTRIBUTES)
            .setRuntimeOnly()
            .setReadOnly()
            .build();


    public static final ThreadMXBeanDumpAllThreadsHandler INSTANCE = new ThreadMXBeanDumpAllThreadsHandler();

    private final ParametersValidator lockedValidator = new ParametersValidator();

    private ThreadMXBeanDumpAllThreadsHandler() {
        lockedValidator.registerValidator(PlatformMBeanConstants.LOCKED_MONITORS, new ModelTypeValidator(ModelType.BOOLEAN));
        lockedValidator.registerValidator(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS, new ModelTypeValidator(ModelType.BOOLEAN));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        lockedValidator.validate(operation);

        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        try {
            ThreadInfo[] infos = mbean.dumpAllThreads(
                    operation.require(PlatformMBeanConstants.LOCKED_MONITORS).asBoolean(),
                    operation.require(PlatformMBeanConstants.LOCKED_SYNCHRONIZERS).asBoolean());

            final ModelNode result = context.getResult();
            if (infos != null) {
                result.setEmptyList();
                for (ThreadInfo info : infos) {
                    result.add(PlatformMBeanUtil.getDetypedThreadInfo(info, mbean.isThreadCpuTimeSupported()));
                }
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }
    }

}
