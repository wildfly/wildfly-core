/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.suites;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.schema.LdapComparator;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.comparators.NormalizingComparator;
import org.apache.directory.api.ldap.model.schema.registries.ComparatorRegistry;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.loader.JarLdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.factory.AvlPartitionFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.i18n.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for a fast (mostly in-memory-only) ApacheDS DirectoryService. Use only for tests!!
 *
 * @author Josef Cacek
 */
public class InMemoryDirectoryServiceFactory implements DirectoryServiceFactory {

    private static Logger LOG = LoggerFactory.getLogger(InMemoryDirectoryServiceFactory.class);

    private final DirectoryService directoryService;
    private final PartitionFactory partitionFactory;

    /**
     * Default constructor which creates {@link DefaultDirectoryService} instance and configures {@link AvlPartitionFactory} as
     * the {@link PartitionFactory} implementation.
     */
    public InMemoryDirectoryServiceFactory() {
        try {
            directoryService = new DefaultDirectoryService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        directoryService.setShutdownHookEnabled(false);
        partitionFactory = new AvlPartitionFactory();
    }

    /**
     * Constructor which uses provided {@link DirectoryService} and {@link PartitionFactory} implementations.
     *
     * @param directoryService must be not-<code>null</code>
     * @param partitionFactory must be not-<code>null</code>
     */
    public InMemoryDirectoryServiceFactory(DirectoryService directoryService, PartitionFactory partitionFactory) {
        this.directoryService = directoryService;
        this.partitionFactory = partitionFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(String name) throws Exception {
        if ((directoryService != null) && directoryService.isStarted()) {
            return;
        }

        directoryService.setInstanceId(name);

        // instance layout
        InstanceLayout instanceLayout = new InstanceLayout(System.getProperty("java.io.tmpdir") + "/server-work-" + name);
        if (instanceLayout.getInstanceDirectory().exists()) {
            try {
                FileUtils.deleteDirectory(instanceLayout.getInstanceDirectory());
            } catch (IOException e) {
                LOG.warn("couldn't delete the instance directory before initializing the DirectoryService", e);
            }
        }
        directoryService.setInstanceLayout(instanceLayout);

        // Init the schema
        // SchemaLoader loader = new SingleLdifSchemaLoader();
        SchemaLoader loader = new JarLdifSchemaLoader();
        SchemaManager schemaManager = new DefaultSchemaManager(loader);
        schemaManager.loadAllEnabled();
        ComparatorRegistry comparatorRegistry = schemaManager.getComparatorRegistry();
        for (LdapComparator<?> comparator : comparatorRegistry) {
            if (comparator instanceof NormalizingComparator) {
                ((NormalizingComparator) comparator).setOnServer();
            }
        }
        directoryService.setSchemaManager(schemaManager);
        InMemorySchemaPartition inMemorySchemaPartition = new InMemorySchemaPartition(schemaManager);

        SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
        schemaPartition.setWrappedPartition(inMemorySchemaPartition);
        directoryService.setSchemaPartition(schemaPartition);
        List<Throwable> errors = schemaManager.getErrors();
        if (errors.size() != 0) {
            throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
        }

        // Init system partition
        Partition systemPartition = partitionFactory.createPartition(directoryService.getSchemaManager(),
                directoryService.getDnFactory(), "system", ServerDNConstants.SYSTEM_DN, 500,
                new File(directoryService.getInstanceLayout().getPartitionsDirectory(), "system"));
        systemPartition.setSchemaManager(directoryService.getSchemaManager());
        partitionFactory.addIndex(systemPartition, SchemaConstants.OBJECT_CLASS_AT, 100);
        directoryService.setSystemPartition(systemPartition);

        directoryService.startup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DirectoryService getDirectoryService() throws Exception {
        return directoryService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PartitionFactory getPartitionFactory() throws Exception {
        return partitionFactory;
    }
}
