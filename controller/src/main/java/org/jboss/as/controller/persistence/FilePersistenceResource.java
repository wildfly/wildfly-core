/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.File;
import java.io.InputStream;

import org.jboss.dmr.ModelNode;

/**
 * {@link ConfigurationPersister.PersistenceResource} that persists to a file upon commit.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class FilePersistenceResource extends AbstractFilePersistenceResource {

    protected final File fileName;

    FilePersistenceResource(final ModelNode model, final File fileName, final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
        super(model, persister);
        this.fileName = fileName;

    }

    @Override
    protected void doCommit(InputStream in) {
        final File tempFileName = FilePersistenceUtils.createTempFile(fileName);
        try {
            FilePersistenceUtils.writeToTempFile(in, tempFileName, fileName);
            FilePersistenceUtils.moveTempFileToMain(tempFileName, fileName);
        } catch (Exception e) {
            MGMT_OP_LOGGER.failedToStoreConfiguration(e, fileName.getName());
        } finally {
            if (tempFileName.exists() && !tempFileName.delete()) {
                MGMT_OP_LOGGER.cannotDeleteTempFile(tempFileName.getName());
                tempFileName.deleteOnExit();
            }
        }
    }
}
