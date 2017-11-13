/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
