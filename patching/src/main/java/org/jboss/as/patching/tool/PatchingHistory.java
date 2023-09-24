/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import static org.jboss.as.patching.Constants.BASE;
import static org.jboss.as.patching.metadata.Patch.PatchType.CUMULATIVE;
import static org.jboss.as.patching.metadata.Patch.PatchType.ONE_OFF;
import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.installation.PatchableTarget.TargetInfo;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.Patch.PatchType;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.metadata.PatchElementProvider;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.metadata.RollbackPatch;
import org.jboss.as.patching.runner.PatchUtils;
import org.jboss.dmr.ModelNode;

/**
 * Provides a read-only view to the patching history of the installation.
 *
 * @author Alexey Loubyansky
 */
public interface PatchingHistory {

    /**
     * Information about a patch.
     *
     * @author Alexey Loubyansky
     */
    public interface Entry {
        String getPatchId();
        Patch.PatchType getType();
        String getAppliedAt();
        boolean isAgedOut();

        /**
         * Patch element ids by layer names they are applied to.
         *
         * @return  map of patch element ids by layer names they are applied to
         */
        Map<String,String> getLayerPatches();
        Map<String,String> getAddOnPatches();

        Patch getMetadata();
    }

    public interface Iterator extends java.util.Iterator<Entry> {
        /**
         * Whether there is still a cumulative patch.
         *
         * @return  true if there is still a cumulative patch, otherwise - false
         */
        boolean hasNextCP();

        /**
         * Skips all the one-off patches (if any) and moves straight to
         * the next cumulative patch. If there is no cumulative patch left,
         * NoSuchElementException will be thrown.
         *
         * @return  next cumulative patch or throw NoSuchElementException
         *          if no more cumulative patches left
         */
        Entry nextCP();
    }

    /**
     * Is equivalent to getHistory(false).
     *
     * @return  returns a list of entries representing basic info
     *          about the patches applied or an empty list if
     *          there is no patching information
     * @throws PatchingException  in case there was an error loading the history
     */
    default ModelNode getHistory() throws PatchingException {
        return getHistory(false);
    }

    /**
     * Returns the history as a list of ModelNode's
     * Entry node has the following attributes:
     * - patch-id - the id of the patch;
     * - type - the type of the patch (cumulative or one-off);
     * - applied-at - a timestamp the patch was applied at.
     *
     * @param excludeAgedOut  whether to exclude the aged out patches
     * @return  returns a list of entries representing basic info
     *          about the patches applied or an empty list if
     *          there is no patching information
     * @throws PatchingException  in case there was an error loading the history
     */
    ModelNode getHistory(boolean excludeAgedOut) throws PatchingException;

    /**
     * Is equivalent to getHistory(info, false)
     *
     * @param info  the point from which to load the history
     * @return  returns a list of entries representing basic info
     *          about the patches applied or an empty list if
     *          there is no patching information
     * @throws PatchingException  in case there was an error loading the history
     */
    default ModelNode getHistory(PatchableTarget.TargetInfo info) throws PatchingException {
        return getHistory(info, false);
    }

    /**
     * Same as getHistory() but for the specified target,
     * i.e. specific point.
     *
     * @param info  the point from which to load the history
     * @param excludeAgedOut  whether to exclude the aged out patches
     * @return  returns a list of entries representing basic info
     *          about the patches applied or an empty list if
     *          there is no patching information
     * @throws PatchingException  in case there was an error loading the history
     */
    ModelNode getHistory(PatchableTarget.TargetInfo info, boolean excludeAgedOut) throws PatchingException;

    /**
     * Is equivalent to iterator(false).
     *
     * @return  iterator over the patching history
     *
     * @throws PatchingException  in case there was an error loading the history
     */
    default Iterator iterator() throws PatchingException {
        return iterator(false);
    }

    /**
     * Returns an iterator over the history.
     *
     * @param excludeAgedOut  whether to exclude the aged out patches
     * @return  iterator over the patching history
     *
     * @throws PatchingException  in case there was an error loading the history
     */
    Iterator iterator(boolean excludeAgedOut) throws PatchingException;

