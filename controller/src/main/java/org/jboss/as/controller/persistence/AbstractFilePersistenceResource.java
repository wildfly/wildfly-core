/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.controller.persistence;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.xnio.IoUtils.safeClose;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractFilePersistenceResource implements ConfigurationPersister.PersistenceResource {
    private volatile ExposedByteArrayOutputStream marshalled;

    protected AbstractFilePersistenceResource(final ModelNode model, final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
        marshalled = new ExposedByteArrayOutputStream(1024 * 8);
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(marshalled);
            persister.marshallAsXml(model, output);
        } catch (Exception e) {
            throw ControllerLogger.ROOT_LOGGER.failedToMarshalConfiguration(e);
        } finally {
            safeClose(output);
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
