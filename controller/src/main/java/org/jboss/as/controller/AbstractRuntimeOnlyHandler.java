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

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.dmr.ModelNode;

/**
 * Base class for operations that do nothing in {@link org.jboss.as.controller.OperationContext.Stage#MODEL} except
 * register a {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} step.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractRuntimeOnlyHandler implements OperationStepHandler {

    protected AbstractRuntimeOnlyHandler() {}

    /**
     * Simply adds a {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} step that calls
     * {@link #executeRuntimeStep(OperationContext, ModelNode)}.
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (requiresRuntime(context) && !isProfile(context)) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // make sure the resource exists before executing a runtime operation.
                    // if that's not the case, the operation will report an error with a comprehensive failure
                    // description instead of a subsequent exception (such as a NPE).
                    if (resourceMustExist(context, operation)) {
                        context.readResource(PathAddress.EMPTY_ADDRESS, false);
                    }
                    executeRuntimeStep(context, operation);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    /**
     * Gets whether the {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} step should be added to call
     * {@link #executeRuntimeStep(OperationContext, ModelNode)}.
     * This default implementation will return {@code true} for a normal server running in normal (non admin-only) mode.
     * If running on a host controller, it will return {@code true} if it is the active copy of the host controller subsystem.
     * Subclasses that perform no runtime update could override and return {@code false}. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     *
     * @param context operation context
     * @return {@code true} if a step to invoke{@code executeRuntimeStep} should be added; {@code false} otherwise.
     *
     * @see OperationContext#isDefaultRequiresRuntime()
     */
    protected boolean requiresRuntime(OperationContext context) {
        // TODO this check of 'subsystem' on a server is solely a workaround until a couple subclasses in full
        // can be modified to override the default behavior
        if (context.getProcessType().isServer() && context.getCurrentAddress().size() > 0 && SUBSYSTEM.equals(context.getCurrentAddress().getElement(0).getKey())) {
            return true;
        }
        return context.isDefaultRequiresRuntime();
    }

    /**
     * By default the handler will check whether the resource exist before calling @{link #executeRuntimeStep(OperationContext, ModelNode)}.
     *
     * This method can be overridden in the special case where a runtime operation must be executed without a corresponding resource.
     *
     * @return true if the resource must exist for this runtime handler to be executed.
     *
     * @param context the operation context
     * @param operation the operation being executed
     */
    protected boolean resourceMustExist(OperationContext context, ModelNode operation) {
        return true;
    }

    /**
     * Execute this step in {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}.
     * If the operation fails, {@link OperationContext#getFailureDescription() context.getFailureDescroption()}
     * must be called, or {@link OperationFailedException} must be thrown, before calling one of the
     * {@link org.jboss.as.controller.OperationContext#completeStep(OperationContext.ResultHandler) context.completeStep variants}.
     * If the operation succeeded, {@link OperationContext#getResult() context.getResult()} should
     * be called and the result populated with the outcome, after which one of the
     * {@link org.jboss.as.controller.OperationContext#completeStep(OperationContext.ResultHandler) context.completeStep variants}
     * must be called.
     *
     * @param context the operation context
     * @param operation the operation being executed
     * @throws OperationFailedException if the operation failed <b>before</b> calling {@code context.completeStep()}
     */
    protected abstract void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException;

    /**
     * Guard against poorly written subsystems that try and execute in /profile=*.
     * Can be removed if WFCORE-2815 relaxed.
     */
    private boolean isProfile(OperationContext context) {
        PathAddress pa = context.getCurrentAddress();
        return pa.size() > 1 && PROFILE.equals(pa.getElement(0).getKey());
    }
}
