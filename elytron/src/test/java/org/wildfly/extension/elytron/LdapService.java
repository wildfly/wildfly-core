/*
 * Copyright 2023 Red Hat, Inc.
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
package org.wildfly.extension.elytron;

import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;

import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.ldap.LdapServer;

public class LdapService extends ElytronCommonLdapService {

    private LdapService(final DirectoryService directoryService, final Collection<LdapServer> servers) {
        super(directoryService, servers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ElytronCommonLdapService.Builder {
        Builder() {
            super();
        }

        @Override
        public Builder setWorkingDir(File workingDir) {
            return (Builder) super.setWorkingDir(workingDir);
        }

        @Override
        public Builder createDirectoryService(String name) throws Exception {
            return (Builder) super.createDirectoryService(name);
        }

        @Override
        public Builder addPartition(String id, String partitionName, int indexSize, String... indexes) throws Exception {
            return (Builder) super.addPartition(id, partitionName, indexSize, indexes);
        }

        @Override
        public Builder importLdif(InputStream ldif) throws Exception {
            return (Builder) super.importLdif(ldif);
        }

        @Override
        public Builder addTcpServer(Class<?> testClass, String serviceName, String hostName, int port, String keyStore, String keyStorePassword) throws URISyntaxException {
            return (Builder) super.addTcpServer(testClass, serviceName, hostName, port, keyStore, keyStorePassword);
        }

        @Override
        public LdapService start() throws Exception {
            return (LdapService) super.start();
        }
    }
}
