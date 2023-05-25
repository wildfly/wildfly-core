/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.instmgr.logging;

import java.util.zip.ZipException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Installation Manager logger.
 */
@SuppressWarnings("DefaultAnnotationParam")
@MessageLogger(projectCode = "WFLYIM", length = 4)
public interface InstMgrLogger extends BasicLogger {
    InstMgrLogger ROOT_LOGGER = Logger.getMessageLogger(InstMgrLogger.class, "org.wildfly.core.installationmanager");

    @Message(id = 1, value = "There is an installation prepared and ready to be applied. The current prepared installation can be discarded by using the 'clean' operation.")
    OperationFailedException serverAlreadyPrepared();

    @Message(id = 2, value = "Invalid status change found for the artifact: '%s'")
    RuntimeException unexpectedArtifactChange(String artifact);

    @Message(id = 3, value = "Invalid status change found for the configuration change: '%s'")
    RuntimeException unexpectedConfigurationChange(String channel);

    @Message(id = 4, value = "Channel name is mandatory.")
    OperationFailedException missingChannelName();

    @Message(id = 5, value = "No repositories have been defined in the '%s' channel.")
    OperationFailedException noChannelRepositoriesDefined(String channelName);

    @Message(id = 6, value = "The '%s' repository in the channel does not have its URL defined.")
    OperationFailedException noChannelRepositoryURLDefined(String channelName);

    @Message(id = 7, value = "The repository URL '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelRepositoryURL(String repoUrl, String channelName);

    @Message(id = 8, value = "The '%s' repository in the channel does not have its ID defined.")
    OperationFailedException noChannelRepositoryIDDefined(String channelName);

    @Message(id = 9, value = "The manifest GAV coordinate '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelManifestGAV(String gav, String channelName);

    @Message(id = 10, value = "The manifest URL '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelManifestURL(String url, String channelName);

    @Message(id = 11, value = "You cannot use the 'local-cache' option when the 'no-resolve-local-cache' option is enabled.")
    OperationFailedException localCacheWithNoResolveLocalCache();

    @Message(id = 12, value = "You cannot use the 'maven-repo-file' option with the 'repositories' option because they are mutually exclusive.")
    OperationFailedException mavenRepoFileWithRepositories();

    @Message(id = 13, value = "Invalid format for the repository URL: '%s'")
    OperationFailedException invalidRepositoryURL(String repoUrl);

    @Message(id = 14, value = "You cannot use the 'work-dir' option with the 'repositories' or 'maven-repo-file' options because they are mutually exclusive.")
    OperationFailedException workDirWithMavenRepoFileOrRepositories();

    @Message(id = 15, value = "Channel with name '%s' cannot be found.")
    OperationFailedException channelNameNotFound(String channelName);

    @Message(id = 16, value = "The manifest maven coordinates for '%s' are invalid. The expected maven coordinates for this manifest are GA (GroupId:ArtifactId).")
    OperationFailedException invalidManifestGAOnly(String ga);

    @Message(id = 17, value = "The manifest maven coordinates for '%s' are invalid. The expected maven coordinates for this manifest are GAV (GroupId:ArtifactId:Version) where Version is optional.")
    OperationFailedException invalidManifestGAV(String gav);

    @Message(id = 18, value = "Installation Manager Service is down.")
    IllegalStateException installationManagerServiceDown();

    @Message(id = 19, value = "Operation has been cancelled.")
    OperationFailedException operationCancelled();

    @Message(id = 20, value = "No custom patches installed found for the specified manifest maven coordinates: '%s'")
    OperationFailedException noCustomPatchFound(String manifestGA);

    ////////////////////////////////////////////////
    // Messages without IDs

    @Message(id = Message.NONE, value = "Zip entry %s is outside of the target dir %s.")
    ZipException zipEntryOutsideOfTarget(String entry, String target);

    @Message(id = Message.NONE, value = "The structure of directories and files in the .zip file is invalid. The '%s' directory cannot be found as a second-level entry in the extracted .zip file.")
    ZipException invalidZipEntry(String directory);
}
