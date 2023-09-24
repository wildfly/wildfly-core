/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
    private final boolean sign;


    private GitRepositoryConfiguration(Path basePath, String repository, String branch, URI authenticationConfig, Set<String> ignored, boolean sign) {
        this.basePath = basePath;
        this.repository = repository;
        this.branch = branch;
        this.authenticationConfig = authenticationConfig;
        this.ignored = ignored;
        this.sign = sign;
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

    public boolean isSign() {
        return sign;
    }

    public static class Builder {

        private Path basePath;
        private String repository;
        private String branch = MASTER;
        private URI authenticationConfig;
        private Set<String> ignored;
        private boolean sign = false;

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
            return new GitRepositoryConfiguration(basePath, repository, branch, authenticationConfig, ignored, sign);
        }

        public Builder setSign(boolean sign) {
            this.sign = sign;
            return this;
        }
    }
    }
