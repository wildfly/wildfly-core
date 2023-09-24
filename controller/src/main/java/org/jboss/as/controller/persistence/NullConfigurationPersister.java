/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * A configuration persister which does not store configuration changes.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NullConfigurationPersister extends AbstractConfigurationPersister {

    public NullConfigurationPersister() {
        super(null);
    }

    public NullConfigurationPersister(XMLElementWriter<ModelMarshallingContext> rootDeparser) {
        super(rootDeparser);
    }

    /** {@inheritDoc} */
    @Override
    public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) {
        return NullPersistenceResource.INSTANCE;
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelNode> load() {
        return Collections.emptyList();
    }

    private static class NullPersistenceResource implements ConfigurationPersister.PersistenceResource {

        private static final NullPersistenceResource INSTANCE = new NullPersistenceResource();

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }
}
