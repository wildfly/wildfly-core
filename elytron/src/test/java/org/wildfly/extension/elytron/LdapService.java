/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.protocol.shared.transport.Transport;

/**
 * Wrapper around ApacheDS
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class LdapService implements Closeable {

    private final DirectoryService directoryService;
    private final Collection<LdapServer> servers;

    private LdapService(final DirectoryService directoryService, final Collection<LdapServer> servers) {
        this.directoryService = directoryService;
        this.servers = servers;
    }

    @Override
    public void close() throws IOException {
        for (LdapServer current : servers) {
            current.stop();
        }
        try {
            directoryService.shutdown();
        } catch (Exception e) {
            throw new IOException("Unable to shut down DirectoryService", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean started = false;
        private File workingDir = null;
        private DirectoryServiceFactory directoryServiceFactory;
        private DirectoryService directoryService;
        private List<LdapServer> servers = new LinkedList<LdapServer>();

        Builder() {
        }

        /**
         * Set the working dir that will be used by the directory server.
         *
         * WARNING - Any contents in this directory will be deleted.
         *
         * @param workingDir - The working dir for the directory server to use.
         * @return This Builder for subsequent changes.
         */
        public Builder setWorkingDir(final File workingDir) {
            assertNotStarted();

            this.workingDir = workingDir;

            return this;
        }

        /**
         * Create the core directory service.
         *
         * @param name - The name of the directory service.
         * @return This Builder for subsequent changes.
         */
        public Builder createDirectoryService(final String name) throws Exception {
            if (directoryService != null) {
                throw new IllegalStateException("Directory service already created.");
            }

            initWorkingDir();

            directoryServiceFactory = new DefaultDirectoryServiceFactory();
            directoryServiceFactory.init(name);

            DirectoryService directoryService = directoryServiceFactory.getDirectoryService();

            directoryService.getChangeLog().setEnabled(false);

            this.directoryService = directoryService;

            return this;
        }

        /**
         * Add a new partition to the directory server.
         *
         * @param partitionName - The name of the partition.
         * @param indexes - The attributes to index.
         * @return This Builder for subsequent changes.
         */
        public Builder addPartition(final String id, final String partitionName, final int indexSize, final String ... indexes) throws Exception {
            assertNotStarted();
            if (directoryService == null) {
                throw new IllegalStateException("The Directory service has not been created.");
            }

            SchemaManager schemaManager = directoryService.getSchemaManager();
            PartitionFactory partitionFactory = directoryServiceFactory.getPartitionFactory();
            Partition partition = partitionFactory.createPartition(schemaManager, directoryService.getDnFactory(), id, partitionName, 1000, workingDir);
            for (String current : indexes) {
                partitionFactory.addIndex(partition, current, indexSize);
            }
            partition.setCacheService(directoryService.getCacheService());
            partition.initialize();
            directoryService.addPartition(partition);

            return this;
        }

        /**
         * Import all of the entries from the provided LDIF stream.
         *
         * Note: The whole stream is read
         *
         * @param ldif - Stream containing the LDIF.
         * @return This Builder for subsequent changes.
         */
        public Builder importLdif(final InputStream ldif) throws Exception {
            assertNotStarted();
            if (directoryService == null) {
                throw new IllegalStateException("The Directory service has not been created.");
            }
            CoreSession adminSession = directoryService.getAdminSession();
            SchemaManager schemaManager = directoryService.getSchemaManager();

            LdifReader ldifReader = new LdifReader(ldif);
            for (LdifEntry ldifEntry : ldifReader) {
                adminSession.add(new DefaultEntry(schemaManager, ldifEntry.getEntry()));
            }
            ldifReader.close();
            ldif.close();

            return this;
        }

        /**
         * Adds a TCP server to the directory service.
         *
         * Note: The TCP server is not started until start() is called on this Builder.
         *
         * @param serviceName - The name of this server.
         * @param hostName - The host name to listen on.
         * @param port - The port to listen on.
         * @return This Builder for subsequent changes.
         */
        public Builder addTcpServer(final String serviceName, final String hostName, final int port, final String keyStore, final String keyStorePassword) throws URISyntaxException {
            assertNotStarted();
            if (directoryService == null) {
                throw new IllegalStateException("The Directory service has not been created.");
            }

            LdapServer server = new LdapServer();
            server.setServiceName(serviceName);
            Transport ldaps = new TcpTransport( hostName, port, 3, 5 );
            ldaps.enableSSL(true);
            server.addTransports(ldaps);
            server.setKeystoreFile(new File(getClass().getResource(keyStore).getFile()).getAbsolutePath());
            server.setCertificatePassword(keyStorePassword);
            server.setDirectoryService(directoryService);
            servers.add(server);

            return this;
        }

        public LdapService start() throws Exception {
            assertNotStarted();
            started = true;

            for (LdapServer current : servers) {
                current.start();
            }

            return new LdapService(directoryService, servers);
        }

        private void assertNotStarted() {
            if (started) {
                throw new IllegalStateException("Already started.");
            }
        }

        private void initWorkingDir() {
            if (workingDir == null) {
                throw new IllegalStateException("No working dir.");
            }

            if (workingDir.exists() == false) {
                if (workingDir.mkdirs() == false) {
                    throw new IllegalStateException("Unable to create working dir.");
                }
            }
            emptyDir(workingDir);
        }

        private void emptyDir(final File dir) {
            for (File current : dir.listFiles()) {
                if (current.delete() == false) {
                    try {
                        throw new IllegalStateException(String.format("Unable to delete file '%s' from working dir '%s'.",
                                current.getName(), workingDir.getCanonicalPath()));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

    }

}
