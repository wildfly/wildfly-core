/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.logging;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.WARN;

import java.io.IOException;
import java.io.SyncFailedException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.patching.ContentConflictsException;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.validation.PatchingArtifact;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Param;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYPAT", length = 4)
public interface PatchLogger extends BasicLogger {

    PatchLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), PatchLogger.class, "org.jboss.as.patching");

    @LogMessage(level = WARN)
    @Message(id = 1, value = "Cannot delete file %s")
    void cannotDeleteFile(String name);

    @LogMessage(level = WARN)
    @Message(id = 2, value = "Cannot invalidate %s")
    void cannotInvalidateZip(String name);

    @Message(id = Message.NONE, value = "Conflicts detected")
    String detectedConflicts();

    @Message(id = Message.NONE, value = "failed to resolve a jboss.home.dir use the --distribution attribute to point to a valid installation")
    IllegalStateException cliFailedToResolveDistribution();

    @Message(id = Message.NONE, value ="No layers directory found at %s")
    IllegalStateException installationNoLayersConfigFound(String path);

    @Message(id = Message.NONE, value = "Cannot find layer '%s' under directory %s")
    IllegalStateException installationMissingLayer(String layer, String path);

    @Message(id = Message.NONE, value = "no associated module or bundle repository with layer '%s'")
    IllegalStateException installationInvalidLayerConfiguration(String layerName);

    @Message(id = Message.NONE, value = "Duplicate %s '%s'")
    IllegalStateException installationDuplicateLayer(String type, String layer);

    @Message(id = Message.NONE, value = "Not a directory %s")
    IllegalStateException notADirectory(String path);

    @Message(id = Message.NONE, value = "patch types don't match")
    IllegalStateException patchTypesDontMatch();

    @Message(id = Message.NONE, value = "invalid rollback information")
    PatchingException invalidRollbackInformation();

    // User related errors

    @Message(id = 3, value = "Patch does not apply - expected (%s), but was (%s)")
    PatchingException doesNotApply(String appliesTo, String version);

    @Message(id = 4, value = "Failed to delete (%s)")
    IOException failedToDelete(String path);

    @Message(id = 5, value = "Failed to create directory (%s)")
    IOException cannotCreateDirectory(String path);

//    /**
//     * A message indicating the argument, represented by the {@code arg} parameter, expected an additional argument.
//     *
//     * @param arg the argument that expects an additional argument.
//     *
//     * @return the message.
//     */
//    @Message(id = 6, value = "Argument expected for option %s")
//    String argumentExpected(String arg);

//    @Message(id = 7, value = "Missing required argument(s): %s")
//    String missingRequiredArgs(Set<String> missing);

    @Message(id = 8, value = "File at path specified by argument %s does not exist")
    String fileDoesNotExist(String arg);

//    @Message(id = 9, value = "File at path specified by argument %s is not a directory")
//    String fileIsNotADirectory(String arg);

//    @Message(id = 10, value = "File at path specified by argument %s is a directory")
//    String fileIsADirectory(String arg);

    @Message(id = 11, value = "Cannot rollback patch (%s)")
    PatchingException cannotRollbackPatch(String id);

    @Message(id = 12, value = "Patch '%s' already applied")
    PatchingException alreadyApplied(String patchId);

    @Message(id = 13, value = "There is no layer called %s installed")
    PatchingException noSuchLayer(String name);

    @Message(id = 14, value = "Failed to resolve a valid patch descriptor for %s %s")
    PatchingException failedToResolvePatch(String product, String version);

    @Message(id = 15, value = "Requires patch '%s'")
    PatchingException requiresPatch(String patchId);

    @Message(id = 16, value = "Patch is incompatible with patch '%s'")
    PatchingException incompatiblePatch(String patchId);

    @Message(id = 17, value = "Conflicts detected")
    ContentConflictsException conflictsDetected(@Param Collection<ContentItem> conflicts);

    @Message(id = 18, value = "copied content does not match expected hash for item: %s")
    SyncFailedException wrongCopiedContent(ContentItem item);

    @Message(id = 19, value = "invalid patch name '%s'")
    IllegalArgumentException illegalPatchName(String name);

    @Message(id = 20, value = "Cannot rollback. No patches applied.")
    IllegalArgumentException noPatchesApplied();

    @Message(id = 21, value = "Patch '%s' not found in history.")
    PatchingException patchNotFoundInHistory(String patchId);

