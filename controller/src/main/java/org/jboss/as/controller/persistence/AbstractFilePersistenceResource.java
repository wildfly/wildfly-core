/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractFilePersistenceResource implements ConfigurationPersister.PersistenceResource {
    private volatile ExposedByteArrayOutputStream marshalled;

    protected AbstractFilePersistenceResource(final ModelNode model, final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
        marshalled = new ExposedByteArrayOutputStream(1024 * 8);
        try {
            try {
                BufferedOutputStream output = new BufferedOutputStream(marshalled);
                persister.marshallAsXml(model, output);
                output.close();
                marshalled.close();
            } finally {
                IoUtils.safeClose(marshalled);
            }
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToMarshalConfiguration(e);
        }
    }

    @Override
    public void commit() {
        if (marshalled == null) {
            throw ControllerLogger.ROOT_LOGGER.rollbackAlreadyInvoked();
        }
        try(InputStream in = getMarshalledInputStream()) {
            doCommit(in);
        } catch (IOException ioex) {
            MGMT_OP_LOGGER.errorf(ioex, ioex.getMessage());
        }
    }

    @Override
    public void rollback() {
        marshalled = null;
    }

    protected InputStream getMarshalledInputStream() {
        return marshalled.getInputStream();
    }

    protected abstract void doCommit(InputStream marshalled);
}
