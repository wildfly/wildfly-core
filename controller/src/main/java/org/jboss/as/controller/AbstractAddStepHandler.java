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

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

import java.util.Collection;
import java.util.Set;

/**
 * Base class for {@link OperationStepHandler} implementations that add managed resource.
 *
 * @author John Bailey
 * @author Brian Stansbarry
 * @author Tomaz Cerar
 */
public class AbstractAddStepHandler extends AbstractBaseStepHandler implements OperationStepHandler {


    /**
     * Constructs an add handler.
     */
    public AbstractAddStepHandler() { //default constructor to preserve backward compatibility
        super();
    }

    /**
     * Constructs an add handler
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Collection<? extends AttributeDefinition> attributes) {
        this(NULL_CAPABILITIES, attributes );
    }

    /**
     * Constructs an add handler
     * @param capability capability to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(RuntimeCapability capability, Collection<? extends AttributeDefinition> attributes) {
        super(capability, attributes);
    }

    /**
     * Constructs an add handler.
     *
     * @param capabilities capabilities to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes   attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Set<RuntimeCapability> capabilities, Collection<? extends AttributeDefinition> attributes) {
        super(capabilities, attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param capability capability to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(RuntimeCapability capability, AttributeDefinition... attributes) {
        super(capability, attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param capabilities capabilities to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Set<RuntimeCapability> capabilities, AttributeDefinition... attributes) {
        super(capabilities, attributes);
    }

    public AbstractAddStepHandler(Parameters parameters) {
        super(parameters);
    }

    /** {@inheritDoc */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = createResource(context, operation);
        populateModel(context, operation, resource);
        recordCapabilitiesAndRequirements(context, operation, resource);
        //verify model for alternatives & requires
        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    performRuntime(context, operation, resource);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            rollbackRuntime(context, operation, resource);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }



    public static class Parameters extends AbstractBaseStepHandler.Parameters {
        public Parameters() {
        }
    }
}
