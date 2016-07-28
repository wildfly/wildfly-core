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

package org.jboss.as.patching.runner;

import static org.jboss.as.patching.IoUtils.safeClose;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.VerbosePatchInfo;
import org.jboss.as.patching.ZipUtils;
import org.jboss.as.patching.installation.AddOn;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerImpl;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.installation.Layer;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.BundledPatch;
import org.jboss.as.patching.metadata.BundledPatch.BundledPatchEntry;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBundleXml;
import org.jboss.as.patching.metadata.PatchMerger;
import org.jboss.as.patching.metadata.PatchMetadataResolver;
import org.jboss.as.patching.metadata.PatchXml;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.as.patching.tool.PatchingHistory;
import org.jboss.as.patching.tool.PatchingResult;

/**
 * The default patch tool implementation.
 *
 * @author Emanuel Muckenhuber
 */
public class PatchToolImpl implements PatchTool {

    private final InstallationManager manager;
    private final InstallationManager.ModificationCompletionCallback callback;
    private final IdentityPatchRunner runner;

    public PatchToolImpl(final InstallationManager manager) {
        this.manager = manager;
        this.runner = new IdentityPatchRunner(manager.getInstalledImage());
        this.callback = runner;
    }

    @Override
    public List<String> getPatchStreams() throws PatchingException {
        final List<InstalledIdentity> installedIdentities = manager.getInstalledIdentities();
        if(installedIdentities.size() == 1) {
            return Collections.singletonList(installedIdentities.get(0).getIdentity().getName());
        }
        final List<String> result = new ArrayList<String>(installedIdentities.size());
        for(InstalledIdentity ii : installedIdentities) {
            result.add(ii.getIdentity().getName());
        }
        return result;
    }

    @Override
    public PatchInfo getPatchInfo() throws PatchingException {
        return getPatchInfo(null);
    }

    @Override
    public PatchInfo getPatchInfo(String streamName) throws PatchingException {
        try {
            final InstalledIdentity installedIdentity = streamName == null ? manager.getDefaultIdentity() : manager.getInstalledIdentity(streamName, null);
            final PatchableTarget.TargetInfo info = installedIdentity.getIdentity().loadTargetInfo();
            final VerbosePatchInfo.Builder infoBuilder = VerbosePatchInfo.builder()
                    .setVersion(installedIdentity.getIdentity().getVersion())
                    .setCumulativePatchId(info.getCumulativePatchID())
                    .setPatchIds(info.getPatchIDs());
            for(Layer layer : installedIdentity.getLayers()) {
                infoBuilder.addLayerInfo(layer.getName(), layer.loadTargetInfo());
            }
            for(AddOn addon : installedIdentity.getAddOns()) {
                infoBuilder.addAddOnInfo(addon.getName(), addon.loadTargetInfo());
            }
           return infoBuilder.build();
        } catch (IOException e) {
            // why throw a rethrowException(e) ?
            throw new RuntimeException(e);
        }
    }

    @Override
    public PatchingHistory getPatchingHistory() throws PatchingException {
        return getPatchingHistory(null);
    }

    @Override
    public PatchingHistory getPatchingHistory(String streamName) throws PatchingException {
        final InstalledIdentity identity = streamName == null ? manager.getDefaultIdentity() : manager.getInstalledIdentity(streamName, null);
        return PatchingHistory.Factory.getHistory(identity);
    }

    @Override
    public PatchingResult applyPatch(final File file, final ContentVerificationPolicy contentPolicy) throws PatchingException {
        try {
            if (file.isDirectory()) {
                final File patchXml = new File(file, PatchXml.PATCH_XML);
                if (patchXml.exists()) {
                    // Shortcut exploded patches
                    return execute(file, contentPolicy);
                }
            }

            try (final InputStream is = new FileInputStream(file)) {
                return applyPatch(is, contentPolicy);
            }
        } catch (Exception e) {
            throw rethrowException(e);
        }
    }

    @Override
    public PatchingResult applyPatch(final URL url, final ContentVerificationPolicy contentPolicy) throws PatchingException {
        try {
            final InputStream is = url.openStream();
            try {
                return applyPatch(is, contentPolicy);
            } finally {
                if(is != null) try {
                    is.close();
                } catch (IOException e) {
                    PatchLogger.ROOT_LOGGER.debugf(e, "failed to close input stream");
                }
            }
        } catch (IOException e) {
            throw new PatchingException(e);
        }
    }

    @Override
    public PatchingResult applyPatch(final InputStream is, final ContentVerificationPolicy contentPolicy) throws PatchingException {
        return applyPatch(null, is, contentPolicy);
    }

