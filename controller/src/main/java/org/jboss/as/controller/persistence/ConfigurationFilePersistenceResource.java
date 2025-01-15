/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import org.jboss.dmr.ModelNode;

/**
 * {@link ConfigurationPersister.PersistenceResource} that persists to a configuration file upon commit, also
 * ensuring proper backup copies are made.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ConfigurationFilePersistenceResource extends AbstractFilePersistenceResource {

    private final ConfigurationFile configurationFile;
    protected final File fileName;


    ConfigurationFilePersistenceResource(final ModelNode model, final ConfigurationFile configurationFile,
                                         final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
        super(model, persister);
        this.configurationFile = configurationFile;
        this.fileName = configurationFile.getMainFile();
    }

    @Override
    protected void doCommit(InputStream in) {
        final File tempFileName;

        if ( FilePersistenceUtils.isParentFolderWritable(fileName) ){
            tempFileName = FilePersistenceUtils.createTempFile(fileName);
        } else if (Files.isWritable(configurationFile.getConfigurationDir().toPath())) {
            tempFileName = FilePersistenceUtils.createTempFile(configurationFile.getConfigurationDir(), fileName.getName());
        } else {
            tempFileName = FilePersistenceUtils.createTempFile(configurationFile.getConfigurationTmpDir(), fileName.getName());
        }

        try {
            try {
                FilePersistenceUtils.writeToTempFile(in, tempFileName, fileName);
            } catch (Exception e) {
                MGMT_OP_LOGGER.failedToStoreConfiguration(e, fileName.getName());
                return;
            }
            try {
                configurationFile.backup();
            } finally {
                configurationFile.commitTempFile(tempFileName);
            }
            configurationFile.fileWritten();
        } catch (ConfigurationPersistenceException e) {
           MGMT_OP_LOGGER.errorf(e, e.toString());
        } finally {
            if (tempFileName.exists() && !tempFileName.delete()) {
                MGMT_OP_LOGGER.cannotDeleteTempFile(tempFileName.getName());
                tempFileName.deleteOnExit();
            }
        }
    }
}
