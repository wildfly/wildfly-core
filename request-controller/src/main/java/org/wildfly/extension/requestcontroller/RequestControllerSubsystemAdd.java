/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import static org.wildfly.extension.requestcontroller.RequestControllerRootDefinition.REQUEST_CONTROLLER_CAPABILITY;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;

import java.util.Collection;
import java.util.function.Supplier;


/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author Stuart Douglas
 */
class RequestControllerSubsystemAdd extends AbstractBoottimeAddStepHandler {

    RequestControllerSubsystemAdd(Collection<AttributeDefinition> attributeDefinitions) {
        super(attributeDefinitions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(OperationContext context, ModelNode operation, final Resource resource)
            throws OperationFailedException {

        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            protected void execute(DeploymentProcessorTarget processorTarget) {

                processorTarget.addDeploymentProcessor(RequestControllerExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, Phase.STRUCTURE_GLOBAL_REQUEST_CONTROLLER, new RequestControllerDeploymentUnitProcessor());
            }
        }, OperationContext.Stage.RUNTIME);

        int maxRequests = RequestControllerRootDefinition.MAX_REQUESTS.resolveModelAttribute(context, resource.getModel()).asInt();
        boolean trackIndividual = RequestControllerRootDefinition.TRACK_INDIVIDUAL_ENDPOINTS.resolveModelAttribute(context, resource.getModel()).asBoolean();



        CapabilityServiceBuilder<?> svcBuilder = context.getCapabilityServiceTarget().addCapability(REQUEST_CONTROLLER_CAPABILITY);
        Supplier<SuspendController> supplier = svcBuilder.requiresCapability("org.wildfly.server.suspend-controller", SuspendController.class);
        RequestController requestController = new RequestController(trackIndividual, supplier);
        requestController.setMaxRequestCount(maxRequests);
        svcBuilder.setInstance(requestController)
                .install();

    }
}
