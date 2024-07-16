/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;

import java.io.File;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.resources.ServerConfigResource;
import org.jboss.dmr.ModelNode;

/**
 * {@code OperationHandler} adding a new server configuration.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerAddHandler extends AbstractAddStepHandler {

    public static final String OPERATION_NAME = ADD;

    private final LocalHostControllerInfo hostControllerInfo;
    private final ServerInventory serverInventory;
    private final ControlledProcessState processState;
    private final File domainDataDir;

    /**
     * Create the ServerAddHandler
     */
    private ServerAddHandler(LocalHostControllerInfo hostControllerInfo, ServerInventory serverInventory, ControlledProcessState processState, File domainDataDir) {
        this.hostControllerInfo = hostControllerInfo;
        this.serverInventory = serverInventory;
        this.processState = processState;
        this.domainDataDir = domainDataDir;
    }

    public static ServerAddHandler create(LocalHostControllerInfo hostControllerInfo, ServerInventory serverInventory, ControlledProcessState processState, final File domainDataDir) {
        return new ServerAddHandler(hostControllerInfo, serverInventory, processState, domainDataDir);
    }

    @Override
    protected Resource createResource(final OperationContext context) {
        final Resource serverConfigResource = new ServerConfigResource(serverInventory, processState,
                context.getCurrentAddress().getLastElement().getValue(), domainDataDir, Resource.Factory.create());
        context.addResource(EMPTY_ADDRESS, serverConfigResource);
        return serverConfigResource;
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {

        super.populateModel(context, operation, resource);

        final PathAddress address = context.getCurrentAddress();
        final PathAddress running = address.subAddress(0, 1).append(PathElement.pathElement(RUNNING_SERVER, address.getLastElement().getValue()));

        //Add the running server
        final ModelNode runningServerAdd = new ModelNode();
        runningServerAdd.get(OP).set(ADD);
        runningServerAdd.get(OP_ADDR).set(running.toModelNode());

        context.addStep(runningServerAdd, new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                context.addResource(PathAddress.EMPTY_ADDRESS, PlaceholderResource.INSTANCE);
            }
        }, OperationContext.Stage.MODEL, true);
    }

    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

}
