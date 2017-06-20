/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.security.common.elytron;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 * Configuration for filesystem-realms Elytron resource.
 *
 * @author Josef Cacek
 */
public class FileSystemRealm extends AbstractUserRolesCapableElement {

    private final CliPath path;
    private final Integer level;
    private final File tempFolder;

    private FileSystemRealm(Builder builder) {
        super(builder);
        if (builder.path != null) {
            tempFolder = null;
            this.path = builder.path;
        } else {
            try {
                tempFolder = Files.createTempDirectory("ely-" + getName()).toFile();
            } catch (IOException e) {
                throw new RuntimeException("Unable to create temporary folder", e);
            }
            this.path = CliPath.builder().withPath(tempFolder.getAbsolutePath()).build();
        }
        level = builder.level;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        final String levelStr = level == null ? "" : ("level=" + level);
        cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add(%s, %s)", name, path.asString(), levelStr));
        for (UserWithRoles user : getUsersWithRoles()) {
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", name, user.getName()));
            cli.sendLine(
                    String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                            name, user.getName(), user.getPassword()));
            if (!user.getRoles().isEmpty()) {
                cli.sendLine(String.format(
                        "/subsystem=elytron/filesystem-realm=%s:add-identity-attribute(identity=%s, name=groups, value=[%s])", name,
                        user.getName(), String.join(",", user.getRoles())));
            }
        }
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:remove()", name));
        FileUtils.deleteQuietly(tempFolder);
    }

    /**
     * Creates builder to build {@link FileSystemRealm}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link FileSystemRealm}.
     */
    public static final class Builder extends AbstractUserRolesCapableElement.Builder<Builder> {
        private CliPath path;
        private Integer level;

        private Builder() {
        }

        public Builder withPath(CliPath path) {
            this.path = path;
            return this;
        }

        public Builder withLevel(Integer level) {
            this.level = level;
            return this;
        }

        public FileSystemRealm build() {
            return new FileSystemRealm(this);
        }

        @Override
        public Builder self() {
            return this;
        }
    }
}
