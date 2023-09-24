/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.model.jvm;

import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * {@code OperationHandler} for the jvm resource add operation.
 *
 * @author Emanuel Muckenhuber
 */
final class JVMAddHandler extends AbstractAddStepHandler {
    public static final String OPERATION_NAME = ADD;

    private final AttributeDefinition[] attrs;
    JVMAddHandler(AttributeDefinition[] attrs) {
        this.attrs = attrs;
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {

        super.populateModel(context, operation, resource);
        final ModelNode model = resource.getModel();
        final PathAddress jvm = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));

        context.addStep(new OperationStepHandler() {
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                Resource root;
                // TODO WFCORE-1527 - this is a workaround for the issue that even though at this point the parent server group
                // may have been added, it is not visible to RRFR, which it should be.
                try {
                    root = context.readResourceFromRoot(context.getCurrentAddress().getParent(), false);
                } catch (Resource.NoSuchResourceException e) {
                    // this occurs in the case of an ignored server-group being added to a slave.
                    // for all other cases, the parent element is always present.
                    return;
                }
                final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
                final int maxOccurs = registration.getMaxOccurs();
                // if we have a configured max (for example /host=master/server-config/server-one/jvm= is cardinality 1, but
                // /host=master/jvm= is unbounded), verify that limit here.
                if (maxOccurs != Integer.MAX_VALUE) {
                    Set<Resource.ResourceEntry> children = root.getChildren(ModelDescriptionConstants.JVM);
                    if (children.size() > 1) {
                        for (Resource.ResourceEntry entry : children) {
                            if (!entry.getName().equals(jvm.getLastElement().getValue())) {
                                throw ControllerLogger.ROOT_LOGGER.cannotAddMoreThanOneJvmForServerOrHost(
                                        jvm,
                                        PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.JVM, entry.getName())));
                            }
                        }
                    }
                }
            }
        }, OperationContext.Stage.MODEL);

        for (AttributeDefinition attr : attrs) {
            attr.validateAndSet(operation, model);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