    /**
     * Is equivalent to iterator(info, false).
     *
     * @param info  the point to start from
     * @return  iterator over the patching history
     * @throws PatchingException  in case there was an error loading the history
     */
    default Iterator iterator(final PatchableTarget.TargetInfo info) throws PatchingException {
        return iterator(info, false);
    }

    /**
     * Same as iterator() but starting from a specific point.
     *
     * @param info  the point to start from
     * @param excludeAgedOut  whether to exclude the aged out patches
     * @return  iterator over the patching history
     * @throws PatchingException  in case there was an error loading the history
     */
    Iterator iterator(final PatchableTarget.TargetInfo info, boolean excludeAgedOut) throws PatchingException;

    public class Factory {

        private Factory() {}

        public static ModelNode getHistory(InstalledIdentity installedImage, PatchableTarget.TargetInfo info) throws PatchingException {
            return getHistory(installedImage, info, false);
        }

        public static ModelNode getHistory(InstalledIdentity installedImage, PatchableTarget.TargetInfo info, boolean excludeAgedOut) throws PatchingException {
            final ModelNode result = new ModelNode();
            result.setEmptyList();
            fillHistoryIn(installedImage, info, result, excludeAgedOut);
            return result;
        }

        public static Iterator iterator(final InstalledIdentity mgr, final PatchableTarget.TargetInfo info) {
            return iterator(mgr, info, false);
        }

        public static Iterator iterator(final InstalledIdentity mgr, final PatchableTarget.TargetInfo info, boolean excludeAgedOut) {
            return new IteratorImpl(checkNotNullParam("info", info), mgr, excludeAgedOut);
        }

        public static PatchingHistory getHistory(final InstalledIdentity installedIdentity) {
            if(installedIdentity == null) {
                throw new IllegalStateException("installedImage is null");
            }

            return new PatchingHistory() {

                @Override
                public ModelNode getHistory(boolean excludeAgedOut) throws PatchingException {
                    try {
                        return getHistory(installedIdentity.getIdentity().loadTargetInfo(), excludeAgedOut);
                    } catch (IOException e) {
                        throw new PatchingException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(installedIdentity.getIdentity().getName()), e);
                    }
                }

                @Override
                public ModelNode getHistory(TargetInfo info, boolean excludeAgedOut) throws PatchingException {
                    return Factory.getHistory(installedIdentity, info, excludeAgedOut);
                }

                @Override
                public Iterator iterator(boolean excludeAgedOut) throws PatchingException {
                    try {
                        return iterator(installedIdentity.getIdentity().loadTargetInfo(), excludeAgedOut);
                    } catch (IOException e) {
                        throw new PatchingException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(installedIdentity.getIdentity().getName()), e);
                    }
                }

