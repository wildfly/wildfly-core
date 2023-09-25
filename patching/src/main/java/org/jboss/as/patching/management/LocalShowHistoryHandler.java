/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;


import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.dmr.ModelNode;


/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012, Red Hat Inc
 * @author Alexey Loubyansky
 */
public final class LocalShowHistoryHandler extends PatchStreamResourceOperationStepHandler {
    public static final OperationStepHandler INSTANCE = new LocalShowHistoryHandler();

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstalledIdentity installedIdentity) throws OperationFailedException {

        final boolean excludeAgedOut = PatchResourceDefinition.EXCLUDE_AGEDOUT.resolveModelAttribute(context, operation).asBoolean();
        try {
            final PatchableTarget.TargetInfo info = installedIdentity.getIdentity().loadTargetInfo();
            final ModelNode result =  PatchingHistory.Factory.getHistory(installedIdentity, info, excludeAgedOut);
            context.getResult().set(result);
        } catch (Throwable t) {
            PatchLogger.ROOT_LOGGER.debugf(t, "failed to get history");
            throw PatchLogger.ROOT_LOGGER.failedToShowHistory(t);
        }
    }
}