//    @Message(id = 22, value = "Cannot complete operation. Patch '%s' is currently active")
//    OperationFailedException patchActive(String patchId);

    @Message(id = 23, value = "Failed to show history of patches")
    OperationFailedException failedToShowHistory(@Cause Throwable cause);

    @Message(id = 24, value = "Unable to apply or rollback a patch when the server is in a restart-required state.")
    OperationFailedException serverRequiresRestart();

    @Message(id = 25, value = "failed to load identity info")
    String failedToLoadIdentity();

    @Message(id = 26, value = "No more patches")
    String noMorePatches();

    @Message(id = 27, value = "No patch history %s")
    String noPatchHistory(String path);

    @Message(id = 28, value = "Patch is missing file %s")
    String patchIsMissingFile(String path);

    @Message(id = 29, value = "File is not readable %s")
    String fileIsNotReadable(String path);

    @Message(id = 30, value = "Layer not found %s")
    String layerNotFound(String name);

    @LogMessage(level = ERROR)
    @Message(id = 31, value = "failed to undo change for: '%s'")
    void failedToUndoChange(String name);

    @Message(id = 32, value = "missing: '%s'")
    String missingArtifact(PatchingArtifact.ArtifactState state);

    @Message(id = 33, value = "inconsistent state: '%s'")
    String inconsistentArtifact(PatchingArtifact.ArtifactState state);

    @Message(id = 34, value = "in error: '%s'")
    String artifactInError(PatchingArtifact.ArtifactState state);

    @LogMessage(level = WARN)
    @Message(id = 35, value = "Cannot rename file %s")
    void cannotRenameFile(String name);

    @Message(id = 36, value = "Cannot process backup by renaming file %s")
    IllegalStateException cannotRenameFileDuringBackup(String name);

    @Message(id = 37, value = "Cannot process restore by renaming file %s")
    IllegalStateException cannotRenameFileDuringRestore(String name);

    @Message(id = 38, value = "Duplicate element patch-id (%s)")
    IllegalStateException duplicateElementPatchId(String id);

    @Message(id = 39, value = "Requested %s version %s did not match the installed version %s")
    String productVersionDidNotMatchInstalled(String product, String expected, String installed);

    @Message(id = 40, value = "failed to load %s info")
    String failedToLoadInfo(String name);

    @Message(id = 41, value = "Patch %s found in more than one stream: %s and %s")
    String patchIdFoundInMoreThanOneStream(String id, String stream1, String stream2);

    @Message(id = 42, value = "Patch bundle is empty")
    String patchBundleIsEmpty();

    @Message(id = 43, value = "Content item type is missing in '%s'")
    PatchingException contentItemTypeMissing(String condition);

    @Message(id = 44, value = "Unsupported content type '%s'")
    String unsupportedContentType(String type);

    @Message(id = 45, value = "Unrecognized condition format '%s'")
    PatchingException unrecognizedConditionFormat(String condition);

    @Message(id = 46, value = "Cannot copy files to temporary directory %s: %s. Note that '-Djava.io.tmpdir' switch can be used to set different temporary directory.")
    PatchingException cannotCopyFilesToTempDir(String tempDir, String reason, @Cause Throwable cause);

    @Message(id = 47, value = "Cannot copy files from %s to %s: %s")
    IOException cannotCopyFiles(String from, String to, String reason, @Cause Throwable cause);

    @LogMessage(level = ERROR)
    @Message(id = 48, value = "Error when restoring file[%s] - %s")
    void deleteRollbackError(String path, String message);

    @Message(id = 49, value = "Some backup files were not removed.")
    IOException failedToDeleteBackup();

    @LogMessage(level = Level.INFO)
    @Message(id = 50, value = "%s cumulative patch ID is: %s, one-off patches include: %s")
    void logPatchingInfo(String identityName, String cp, String patches);

    @Message(id = 51, value = "Invalid zip file. Found an entry that resolves to a path outside of the patch directory: %s")
    IOException entryOutsideOfPatchDirectory(String path);
}