                @Override
                public Iterator iterator(TargetInfo info, boolean excludeAgedOut) throws PatchingException {
                    return Factory.iterator(installedIdentity, info, excludeAgedOut);
                }
            };
        }

        private static class IteratorState {
            protected PatchableTarget.TargetInfo currentInfo;
            protected int patchIndex;
            protected Patch.PatchType type = ONE_OFF;

            IteratorState(PatchableTarget.TargetInfo info) {
                this(info, -1);
            }

            IteratorState(PatchableTarget.TargetInfo info, int patchIndex) {
                if(info == null) {
                    throw new IllegalArgumentException("Target info is null");
                }
                this.currentInfo = info;
                this.patchIndex = patchIndex;
            }
        }

        private static final class IteratorImpl extends IteratorState implements Iterator {
            private final InstalledIdentity mgr;
            private final boolean excludeAgedOut;

            private IteratorImpl(TargetInfo info, InstalledIdentity mgr, boolean excludeAgedOut) {
                super(info);
                this.mgr = mgr;
                this.excludeAgedOut = excludeAgedOut;
            }

            @Override
            public boolean hasNext() {
                return hasNext(this, this);
            }

            private static boolean hasNext(IteratorImpl i, IteratorState state) {
                // current info hasn't been initialized yet
                if(state.patchIndex < 0) {
                    if(BASE.equals(state.currentInfo.getCumulativePatchID())) {
                        if(state.currentInfo.getPatchIDs().isEmpty()) {
                            return false; // unpatched
                        }
                    }
                    return true;
                }

                // check whether there are still one-offs left in the current info
                // one-offs + 1 means the cumulative patch has been returned as well
                final int size = state.currentInfo.getPatchIDs().size();
                if(state.patchIndex < size) {
                    return existsOnDisk(i, state.currentInfo.getPatchIDs().get(state.patchIndex));
                }

                // see whether there is the next CP
                final String releaseID = state.currentInfo.getCumulativePatchID();
                if(BASE.equals(releaseID)) {
                    return false;
                }

                // it's not the base yet and the cumulative has not been returned yet
                if(state.patchIndex == size) {
                    return existsOnDisk(i, state.currentInfo.getCumulativePatchID());
                }

                // if it's not BASE then it's a specific patch, so it actually
                // means that there should more to iterate. But we rely on
                // the presence of the patch directory and its rollback.xml.

                File patchHistoryDir = i.mgr.getInstalledImage().getPatchHistoryDir(releaseID);
                if(patchHistoryDir.exists()) {
                    final File rollbackXml = new File(patchHistoryDir, "rollback.xml");
                    if(rollbackXml.exists()) {
                        try {
                            final PatchBuilder patchBuilder = (PatchBuilder)PatchXml.parse(rollbackXml);
                            final RollbackPatch patch = (RollbackPatch) patchBuilder.build();
                            PatchableTarget.TargetInfo nextInfo = patch.getIdentityState().getIdentity().loadTargetInfo();
                            if(BASE.equals(nextInfo.getCumulativePatchID())) {
                                if(nextInfo.getPatchIDs().isEmpty()) {
                                    return false;
                                }
                            } else if(!existsOnDisk(i, nextInfo.getCumulativePatchID())) {
                                return false;
                            }
                        } catch(Exception e) {
                            throw new IllegalStateException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(i.mgr.getIdentity().getName()), e);
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Entry next() {
                return next(this, this);
            }

            private static Entry next(final IteratorImpl i, IteratorState state) {

                String patchId = nextPatchIdForCurrentInfo(state);
                if(patchId == null) { // current info is exhausted, try moving to the next CP
                    if(state.patchIndex < 0) {
                        state.patchIndex = 0;
                    } else {
                        final String releaseID = state.currentInfo.getCumulativePatchID();
                        if(BASE.equals(releaseID)) {
                            throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.noMorePatches());
                        }

                        final File patchHistoryDir = i.mgr.getInstalledImage().getPatchHistoryDir(releaseID);
                        if(patchHistoryDir.exists()) {
                            final File rollbackXml = new File(patchHistoryDir, "rollback.xml");
                            if(rollbackXml.exists()) {
                                try {
                                    final PatchBuilder patchBuilder = (PatchBuilder)PatchXml.parse(rollbackXml);
                                    final RollbackPatch patch = (RollbackPatch) patchBuilder.build();
                                    state.currentInfo = patch.getIdentityState().getIdentity().loadTargetInfo();
                                    state.patchIndex = 0;
                                    state.type = ONE_OFF;
                                } catch(Exception e) {
                                    throw new IllegalStateException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(i.mgr.getIdentity().getName()), e);
                                }
                            } else {
                                throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.patchIsMissingFile(rollbackXml.getAbsolutePath()));
                            }
                        } else {
                            throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.noPatchHistory(patchHistoryDir.getAbsolutePath()));
                        }
                    }

                    patchId = nextPatchIdForCurrentInfo(state);
                    if(patchId == null) {
                        throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.noMorePatches());
                    }
                    assertExistsOnDisk(i, patchId);
                }

                final String entryPatchId = patchId;
                final Patch.PatchType entryType = state.type;

                return new Entry() {
                    String appliedAt;
                    Map<String,String> layerPatches;
                    Map<String,String> addOnPatches;
                    Patch patch;
                    Boolean agedOut;

                    @Override
                    public String getPatchId() {
                        return entryPatchId;
                    }

                    @Override
                    public PatchType getType() {
                        return entryType;
                    }

                    @Override
                    public String getAppliedAt() {
                        if(appliedAt == null) {
                            final File patchHistoryDir = i.mgr.getInstalledImage().getPatchHistoryDir(entryPatchId);
                            if(patchHistoryDir.exists()) {
                                try {
                                    appliedAt = getAppliedAt(patchHistoryDir);
                                } catch (PatchingException e) {
                                }
                            }
                        }
                        return appliedAt;
                    }

                    @Override
                    public Map<String, String> getLayerPatches() {
                        if(layerPatches == null) {
                            layerPatches = loadLayerPatches(false);
                        }
                        return layerPatches;
                    }

                    @Override
                    public Map<String, String> getAddOnPatches() {
                        if(addOnPatches == null) {
                            addOnPatches = loadLayerPatches(true);
                        }
                        return addOnPatches;
                    }

                    private String getAppliedAt(File patchDir) throws PatchingException {
                        File timestampFile = new File(patchDir, Constants.TIMESTAMP);
                        try {
                            return timestampFile.exists() ? PatchUtils.readRef(timestampFile) : null;
                        } catch (IOException e) {
                            throw new PatchingException(PatchLogger.ROOT_LOGGER.fileIsNotReadable(timestampFile.getAbsolutePath()));
                        }
                    }

                    private Map<String,String> loadLayerPatches(boolean addons) {
                        Map<String,String> result = Collections.emptyMap();
                        final Patch patch = getMetadata();
                        if(patch != null) {
                            result = new HashMap<String, String>();
                            for (PatchElement e : patch.getElements()) {
                                if (e.getProvider().isAddOn() == addons) {
                                    result.put(e.getProvider().getName(), e.getId());
                                }
                            }
                        }
                        return result;
                    }

                    @Override
                    public Patch getMetadata() {
                        if(patch == null) {
                            final File patchDir = i.mgr.getInstalledImage().getPatchHistoryDir(entryPatchId);
                            if(patchDir.exists()) {
                                final File patchXml = new File(patchDir, "patch.xml");
                                if(patchXml.exists()) {
                                    try {
                                        patch = ((PatchBuilder)PatchXml.parse(patchXml)).build();
                                    } catch (Exception e) {
                                        PatchLogger.ROOT_LOGGER.error(e.getLocalizedMessage(), e);
                                        throw new IllegalStateException(patchXml + " is corrupted");
                                    }
                                }
                            } else {
                                throw new IllegalStateException("Failed to locate patch " + entryPatchId + " in the history");
                            }
                        }
                        return patch;
                    }

                    @Override
                    public boolean isAgedOut() {
                        if (agedOut == null) {
                            agedOut = Factory.isAgedOut(i.mgr, getMetadata());
                        }
                        return agedOut;
                    }
                };
            }

            /**
             * @throws UnsupportedOperationException if the {@code remove}
             *         operation is not supported by this iterator.
             */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            private static boolean existsOnDisk(IteratorImpl i, String id) {
                try {
                    assertExistsOnDisk(i, id);
                    return true;
                } catch(NoSuchElementException e) {
                    return false;
                }
            }

            private static void assertExistsOnDisk(IteratorImpl i, String id) throws NoSuchElementException {
                final File historyDir = i.mgr.getInstalledImage().getPatchHistoryDir(id);
                if(!historyDir.exists()) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.noPatchHistory(historyDir.getAbsolutePath()));
                }
                // TODO parsed xml can be cached
                final File rollbackXml = new File(historyDir, "rollback.xml");
                if(!rollbackXml.exists()) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.patchIsMissingFile(rollbackXml.getAbsolutePath()));
                }
                try {
                    PatchXml.parse(rollbackXml);
                } catch (Exception e) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.fileIsNotReadable(rollbackXml.getAbsolutePath() + ": " + e.getLocalizedMessage()));
                }
                final File patchXml = new File(historyDir, "patch.xml");
                if(!patchXml.exists()) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.patchIsMissingFile(patchXml.getAbsolutePath()));
                }
                final Patch patchMetaData;
                try {
                    patchMetaData = PatchXml.parse(patchXml).resolvePatch(i.mgr.getIdentity().getName(), i.mgr.getIdentity().getVersion());
                } catch (Exception e) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.fileIsNotReadable(patchXml.getAbsolutePath() + ": " + e.getLocalizedMessage()));
                }
                if(i.excludeAgedOut && isAgedOut(i.mgr, patchMetaData)) {
                    throw new NoSuchElementException("Patch " + patchMetaData.getPatchId() + " was aged out");
                }
            }

            /**
             * Returns the next patch id, be it a one-off or the CP
             * <b>for the current info</b>. If the current info has been
             * exhausted, the method returns null.
             */
            private static String nextPatchIdForCurrentInfo(IteratorState state) {
                if(state.patchIndex < 0) {
                    return null;
                }
                final int size = state.currentInfo.getPatchIDs().size();
                if(state.patchIndex < size) {
                    return state.currentInfo.getPatchIDs().get(state.patchIndex++);
                } else if(state.patchIndex == size) {
                    ++state.patchIndex;
                    state.type = CUMULATIVE;
                    final String cp = state.currentInfo.getCumulativePatchID();
                    return BASE.equals(cp) ? null : cp;
                }
                return null;
            }

            @Override
            public boolean hasNextCP() {
                final IteratorState state = new IteratorState(currentInfo, patchIndex);
                return nextCP(this, state) != null;
            }

            @Override
            public Entry nextCP() {
                final IteratorState state = new IteratorState(currentInfo, patchIndex);
                final Entry entry = nextCP(this, state);
                if(entry == null) {
                    throw new NoSuchElementException(PatchLogger.ROOT_LOGGER.noMorePatches());
                }
                currentInfo = state.currentInfo;
                patchIndex = state.patchIndex;
                type = state.type;
                return entry;
            }

            private static Entry nextCP(IteratorImpl i, IteratorState state) {
                while(hasNext(i, state)) {
                    final Entry entry = next(i, state);
                    if(state.type == Patch.PatchType.CUMULATIVE) {
                        return entry;
                    }
                }
                return null;
            }
        }

        private static boolean isAgedOut(InstalledIdentity installedIdentity, final Patch metadata) {
            for (PatchElement pe : metadata.getElements()) {
                final PatchElementProvider peProvider = pe.getProvider();
                final PatchableTarget layer = peProvider.isAddOn() ? installedIdentity.getAddOn(peProvider.getName())
                        : installedIdentity.getLayer(peProvider.getName());
                if (layer == null) {
                    // if an identity is missing a layer that means either it was re-configured and the layer
                    // was removed
                    // or the layer in the patch is optional and may be missing on the disk
                    // we haven't had this case yet, not sure how critical this is to terminate the process of
                    // cleaning up
                    throw new IllegalStateException(PatchLogger.ROOT_LOGGER.layerNotFound(peProvider.getName()));
                }
                final File patchDir = layer.getDirectoryStructure().getModulePatchDirectory(pe.getId());
                if (!patchDir.exists()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Goes back in rollback history adding the patch id and it's application timestamp
         * to the resulting list.
         */
        private static void fillHistoryIn(InstalledIdentity installedImage, PatchableTarget.TargetInfo info, ModelNode result, boolean excludeAgedOut) throws PatchingException {
            final Iterator i = iterator(installedImage, info, excludeAgedOut);
            while(i.hasNext()) {
                final Entry next = i.next();
                fillHistoryIn(result, next);
            }
        }

        private static void fillHistoryIn(ModelNode result, Entry entry) throws PatchingException {
            ModelNode history = new ModelNode();
            history.get(Constants.PATCH_ID).set(entry.getPatchId());
            history.get(Constants.TYPE).set(entry.getType().getName());
            final ModelNode appliedAtNode = history.get(Constants.APPLIED_AT);
            if(entry.getAppliedAt() != null) {
                appliedAtNode.set(entry.getAppliedAt());
            }
            history.get(Constants.AGED_OUT).set(entry.isAgedOut());
            result.add(history);
        }
    }
}
