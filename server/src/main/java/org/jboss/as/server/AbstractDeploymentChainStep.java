/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;

/**
 * A step handler for a deployment chain step which adds a processor to the deployment chain.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractDeploymentChainStep implements OperationStepHandler {
    private static final DeploymentProcessorTarget TARGET = DeployerChainAddHandler::addDeploymentProcessor;

    public final void execute(final OperationContext context, final ModelNode operation) {
        if (context.isBooting()) {
            execute(TARGET);
        } else {
            // WFCORE-1781 We should not be called post-boot as the DUP chain can only be modified in boot
            // Check and see if the OperationDefinition for this op declares reload/restart required and if so
            // trigger that; otherwise fail.
            ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
            OperationEntry oe = mrr.getOperationEntry(PathAddress.EMPTY_ADDRESS, operation.get(ModelDescriptionConstants.OP).asString());
            Set<OperationEntry.Flag> flags = oe == null ? Collections.emptySet() : oe.getOperationDefinition().getFlags();
            if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
                context.restartRequired();
                context.completeStep((ctx, op) -> ctx.revertRestartRequired());
            } else if (flags.contains(OperationEntry.Flag.RESTART_ALL_SERVICES)) {
                context.reloadRequired();
                context.completeStep(OperationContext.RollbackHandler.REVERT_RELOAD_REQUIRED_ROLLBACK_HANDLER);
            } else {
                // Coding error we cannot recover from
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Execute the step, adding deployment processors.
     *
     * @param processorTarget the processor target
     */
    protected abstract void execute(DeploymentProcessorTarget processorTarget);
}
