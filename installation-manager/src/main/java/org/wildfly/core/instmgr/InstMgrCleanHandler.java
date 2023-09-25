/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.installationmanager.spi.InstallationManagerFactory;

/**
 * Operation handler that cleans the installation manager work directories or remove a custom patch.
 */
public class InstMgrCleanHandler extends InstMgrOperationStepHandler {
    static final String OPERATION_NAME = "clean";
    protected static final AttributeDefinition LIST_UPDATES_WORK_DIR = SimpleAttributeDefinitionBuilder.create(InstMgrConstants.LIST_UPDATES_WORK_DIR, ModelType.STRING)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, InstMgrResolver.RESOLVER)
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .addParameter(LIST_UPDATES_WORK_DIR)
            .setRuntimeOnly()
            .build();

    InstMgrCleanHandler(InstMgrService imService, InstallationManagerFactory imf) {
        super(imService, imf);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String listUpdatesWorkDir = LIST_UPDATES_WORK_DIR.resolveModelAttribute(context, operation).asStringOrNull();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.acquireControllerLock();
                try {
                    if (listUpdatesWorkDir != null) {
                        // Notice listUpdatesWorkDir is just a key to locate the real path to be deleted.
                        // The imService maintains a relationship between this key and the temporal directories created to unzip
                        // files and other temporal work.
                        imService.deleteTempDir(listUpdatesWorkDir);
                    } else {
                        deleteDirIfExits(imService.getPreparedServerDir());
                        imService.deleteTempDirs();
                        imService.resetCandidateStatus();
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
