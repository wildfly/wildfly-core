/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class LocalDomainControllerAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "write-local-domain-controller";
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.DOMAIN_CONTROLLER)
            .setDeprecated(ModelVersion.create(5, 0, 0))
            .build();


    public static LocalDomainControllerAddHandler getInstance(DomainControllerWriteAttributeHandler writeAttributeHandler) {
        return new RealLocalDomainControllerAddHandler(writeAttributeHandler);
    }

    public static LocalDomainControllerAddHandler getTestInstance() {
        return new TestLocalDomainControllerAddHandler();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        ModelNode dc = model.get(DOMAIN_CONTROLLER);
        dc.get(LOCAL).setEmptyObject();

        if (dc.has(REMOTE)) {
            dc.remove(REMOTE);
        }

        // check if this is /host=foo:add() being performed after HC boot.
        final boolean hostAdd = context.getAttachment(HostAddHandler.HOST_ADD_AFTER_BOOT) == null ? false :
                context.getAttachment(HostAddHandler.HOST_ADD_AFTER_BOOT).booleanValue();

        if (context.isBooting() || hostAdd) {
            initializeDomain(hostAdd ? context.getCurrentAddress().getLastElement().getValue() : null);
        } else {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (!context.isBooting()) {
                    context.revertReloadRequired();
                }
            }
        });
    }

    abstract void initializeDomain(final String hostName);

    private static class RealLocalDomainControllerAddHandler extends LocalDomainControllerAddHandler {
        private final DomainControllerWriteAttributeHandler writeAttributeHandler;

        RealLocalDomainControllerAddHandler(DomainControllerWriteAttributeHandler writeAttributeHandler) {
            this.writeAttributeHandler = writeAttributeHandler;
        }

        void initializeDomain(final String hostName) {
            writeAttributeHandler.initializeLocalDomain(hostName);
        }
    }

    private static class TestLocalDomainControllerAddHandler extends LocalDomainControllerAddHandler {

        @Override
        void initializeDomain(final String hostName) {
        }
    }
}
