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

import java.io.IOException;
import java.nio.file.Path;

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
    InstMgrLogger ROOT_LOGGER = Logger.getMessageLogger(InstMgrLogger.class, " org.wildfly.core.installationmanager");

    @Message(id = 1, value = "No known attribute %s")
    OperationFailedException unknownAttribute(String asString);

    @Message(id = 2, value = "Zip entry %s is outside of the target dir %s")
    IOException zipEntryOutsideOfTarget(String entry, String target);

    @Message(id = 3, value = "The Zip archive format is invalid. The '%s' directory cannot be found as a second-level entry within the unloaded Zip file.")
    Exception invalidZipEntry(String directory);

    @Message(id = 4, value = "There is an installation prepared and ready to be applied. The current prepared installation can be discarded by using the 'clean' operation.")
    OperationFailedException serverAlreadyPrepared();

    @Message(id = 5, value = "Invalid status change found for the artifact: '%s'")
    RuntimeException unexpectedArtifactChange(String artifact);

    @Message(id = 6, value = "Invalid status change found for the configuration change: '%s'")
    RuntimeException unexpectedConfigurationChange(String channel);

    @Message(id = 7, value = "Channel name is mandatory")
    OperationFailedException missingChannelName();

    @Message(id = 8, value = "No repositories are defined in the '%s' channel.")
    OperationFailedException noChannelRepositoriesDefined(String channelName);

    @Message(id = 9, value = "The '%s' channel's repository does not have any defined URL.")
    OperationFailedException noChannelRepositoryURLDefined(String channelName);

    @Message(id = 10, value = "The repository URL '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelRepositoryURL(String repoUrl, String channelName);

    @Message(id = 11, value = "The '%s' channel's repository does not have any defined ID")
    OperationFailedException noChannelRepositoryIDDefined(String channelName);

    @Message(id = 12, value = "The GAV manifest '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelManifestGAV(String gav, String channelName);

    @Message(id = 13, value = "The URL manifest '%s' for '%s' channel is invalid.")
    OperationFailedException invalidChannelManifestURL(String url, String channelName);

    @Message(id = 14, value = "'local-cache' cannot be used when 'no-resolve-local-cache' is enabled.")
    OperationFailedException localCacheWithNoResolveLocalCache();

    @Message(id = 15, value = "You cannot use 'maven-repo-file' in conjunction with 'repositories' because they are mutually exclusive.")
    OperationFailedException mavenRepoFileWithRepositories();

    @Message(id = 16, value = "Repository does not have any defined URL.")
    OperationFailedException noRepositoryURLDefined();

    @Message(id = 17, value = "Repository does not have any defined ID.")
    OperationFailedException noRepositoryIDDefined();

    @Message(id = 18, value = "Invalid format for the repository URL: '%s'")
    OperationFailedException invalidRepositoryURL(String repoUrl);

    @Message(id = 19, value = "You cannot use 'work-dir' in conjunction with 'repositories' or 'maven-repo-file' because they are mutually exclusive.")
    OperationFailedException workDirWithMavenRepoFileOrRepositories();

    @Message(id = 20, value = "Channel with name '%s' cannot be found.")
    OperationFailedException channelNameNotFound(String channelName);

    @Message(id = 21, value = "The path '%s' does not exit.")
    OperationFailedException exportPathDoesNotExist(Path path);

    @Message(id = 22, value = "The path '%s' is not writable.")
    OperationFailedException exportPathIsNotWritable(Path path);

    @Message(id = 23, value = "Could not find a path called '%s'")
    OperationFailedException pathEntryNotFound(String relativeToPath);

    @Message(id = 24, value = "The GAV manifest '%s' is invalid.")
    OperationFailedException invalidManifestGAV(String gav);

    @Message(id = 25, value = "The URL manifest '%s' is invalid.")
    OperationFailedException invalidManifestURL(String url);
}
