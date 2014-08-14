/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller;

import org.jboss.as.controller.access.management.AuthorizedAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_ERROR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_ERRORS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MISSING_DEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class BootErrorCollector {

    private final ModelNode errors = new ModelNode();
    private final OperationStepHandler listBootErrorsHandler = new ListBootErrorsHandler(this);

    void addFailureDescription(final ModelNode operation, final ModelNode failureDescription) {
        ModelNode error = new ModelNode();
        ModelNode failure = new ModelNode();
        failure.get(FAILED).set(operation.clone());
        if (failureDescription != null) {
            if (failureDescription.hasDefined(ControllerLogger.ROOT_LOGGER.failedServices())) {
                failure.get(FAILURES).add(failureDescription.get(ControllerLogger.ROOT_LOGGER.failedServices()));
            }
            if (failureDescription.hasDefined(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies())) {
                failure.get(MISSING_DEPS).add(failureDescription.get(ControllerLogger.ROOT_LOGGER.servicesMissingDependencies()));
            }
            if (failureDescription.getType() == ModelType.STRING) {
                failure.get(FAILURES).add(failureDescription.asString());
            }
        }
        error.get(BOOT_ERROR).set(failure);
        errors.get(BOOT_ERRORS).add(error);
    }

    private ModelNode getErrors() {
        return errors.clone();
    }

    public OperationStepHandler getReadBootErrorsHandler() {
        return this.listBootErrorsHandler;
    }

    public static class ListBootErrorsHandler implements OperationStepHandler {

        private static final String OPERATION_NAME = "read-boot-errors";
        public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
                ControllerResolver.getResolver("errors")).setReplyType(ModelType.LIST).setReplyValueType(ModelType.OBJECT).setRuntimeOnly().build();
        private final BootErrorCollector errors;

        ListBootErrorsHandler(final BootErrorCollector errors) {
            this.errors = errors;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                    ModelNode bootErrors = new ModelNode();
                    for (ModelNode bootError : errors.getErrors().get(BOOT_ERRORS).asList()) {
                        secureOperationAddress(context, bootError);
                        bootErrors.get(BOOT_ERRORS).add(bootError);
                    }
                    context.getResult().set(errors.getErrors());
                    context.stepCompleted();
                }
            }, OperationContext.Stage.RUNTIME);
            context.stepCompleted();
        }

        private void secureOperationAddress(OperationContext context, ModelNode error) throws OperationFailedException {
            ModelNode bootError = error.get(BOOT_ERROR);
            if (bootError.hasDefined(FAILED)) {
                ModelNode failedOperation = bootError.get(FAILED);
                ModelNode address = failedOperation.get(OP_ADDR);
                ModelNode fakeOperation = new ModelNode();
                fakeOperation.get(ModelDescriptionConstants.OP).set(READ_RESOURCE_OPERATION);
                fakeOperation.get(ModelDescriptionConstants.OP_ADDR).set(address);
                AuthorizedAddress authorizedAddress = AuthorizedAddress.authorizeAddress(context, fakeOperation);
                if(authorizedAddress.isElided()) {
                    failedOperation.get(OP_ADDR).set(authorizedAddress.getAddress());
                }
            }
        }
    }
}
