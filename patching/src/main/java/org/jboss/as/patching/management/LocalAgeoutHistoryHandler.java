/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.Layer;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.dmr.ModelNode;

/**
 * This handler removes the part of the history which is inactive.
 *
 * @author Alexey Loubyansky
 */
public class LocalAgeoutHistoryHandler extends PatchStreamResourceOperationStepHandler {

    public static final LocalAgeoutHistoryHandler INSTANCE = new LocalAgeoutHistoryHandler();

    static final FilenameFilter ALL = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return true;
        }
    };

    static final FilenameFilter HISTORY_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            if (PatchXml.PATCH_XML.equals(name) || PatchXml.ROLLBACK_XML.equals(name)) {
                return false;
            }
            return true;
        }
    };

    @Override
    protected void execute(OperationContext context, ModelNode operation, InstallationManager instMgr, String patchStream) throws OperationFailedException {
        try {
            if (patchStream != null) {
                final InstalledIdentity installedIdentity = instMgr.getInstalledIdentity(patchStream, null);
                ageOutHistory(installedIdentity);
            } else {
                for (InstalledIdentity installedIdentity : instMgr.getInstalledIdentities()) {
                    ageOutHistory(installedIdentity);
                }
            }
        } catch (PatchingException e) {
            throw new IllegalStateException(PatchLogger.ROOT_LOGGER.failedToLoadIdentity(), e);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    protected void ageOutHistory(final InstalledIdentity installedIdentity) {

        final PatchableTarget.TargetInfo info;
        try {
            info = installedIdentity.getIdentity().loadTargetInfo();
        } catch (IOException e) {
            throw new IllegalStateException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(installedIdentity.getIdentity().getName()), e);
        }
        final PatchingHistory.Iterator i = PatchingHistory.Factory.iterator(installedIdentity, info);
        if(i.hasNextCP()) {
            i.nextCP();
            // everything else down to the base is inactive
            while(i.hasNext()) {
                final PatchingHistory.Entry entry = i.next();
                final Map<String, String> layerPatches = entry.getLayerPatches();
                final List<DeleteOp> ops = new ArrayList<>();

                if (!layerPatches.isEmpty()) {
                    for (String layerName : layerPatches.keySet()) {
                        final Layer layer = installedIdentity.getLayer(layerName);
                        if (layer == null) {
                            // if an identity is missing a layer that means either it was re-configured and the layer was removed
                            // or the layer in the patch is optional and may be missing on the disk
                            // we haven't had this case yet, not sure how critical this is to terminate the process of cleaning up
                            throw new IllegalStateException(PatchLogger.ROOT_LOGGER.layerNotFound(layerName));
                        }
                        final File patchDir = layer.getDirectoryStructure().getModulePatchDirectory(layerPatches.get(layerName));
                        if (patchDir.exists()) {
                            ops.add(new DeleteOp(patchDir, ALL));
                        }
                    }
                }
                final File patchHistoryDir = installedIdentity.getInstalledImage().getPatchHistoryDir(entry.getPatchId());
                if (patchHistoryDir.exists()) {
                    ops.add(new DeleteOp(patchHistoryDir, HISTORY_FILTER));
                }

                try {
                    DeleteOp.execute(ops);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
