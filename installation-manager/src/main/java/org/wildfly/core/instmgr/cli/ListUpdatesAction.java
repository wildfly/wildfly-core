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
    private final File mavenRepoFile;
    private final List<String> repositories;
    private final Path localCache;
    private final boolean noResolveLocalCache;
    private final boolean offline;
    private final ModelNode headers;

    public ListUpdatesAction(Builder builder) {
        this.mavenRepoFile = builder.mavenRepoFile;
        this.repositories = builder.repositories;
        this.localCache = builder.localCache;
        this.noResolveLocalCache = builder.noResolveLocalCache;
        this.offline = builder.offline;
        this.headers = builder.headers;
    }

    @Override
    protected Operation buildOperation() throws CommandException {
        final ModelNode op = new ModelNode();
        final OperationBuilder operationBuilder = OperationBuilder.create(op);

        op.get(OP).set(InstMgrListUpdatesHandler.DEFINITION.getName());

        if (mavenRepoFile != null) {
            op.get(InstMgrConstants.MAVEN_REPO_FILE).set(0);
            operationBuilder.addFileAsAttachment(mavenRepoFile);
        }

        addRepositoriesToModelNode(op, this.repositories);

        if (localCache != null) {
            op.get(InstMgrConstants.LOCAL_CACHE).set(localCache.normalize().toAbsolutePath().toString());
        }

        op.get(InstMgrConstants.NO_RESOLVE_LOCAL_CACHE).set(noResolveLocalCache);
        op.get(InstMgrConstants.OFFLINE).set(offline);

        if (this.headers != null && headers.isDefined()) {
            op.get(Util.OPERATION_HEADERS).set(headers);
        }

        return operationBuilder.build();
    }

    public static class Builder {
        public boolean offline;
        private ModelNode headers;
        private File mavenRepoFile;
        private List<String> repositories;
        private Path localCache;

        private boolean noResolveLocalCache;

        public Builder() {
            this.repositories = new ArrayList<>();
            this.offline = false;
            this.noResolveLocalCache = false;
        }

        public Builder setMavenRepoFile(File mavenRepoFile) {
            if (mavenRepoFile != null) {
                this.mavenRepoFile = mavenRepoFile;
            }
            return this;
        }

        public Builder setRepositories(List<String> repositories) {
            if (repositories != null) {
                this.repositories = new ArrayList<>(repositories);
            }
            return this;
        }

        public Builder setLocalCache(File localCache) {
            if (localCache != null) {
                this.localCache = localCache.toPath();
            }
            return this;
        }

        public Builder setNoResolveLocalCache(boolean noResolveLocalCache) {
            this.noResolveLocalCache = noResolveLocalCache;
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
