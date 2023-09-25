/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.dmr.ModelNode;

/**
 * Removes the HTTP management interface.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HttpManagementRemoveHandler extends ReloadRequiredRemoveStepHandler {

    public static final HttpManagementRemoveHandler INSTANCE = new HttpManagementRemoveHandler();

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final PathAddress nativeAddress = context.getCurrentAddress().getParent().append(PathElement.pathElement(MANAGEMENT_INTERFACE, NATIVE_INTERFACE));
        context.addStep((OperationContext opContext, ModelNode op) -> {
            ManagementRemotingServices.isManagementResourceRemoveable(opContext, nativeAddress);
        }, OperationContext.Stage.MODEL, false);
        super.performRemove(context, operation, model);
    }

    static void clearHostControllerInfo(LocalHostControllerInfoImpl hostControllerInfo) {
        hostControllerInfo.setHttpManagementInterface(null);
        hostControllerInfo.setHttpManagementPort(0);
        hostControllerInfo.setHttpManagementSecureInterface(null);
        hostControllerInfo.setHttpManagementSecurePort(0);
    }
}