    private PatchingResult applyPatch(final File parentWorkDir, final InputStream is, final ContentVerificationPolicy contentPolicy) throws PatchingException {
        File workDir = null;
        try {
            // Create a working dir
            workDir = parentWorkDir == null ? IdentityPatchRunner.createTempDir() : IdentityPatchRunner.createTempDir(parentWorkDir);

            try {
                // Save the content
                Path cachedContent = workDir.toPath().resolve("content");
                Files.copy(is, cachedContent);
                // Unpack to the work dir
                ZipUtils.unzip(cachedContent.toFile(), workDir);
            } catch (IOException e) {
                throw PatchLogger.ROOT_LOGGER.cannotCopyFilesToTempDir(workDir.getAbsolutePath(), e.getMessage(), e); // add info that temp dir is involved
            }

            // Execute
            return execute(workDir, contentPolicy);
        } catch (Exception e) {
            throw rethrowException(e);
        } finally {
            if (workDir != null && !IoUtils.recursiveDelete(workDir)) {
                PatchLogger.ROOT_LOGGER.cannotDeleteFile(workDir.getAbsolutePath());
            }
        }
    }

    @Override
    public PatchingResult rollback(final String patchId, final ContentVerificationPolicy contentPolicy,
                                   final boolean rollbackTo, final boolean resetConfiguration) throws PatchingException {
        return rollback(null, patchId, contentPolicy, rollbackTo, resetConfiguration);
    }

    @Override
    public PatchingResult rollback(final String streamName, final String patchId, final ContentVerificationPolicy contentPolicy,
            final boolean rollbackTo, final boolean resetConfiguration) throws PatchingException {
        InstalledIdentity targetIdentity = null;
        if (streamName == null) {
            for (InstalledIdentity identity : manager.getInstalledIdentities()) {
                if (identity.getAllInstalledPatches().contains(patchId)) {
                    if (targetIdentity != null) {
                        throw new PatchingException(PatchLogger.ROOT_LOGGER.patchIdFoundInMoreThanOneStream(patchId,
                                targetIdentity.getIdentity().getName(), identity.getIdentity().getName()));
                    }
                    targetIdentity = identity;
                }
            }
            if(targetIdentity == null) {
                throw PatchLogger.ROOT_LOGGER.patchNotFoundInHistory(patchId);
            }
        } else {
            targetIdentity = manager.getInstalledIdentity(streamName, null);
        }

        // Rollback the patch
        final InstallationManager.InstallationModification modification = targetIdentity.modifyInstallation(runner);
        try {
            return runner.rollbackPatch(patchId, contentPolicy, rollbackTo, resetConfiguration, modification);
        } catch (Exception e) {
            modification.cancel();
            throw rethrowException(e);
        }
    }

    @Override
    public PatchingResult rollbackLast(final ContentVerificationPolicy contentPolicy, final boolean resetConfiguration) throws PatchingException {
        return rollbackLast(null, contentPolicy, resetConfiguration);
    }

    @Override
    public PatchingResult rollbackLast(final String streamName, final ContentVerificationPolicy contentPolicy, final boolean resetConfiguration) throws PatchingException {
        final InstalledIdentity targetIdentity = streamName == null ? manager.getDefaultIdentity() : manager.getInstalledIdentity(streamName, null);
        final InstallationManager.InstallationModification modification = targetIdentity.modifyInstallation(runner);
        try {
            return runner.rollbackLast(contentPolicy, resetConfiguration, modification);
        } catch (Exception e) {
            modification.cancel();
            throw rethrowException(e);
        }
    }

    protected PatchingResult execute(final File workDir, final ContentVerificationPolicy contentPolicy)
            throws PatchingException, IOException, XMLStreamException {

        final File patchBundleXml = new File(workDir, PatchBundleXml.MULTI_PATCH_XML);
        if (patchBundleXml.exists()) {
            final InputStream patchIs = new FileInputStream(patchBundleXml);
            try {
                // Handle multi patch installs
                final BundledPatch bundledPatch = PatchBundleXml.parse(patchIs);
                return applyPatchBundle(workDir, bundledPatch, contentPolicy);
            } finally {
                safeClose(patchIs);
            }
        } else {
            // Parse the xml
            File patchXml = new File(workDir, PatchXml.PATCH_XML);
            PatchMetadataResolver patchResolver = parsePatchXml(patchXml);

            Patch patch = patchResolver.resolvePatch(null, null);
            final InstalledIdentity installedIdentity = manager.getInstalledIdentity(patch.getIdentity().getName(), null);
            final String currentVersion = installedIdentity.getIdentity().getVersion();
            if(!Constants.UNKNOWN.equals(currentVersion) && !patch.getIdentity().getVersion().equals(currentVersion)) {
                patchXml = new File(workDir, currentVersion + PatchMerger.PATCH_XML_SUFFIX);
                if(!patchXml.exists()) {
                    throw new PatchingException("The patch does not contain metadata for currently installed " + patch.getIdentity().getName() + " version " + currentVersion);
                }
                patchResolver = parsePatchXml(patchXml);
                patch = patchResolver.resolvePatch(null, null);
            }

            return apply(patchResolver, PatchContentProvider.DefaultContentProvider.create(workDir), contentPolicy);
        }
    }

