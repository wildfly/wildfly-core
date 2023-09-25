/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Operation handler to run a single scan by a DeploymentScanner.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class FileSystemDeploymentScanHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "run-scan";
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(
            OPERATION_NAME, DeploymentScannerExtension.getResourceDescriptionResolver("deployment.scanner")).build();

    public static final FileSystemDeploymentScanHandler INSTANCE = new FileSystemDeploymentScanHandler();

    private FileSystemDeploymentScanHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final FileSystemDeploymentService scanner = getExistingScanner(context, operation);
                if (scanner != null) {
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.KEEP) {
                                scanner.singleScan();
                            }
                        }
                    });
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }

    private FileSystemDeploymentService getExistingScanner(OperationContext context, ModelNode operation) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String name = address.getLastElement().getValue();
        ServiceController<?> serviceController = context.getServiceRegistry(true).getService(DeploymentScannerService.getServiceName(name));
        if(serviceController != null && serviceController.getState() == ServiceController.State.UP) {
            DeploymentScannerService service =  (DeploymentScannerService) serviceController.getService();
            return (FileSystemDeploymentService) service.getValue();
        }
        return null;
    }
}
