/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface InstMgrConstants {
    Path CUSTOM_PATCH_SUBPATH = Paths.get(".installation")
            .resolve("installation-manager")
            .resolve("custom-patch");
    Path PREPARED_SERVER_SUBPATH = Paths.get("installation-manager")
            .resolve("prepared-server");

    String CHANNEL = "channel";
    String CHANNELS = "channels";
    String CHANNEL_NAME = "name";
    String CUSTOM_PATCH_FILE = "custom-patch-file";
    String DEFAULT_CUSTOM_CHANNEL_NAME_PREFIX = "custom-channel-";
    String HISTORY_DETAILED_ARTIFACT_NAME = "name";
    String HISTORY_DETAILED_ARTIFACT_NEW_VERSION = "new-version";
    String HISTORY_DETAILED_ARTIFACT_OLD_VERSION = "old-version";
    String HISTORY_DETAILED_ARTIFACT_STATUS = "status";
    String HISTORY_DETAILED_CHANNEL_MANIFEST = "manifest";
    String HISTORY_DETAILED_CHANNEL_NAME = "channel-name";
    String HISTORY_DETAILED_CHANNEL_NEW_MANIFEST = "new-manifest";
    String HISTORY_DETAILED_CHANNEL_NEW_REPOSITORY = "new-repository";
    String HISTORY_DETAILED_CHANNEL_OLD_MANIFEST = "old-manifest";
    String HISTORY_DETAILED_CHANNEL_OLD_REPOSITORY = "old-repository";
    String HISTORY_DETAILED_CHANNEL_REPOSITORIES = "repositories";
    String HISTORY_DETAILED_CHANNEL_STATUS = "status";
    String HISTORY_RESULT_DESCRIPTION = "description";
    String HISTORY_RESULT_DETAILED_ARTIFACT_CHANGES = "artifact-changes";
    String HISTORY_RESULT_DETAILED_CHANNEL_CHANGES = "channel-changes";
    String HISTORY_RESULT_HASH = "hash";
    String HISTORY_RESULT_TIMESTAMP = "timestamp";
    String HISTORY_RESULT_TYPE = "type";
    String INSTALLATION_MANAGER = "installation-manager";
    String LIST_UPDATES_ARTIFACT_NAME = "name";
    String LIST_UPDATES_NEW_VERSION = "new-version";
    String LIST_UPDATES_OLD_VERSION = "old-version";
    String LIST_UPDATES_RESULT = "updates";
    String LIST_UPDATES_STATUS = "status";
    String LIST_UPDATES_WORK_DIR = "work-dir";
    String LOCAL_CACHE = "local-cache";
    String MANIFEST = "manifest";
    String MANIFEST_GAV = "gav";
    String MANIFEST_URL = "url";
    String MAVEN_REPO_DIR_NAME_IN_ZIP_FILES = "maven-repository";
    String MAVEN_REPO_FILE = "maven-repo-file";
    String MAVEN_REPO_FILES = "maven-repo-files";
    String NO_RESOLVE_LOCAL_CACHE = "no-resolve-local-cache";
    String OFFLINE = "offline";
    String REPOSITORIES = "repositories";
    String REPOSITORY = "repository";
    String REPOSITORY_ID = "id";
    String REPOSITORY_URL = "url";
    String REVISION = "revision";
    String TOOL_NAME = "installer";
    String INTERNAL_REPO_PREFIX = "repo-";
}
