/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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

    private final RuntimeCapability<Void> cap;

    /**
     * @param cap : nullable -  Setting it to null will only use old service name.
     */
    ThreadFactoryAdd(RuntimeCapability<Void> cap) {
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

        final ServiceTarget target = context.getCapabilityServiceTarget();
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
