/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.persistence;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class TestConfigurationPersister extends AbstractConfigurationPersister {

    public TestConfigurationPersister() {
        super(null);
    }

    @Override
    public PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses)
            throws ConfigurationPersistenceException {
        return create(model);
    }

    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        return Collections.emptyList();
    }

    @Override
    public void marshallAsXml(ModelNode model, OutputStream output) throws ConfigurationPersistenceException {
        try {
            output.write(model.asString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ConfigurationPersistenceException(e);
        }
    }

    abstract PersistenceResource create(ModelNode model) throws ConfigurationPersistenceException;
}
