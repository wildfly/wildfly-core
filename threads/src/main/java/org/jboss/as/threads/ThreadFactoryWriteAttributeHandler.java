/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;


/**
 * Handles attribute writes for a thread factory.
 *
 * @author Alexey Loubyansky
 */
public class ThreadFactoryWriteAttributeHandler extends ThreadsWriteAttributeOperationHandler {

    public static final ThreadFactoryWriteAttributeHandler INSTANCE = new ThreadFactoryWriteAttributeHandler();

    private ThreadFactoryWriteAttributeHandler() {
        super(ThreadFactoryAdd.ATTRIBUTES, ThreadFactoryAdd.RW_ATTRIBUTES);
    }

    @Override
    protected ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String name = Util.getNameFromAddress(model.require(OP_ADDR));
        final ServiceName serviceName = ThreadsServices.threadFactoryName(name);
        ServiceController<?> controller = context.getServiceRegistry(true).getService(serviceName);
        if(controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.threadFactoryServiceNotFound(serviceName);
        }
        return controller;
    }

    @Override
    protected void applyOperation(final OperationContext context, ModelNode operation, String attributeName,
                                  ServiceController<?> service, boolean forRollback) throws OperationFailedException {

        final ThreadFactoryService tf = (ThreadFactoryService) service.getService();
        if (CommonAttributes.GROUP_NAME.equals(attributeName)) {
            final ModelNode value = PoolAttributeDefinitions.GROUP_NAME.resolveModelAttribute(context, operation);
            tf.setThreadGroupName(value.isDefined() ? value.asString() : null);
        } else if(CommonAttributes.PRIORITY.equals(attributeName)) {
            final ModelNode value = PoolAttributeDefinitions.PRIORITY.resolveModelAttribute(context, operation);
            tf.setPriority(value.isDefined() ? value.asInt() : null);
        } else if(CommonAttributes.THREAD_NAME_PATTERN.equals(attributeName)) {
            final ModelNode value = PoolAttributeDefinitions.THREAD_NAME_PATTERN.resolveModelAttribute(context, operation);
            tf.setNamePattern(value.isDefined() ? value.asString() : null);
        } else if (!forRollback) {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedThreadFactoryAttribute(attributeName);
        }
    }
}
