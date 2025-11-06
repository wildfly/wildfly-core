/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.instmgr.cli;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.aesh.command.CommandException;
import org.jboss.as.cli.Util;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.instmgr.InstMgrConstants;
import org.wildfly.core.instmgr.InstMgrListUpdatesHandler;

public class ListUpdatesAction extends AbstractInstMgrCommand {
    private final List<File> mavenRepoFiles;
    private final List<String> repositories;
    private final List<String> manifestVersions;
    private final Path localCache;
    private final Boolean noResolveLocalCache;
    private final Boolean useDefaultLocalCache;
    private final boolean offline;
    private final ModelNode headers;

    public ListUpdatesAction(Builder builder) {
        this.mavenRepoFiles = builder.mavenRepoFiles;
        this.repositories = builder.repositories;
        this.manifestVersions = builder.manifestVersions;
        this.localCache = builder.localCache;
        this.noResolveLocalCache = builder.noResolveLocalCache;
        this.offline = builder.offline;
        this.headers = builder.headers;
        this.useDefaultLocalCache = builder.useDefaultLocalCache;
    }

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrListUpdatesHandler.DEFINITION.getName());

        if (mavenRepoFiles != null && !mavenRepoFiles.isEmpty()) {
            final ModelNode filesMn = new ModelNode().addEmptyList();
            for (int i = 0; i < mavenRepoFiles.size(); i++) {
                filesMn.add(i);
                operationBuilder.addFileAsAttachment(mavenRepoFiles.get(i));
            }
            op.get(InstMgrConstants.MAVEN_REPO_FILES).set(filesMn);
        }

        addRepositoriesToModelNode(op, this.repositories);
        addManifestVersionsToModelNode(op, this.manifestVersions);

        if (localCache != null) {
            op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.normalize().toAbsolutePath().toString());
        }

        if (useDefaultLocalCache != null) {
            op.get(InstMgrConstants.USE_DEFAULT_LOCAL_CACHE).set(useDefaultLocalCache);
        }

        if (noResolveLocalCache != null) {
            op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        }

        op.get(InstMgrConstants.OFFLINE).set(offline);

        if (this.headers != null && headers.isDefined()) {
            op.get(Util.OPERATION_HEADERS).set(headers);
        }

        return operationBuilder.build();
    }

    public static class Builder {
        public boolean offline;
        private ModelNode headers;
        private List<File> mavenRepoFiles;
        private List<String> repositories;
        private List<String> manifestVersions;
        private Path localCache;

        private Boolean noResolveLocalCache;
        private Boolean useDefaultLocalCache;

        public Builder() {
            this.repositories = new ArrayList<>();
            this.manifestVersions = new ArrayList<>();
            this.offline = false;
            this.mavenRepoFiles = new ArrayList<>();
        }

        public Builder setMavenRepoFiles(List<File> mavenRepoFiles) {
            if (mavenRepoFiles != null) {
                this.mavenRepoFiles.addAll(mavenRepoFiles);
            }
            return this;
        }

        public Builder setRepositories(List<String> repositories) {
            if (repositories != null) {
                this.repositories.addAll(repositories);
            }
            return this;
        }

        public Builder setManifestVersions(List<String> manifestVersions) {
            if (manifestVersions != null) {
                this.manifestVersions = manifestVersions;
            }
            return this;
        }

        public Builder setLocalCache(File localCache) {
            if (localCache != null) {
                this.localCache = localCache.toPath();
            }
            return this;
        }

        public Builder setNoResolveLocalCache(Boolean noResolveLocalCache) {
            this.noResolveLocalCache = noResolveLocalCache;
            return this;
        }

        public Builder setUseDefaultLocalCache(Boolean useDefaultLocalCache) {
            this.useDefaultLocalCache = useDefaultLocalCache;
            return this;
        }

        public Builder setOffline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder setHeaders(ModelNode headers) {
            this.headers = headers;
            return this;
        }

        public ListUpdatesAction build() {
            return new ListUpdatesAction(this);
        }
    }
}