    private PatchMetadataResolver parsePatchXml(final File patchXml) throws XMLStreamException, IOException {
        InputStream patchIS = null;
        try {
            patchIS = new FileInputStream(patchXml);
            return PatchXml.parse(patchIS);
        } finally {
            safeClose(patchIS);
        }
    }

    protected PatchingResult apply(final PatchMetadataResolver patchResolver, final PatchContentProvider contentProvider, final ContentVerificationPolicy contentPolicy) throws PatchingException {
        // Apply the patch
        final org.jboss.as.patching.metadata.Identity identity = patchResolver.resolvePatch(null, null).getIdentity();
        final InstallationManager.InstallationModification modification = ((InstallationManagerImpl)manager).
                getInstalledIdentity(identity.getName(), identity.getVersion()).modifyInstallation(callback);
        try {
            return runner.applyPatch(patchResolver, contentProvider, contentPolicy, modification);
        } catch (Exception e) {
            modification.cancel();
            throw rethrowException(e);
        }
    }

    protected PatchingResult applyPatchBundle(final File workDir, final BundledPatch bundledPatch, final ContentVerificationPolicy contentPolicy) throws PatchingException, IOException {
        final List<BundledPatchEntry> patches = bundledPatch.getPatches();
        if(patches.isEmpty()) {
            throw new PatchingException(PatchLogger.ROOT_LOGGER.patchBundleIsEmpty());
        }

        PatchingResult result = null;
        BundledPatchEntry lastCommittedEntry = null;
        final List<BundledPatch.BundledPatchEntry> results = new ArrayList<BundledPatch.BundledPatchEntry>(patches.size());

        final List<InstalledIdentity> installedIdentities = manager.getInstalledIdentities();
        for(BundledPatchEntry entry : patches) {
            // TODO this has to be checked against the specific one targeted by the patch
            boolean alreadyApplied = false;
            for (InstalledIdentity identity : installedIdentities) {
                if (identity.getAllInstalledPatches().contains(entry.getPatchId())) {
                    alreadyApplied = true;
                    break;
                }
            }
            if(alreadyApplied) {
                continue;
            }

            if(result != null) {
                result.commit();
                results.add(0, lastCommittedEntry);
            }

            final File patch = new File(workDir, entry.getPatchPath());
            final FileInputStream is = new FileInputStream(patch);
            PatchingResult currentResult = null;
            try {
                currentResult = applyPatch(workDir, is, contentPolicy);
            } catch (PatchingException e) {
                // Undo the changes included as part of this patch
                for (BundledPatch.BundledPatchEntry committed : results) {
                    try {
                        rollback(committed.getPatchId(), contentPolicy, false, false).commit();
                    } catch (PatchingException oe) {
                        PatchLogger.ROOT_LOGGER.debugf(oe, "failed to rollback patch '%s'", committed.getPatchId());
                    }
                }
                throw e;
            } finally {
                safeClose(is);
            }

            if (currentResult != null) {
                result = currentResult;
                lastCommittedEntry = entry;
            }
        }

        if (result == null) {
            throw new PatchingException();
        }
        return new WrappedMultiInstallPatch(result, contentPolicy, results);
    }

    static PatchingException rethrowException(final Exception e) {
        if (e instanceof PatchingException) {
            return (PatchingException) e;
        } else {
            return new PatchingException(e);
        }
    }

    class WrappedMultiInstallPatch implements PatchingResult {

        private final PatchingResult last;
        private final ContentVerificationPolicy policy;
        private final List<BundledPatch.BundledPatchEntry> committed;

        WrappedMultiInstallPatch(PatchingResult last, ContentVerificationPolicy policy, List<BundledPatch.BundledPatchEntry> committed) {
            this.last = last;
            this.policy = policy;
            this.committed = committed;
        }

        @Override
        public String getPatchId() {
            return last.getPatchId();
        }

        @Override
        public PatchInfo getPatchInfo() {
            return last.getPatchInfo();
        }

        @Override
        public void commit() {
            last.commit();
        }

        @Override
        public void rollback() {
            last.rollback(); // Rollback the last
            for (final BundledPatch.BundledPatchEntry entry : committed) {
                try {
                    PatchToolImpl.this.rollback(entry.getPatchId(), policy, false, false).commit();
                } catch (Exception e) {
                    PatchLogger.ROOT_LOGGER.debugf(e, "failed to rollback patch '%s'", entry.getPatchId());
                }
            }
        }
    }

}
