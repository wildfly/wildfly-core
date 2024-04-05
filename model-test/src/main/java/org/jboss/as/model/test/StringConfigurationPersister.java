/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * Internal class used to marshall/read the model
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StringConfigurationPersister extends AbstractConfigurationPersister {

    private final List<ModelNode> bootOperations;
    private final boolean persistXml;
    volatile String marshalled;
    private volatile boolean stored = false;

    public StringConfigurationPersister(List<ModelNode> bootOperations, XMLElementWriter<ModelMarshallingContext> rootDeparser, boolean persistXml) {
        super(rootDeparser);
        this.bootOperations = bootOperations;
        this.persistXml = persistXml;
    }

    @Override
    public PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses)
            throws ConfigurationPersistenceException {
        if (!persistXml) {
            return new NullConfigurationPersister().store(model, affectedAddresses);
        }
        stored = true;
        return new StringPersistenceResource(model, this);
    }

    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        return bootOperations;
    }

    public List<ModelNode> getBootOperations(){
        return bootOperations;
    }

    public String getMarshalled() {
        return marshalled;
    }

    @Override
    public boolean hasStored() {
        return isPersisting() && stored;
    }

    private class StringPersistenceResource implements PersistenceResource {
        private byte[] bytes;

        StringPersistenceResource(final ModelNode model, final AbstractConfigurationPersister persister) throws ConfigurationPersistenceException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(1024 * 8);
            try {
                try {
                    persister.marshallAsXml(model, output);
                } finally {
                    try {
                        output.close();
                    } catch (Exception ignore) {
                    }
                    bytes = output.toByteArray();
                }
            } catch (Exception e) {
                throw new ConfigurationPersistenceException("Failed to marshal configuration", e);
            }
        }

        @Override
        public void commit() {
            marshalled = new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public void rollback() {
            marshalled = null;
        }
    }
}