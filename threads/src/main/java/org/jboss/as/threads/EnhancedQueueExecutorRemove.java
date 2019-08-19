/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.threads;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.threads.ThreadPoolManagementUtils.EnhancedQueueThreadPoolParameters;
import org.jboss.dmr.ModelNode;

/**
 * Removes an {@code org.jboss.threads.EnhancedQueueExecutor}.
 */
class EnhancedQueueExecutorRemove extends AbstractRemoveStepHandler {

    private final EnhancedQueueExecutorAdd addHandler;

    EnhancedQueueExecutorRemove(EnhancedQueueExecutorAdd addHandler) {
        this.addHandler = addHandler;
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final EnhancedQueueThreadPoolParameters params =
                ThreadPoolManagementUtils.parseEnhancedQueueThreadPoolParameters(context, operation, model);
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(), addHandler.getCapability(), addHandler.getServiceNameBase(),
                params.getThreadFactory(), addHandler.getThreadFactoryResolver(),
                context);
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        addHandler.performRuntime(context, operation, model);
    }
}
