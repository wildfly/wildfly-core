/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.controller.extension;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Base handler for the extension resource remove operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REMOVE;
    private final ExtensionRegistry extensionRegistry;
    private final boolean isMasterDomainController;
    private final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider;

    /**
     * Create the ExtensionRemoveHandler
     *
     * @param extensionRegistry the registry for extensions
     */
    public ExtensionRemoveHandler(final ExtensionRegistry extensionRegistry,
                                  final boolean isMasterDomainController,
                                  final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider) {
        this.extensionRegistry = extensionRegistry;
        this.isMasterDomainController = isMasterDomainController;
        this.rootResourceRegistrationProvider = rootResourceRegistrationProvider;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String module = address.getLastElement().getValue();

        context.removeResource(PathAddress.EMPTY_ADDRESS);

        final ManagementResourceRegistration rootRegistration = rootResourceRegistrationProvider.getRootResourceRegistrationForUpdate(context);
        extensionRegistry.removeExtension(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS), module, rootRegistration);

        if (context.isNormalServer()) {
            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.reloadRequired();
                    context.completeStep(OperationContext.RollbackHandler.REVERT_RELOAD_REQUIRED_ROLLBACK_HANDLER);
                }
            }, OperationContext.Stage.RUNTIME);
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                // Restore the extension to the ExtensionRegistry and the ManagementResourceRegistration tree
                ExtensionAddHandler.initializeExtension(extensionRegistry, module, rootRegistration, isMasterDomainController);
            }
        });
    }
}
