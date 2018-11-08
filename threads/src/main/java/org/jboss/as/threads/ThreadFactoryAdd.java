/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;


/**
 * Adds a thread factory to the threads subsystem.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="alex@jboss.org">Alexey Loubyansky</a>
 */
public class ThreadFactoryAdd extends AbstractAddStepHandler {

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
        PoolAttributeDefinitions.GROUP_NAME, PoolAttributeDefinitions.THREAD_NAME_PATTERN, PoolAttributeDefinitions.PRIORITY};

    static final AttributeDefinition[] RW_ATTRIBUTES = new AttributeDefinition[] {
        PoolAttributeDefinitions.GROUP_NAME, PoolAttributeDefinitions.THREAD_NAME_PATTERN, PoolAttributeDefinitions.PRIORITY};

    private final RuntimeCapability cap;

    /**
     * @param cap : nullable -  Setting it to null will only use old service name.
     */
    ThreadFactoryAdd(RuntimeCapability cap) {
        super(ATTRIBUTES);
        this.cap = cap;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        ModelNode priorityModelNode = PoolAttributeDefinitions.PRIORITY.resolveModelAttribute(context, model);
        ModelNode groupNameModelNode = PoolAttributeDefinitions.GROUP_NAME.resolveModelAttribute(context, model);
        ModelNode threadNamePatternModelNode = PoolAttributeDefinitions.THREAD_NAME_PATTERN.resolveModelAttribute(context, model);

        final String threadNamePattern = threadNamePatternModelNode.isDefined() ? threadNamePatternModelNode.asString() : null;
        final Integer priority = priorityModelNode.isDefined() ? priorityModelNode.asInt() : null;
        final String groupName = groupNameModelNode.isDefined() ? groupNameModelNode.asString() : null;

        final String name = context.getCurrentAddressValue();

        final ServiceTarget target = context.getServiceTarget();
        final ThreadFactoryService service = new ThreadFactoryService();
        service.setNamePattern(threadNamePattern);
        service.setPriority(priority);
        service.setThreadGroupName(groupName);
        if (cap != null) {
            target.addService(cap.getCapabilityServiceName(context.getCurrentAddress()), service)
                    .addAliases(ThreadsServices.threadFactoryName(name))
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
        } else {
            target.addService(ThreadsServices.threadFactoryName(name), service)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
        }
    }
}
