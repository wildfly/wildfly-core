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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Base class for {@link OperationStepHandler} implementations that add managed resources and also perform runtime
 * processing that <strong>should only occur during server boot</strong>. An example of such processing would be installing a
 * deployment unit processor.
 * <p>
 * <strong>Do not extend this class for operations that can run after server boot.</strong> Typically it should only
 * be extended for operations that add a deployment unit processor.
 * </p>
 * <p>
 * If an operation handled via an extension of this class is executed on a server after boot, the server's persistent
 * configuration model will be updated, but the
 * {@link #performBoottime(OperationContext, ModelNode, org.jboss.as.controller.registry.Resource) performBoottime}
 * method will not be invoked. Instead the server will be {@link OperationContext#reloadRequired() put into "reload required" state}.
 * </p>
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractBoottimeAddStepHandler extends AbstractAddStepHandler {

    /**
     * {@inheritDoc}
     */
    protected AbstractBoottimeAddStepHandler() {
    }

    /**
     * {@inheritDoc}
     */
    protected AbstractBoottimeAddStepHandler(Collection<? extends AttributeDefinition> attributes) {
        super(attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    protected AbstractBoottimeAddStepHandler(RuntimeCapability capability, Collection<? extends AttributeDefinition> attributes) {
        super(capability, attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    protected AbstractBoottimeAddStepHandler(Set<RuntimeCapability> capabilities, Collection<? extends AttributeDefinition> attributes) {
        super(capabilities, attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    protected AbstractBoottimeAddStepHandler(RuntimeCapability capability, AttributeDefinition... attributes) {
        super(capability, attributes);
    }

    /**
     * {@inheritDoc}
     */
    protected AbstractBoottimeAddStepHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    protected AbstractBoottimeAddStepHandler(Set<RuntimeCapability> capabilities, AttributeDefinition... attributes) {
        super(capabilities, attributes);
    }

    public AbstractBoottimeAddStepHandler(Parameters parameters) {
        super(parameters);
    }

    /**
     * If {@link OperationContext#isBooting()} returns {@code true}, invokes
     * {@link #performBoottime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)},
     * else invokes {@link OperationContext#reloadRequired()}.
     *
     * {@inheritDoc}
     */
    @Override
    protected final void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        if (context.isBooting()) {
            performBoottime(context, operation, resource);
        } else {
            context.reloadRequired();
        }
    }

    /**
     * Make any runtime changes necessary to effect the changes indicated by the given {@code operation}. Will only be
     * invoked if {@link OperationContext#isBooting()} returns {@code true}. Executes
     * after {@link #populateModel(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}, so the given {@code resource}
     * parameter will reflect any changes made in that method. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}. Subclasses that wish to make
     * changes to runtime services should override this method or the
     * {@link #performBoottime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} variant.
     * <p>
     * This default implementation simply calls the
     * {@link #performBoottime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} variant.
     * <strong>Subclasses that override this method should not call{@code super.performBoottime(...)}.</strong>
     *
     * @param context             the operation context
     * @param operation           the operation being executed
     * @param resource               persistent configuration resource that corresponds to the address of {@code operation}
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        performBoottime(context, operation, resource.getModel());
    }

    /**
     * Make any runtime changes necessary to effect the changes indicated by the given {@code operation}. Will only be
     * invoked if {@link OperationContext#isBooting()} returns {@code true}. Executes
     * after {@link #populateModel(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}, so the given {@code resource}
     * parameter will reflect any changes made in that method. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}. Subclasses that wish to make
     * changes to runtime services should override this method or the
     * {@link #performBoottime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     * variant.
     *
     * @param context             the operation context
     * @param operation           the operation being executed
     * @param model               persistent configuration model from the resource that corresponds to the address of {@code operation}
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
    }

    /**
     * Overrides the superclass to call {@link OperationContext#revertReloadRequired()}
     * if {@link OperationContext#isBooting()} returns {@code false}.
     *
     * {@inheritDoc}
     */
    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        revertReload(context);
    }

    /**
     * <strong>Deprecated</strong>. Overrides the superclass to call {@link OperationContext#revertReloadRequired()}
     * if {@link OperationContext#isBooting()} returns {@code false}.
     *
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    @SuppressWarnings("deprecation")
    protected void rollbackRuntime(OperationContext context, ModelNode operation, ModelNode model, List<ServiceController<?>> controllers) {
        revertReload(context);
    }

    private static void revertReload(OperationContext context) {
        if (!context.isBooting()) {
            context.revertReloadRequired();
        }
    }
}
