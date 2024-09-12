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
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Executes the {@link java.lang.management.ThreadMXBean} {@code getThreadInfo} methods that return a single thread id.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanThreadInfoHandler implements OperationStepHandler {
    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PlatformMBeanConstants.GET_THREAD_INFO, PlatformMBeanUtil.getResolver(PlatformMBeanConstants.THREADING))
            .setParameters(CommonAttributes.ID, CommonAttributes.MAX_DEPTH)
            .setReplyType(ModelType.OBJECT)
            .setReplyParameters(CommonAttributes.THREAD_INFO_ATTRIBUTES)
            .setReadOnly()
            .setRuntimeOnly()
            .allowReturnNull()
            .build();


    public static final ThreadMXBeanThreadInfoHandler INSTANCE = new ThreadMXBeanThreadInfoHandler();

    private final ParametersValidator validator = new ParametersValidator();

    private ThreadMXBeanThreadInfoHandler() {
        validator.registerValidator(PlatformMBeanConstants.ID, LongRangeValidator.POSITIVE);
        validator.registerValidator(PlatformMBeanConstants.MAX_DEPTH, new IntRangeValidator(1, Integer.MAX_VALUE, true, false));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        validator.validate(operation);
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        try {
            long id = operation.require(PlatformMBeanConstants.ID).asLong();
            ThreadInfo info;
            if (operation.hasDefined(PlatformMBeanConstants.MAX_DEPTH)) {
                info = mbean.getThreadInfo(id, operation.require(PlatformMBeanConstants.MAX_DEPTH).asInt());
            } else {
                info = mbean.getThreadInfo(id);
            }

            final ModelNode result = context.getResult();
            if (info != null) {
                result.set(PlatformMBeanUtil.getDetypedThreadInfo(info, mbean.isThreadCpuTimeSupported()));
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        }
    }

}
