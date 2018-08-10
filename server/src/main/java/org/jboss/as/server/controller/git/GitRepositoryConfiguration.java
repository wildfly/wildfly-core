/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.controller.git;

import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import org.jboss.as.server.logging.ServerLogger;

/**
 * Encapsulate the git configuration used for configuration history.
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class GitRepositoryConfiguration {
    private final Path basePath;
    private final String repository;
    private final String branch;
    private final URI authenticationConfig;
    private final Set<String> ignored;


    private GitRepositoryConfiguration(Path basePath, String repository, String branch, URI authenticationConfig, Set<String> ignored) {
        this.basePath = basePath;
        this.repository = repository;
        this.branch = branch;
        this.authenticationConfig = authenticationConfig;
        this.ignored = ignored;
    }

    public Path getBasePath() {
        return basePath;
    }

    public String getRepository() {
        return repository;
    }

    public String getBranch() {
        return branch;
    }

    public URI getAuthenticationConfig() {
        return authenticationConfig;
    }

    public Set<String> getIgnored() {
        return ignored;
    }

    public boolean isLocal() {
        return "local".equals(repository);
    }

    public static class Builder {

        private Path basePath;
        private String repository;
        private String branch = MASTER;
        private URI authenticationConfig;
        private Set<String> ignored;

        private Builder() {
        }

        public static Builder getInstance() {
            return new Builder();
        }

        public Builder setBasePath(Path basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder setRepository(String repository) {
            this.repository = repository;
            return this;
        }

        public Builder setBranch(String branch) {
            if(branch != null) {
                this.branch = branch;
            }
            return this;
        }

        public Builder setAuthenticationConfig(URI authenticationConfig) {
            this.authenticationConfig = authenticationConfig;
            return this;
        }

        public Builder setAuthenticationConfig(String authConfiguration) {
            if (authConfiguration != null) {
                try {
                    authenticationConfig = new URI(authConfiguration);
                } catch (URISyntaxException ex) {
                    ServerLogger.ROOT_LOGGER.errorUsingGit(ex, ex.getMessage());
                }
            }
            return this;
        }

        public Builder setIgnored(Set<String> ignored) {
            this.ignored = ignored;
            return this;
        }

        public GitRepositoryConfiguration build() {
            if (repository == null || repository.isEmpty()) {
                if (Files.exists(basePath.resolve(DOT_GIT))) {
                    this.repository = "local";
                } else {
                    return null;
                }
            }
            if(this.ignored == null) {
                this.ignored =  Collections.emptySet();
            }
            return new GitRepositoryConfiguration(basePath, repository, branch, authenticationConfig, ignored);
        }
    }
    }
