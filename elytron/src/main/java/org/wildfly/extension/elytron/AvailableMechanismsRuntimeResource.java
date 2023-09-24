/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;

import java.util.function.Function;

import org.jboss.as.controller.DelegatingResourceDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * A {@link ResourceDefinition} to wrap an existing resource and add a runtime attribute to return the available authentication mechanisms.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AvailableMechanismsRuntimeResource extends DelegatingResourceDefinition {

    private final Function<OperationContext, String[]> availableMechanismsFunction;

    private static final StringListAttributeDefinition AVAILABLE_MECHANISMS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.AVAILABLE_MECHANISMS)
        .setStorageRuntime()
        .build();

    private AvailableMechanismsRuntimeResource(ResourceDefinition delegate, Function<OperationContext, String[]> availableMechanismsFunction) {
        this.availableMechanismsFunction = checkNotNullParam("availableMechanismsFunction", availableMechanismsFunction);
        setDelegate(delegate);
    }

    static ResourceDefinition wrap(ResourceDefinition delegate, Function<OperationContext, String[]> availableMechanismsFunction) {
        return new AvailableMechanismsRuntimeResource(delegate, availableMechanismsFunction);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(AVAILABLE_MECHANISMS, new AvailableMechanismsHandler());
        }
    }

    private class AvailableMechanismsHandler extends ElytronRuntimeOnlyHandler {

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            String[] mechanisms = availableMechanismsFunction.apply(context);

            ModelNode mechanismList = new ModelNode();
            if (mechanisms != null) {
                for (String current : mechanisms) {
                    mechanismList.add(current);
                }
            }
            context.getResult().set(mechanismList);
        }
    }

}

