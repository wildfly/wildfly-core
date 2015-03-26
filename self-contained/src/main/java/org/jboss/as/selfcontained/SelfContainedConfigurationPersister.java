/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.selfcontained;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementWriter;

import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/** A read-only configuration persister that provides configuration
 *  from a pre-computed list of operations.
 *
 * @author Bob McWhirter
 */
public class SelfContainedConfigurationPersister implements ExtensibleConfigurationPersister {

    /** Pre-computed configuration */
    private final List<ModelNode> containerDefinition;

    /** Construct with a pre-computed list of operations.
     *
     * @param containerDefinition The list of configuration operations.
     */
    public SelfContainedConfigurationPersister(List<ModelNode> containerDefinition) {
        this.containerDefinition = containerDefinition;
    }

    @Override
    public ConfigurationPersister.PersistenceResource store(ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        return new ConfigurationPersister.PersistenceResource() {
            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }
        };
    }

    @Override
    public void marshallAsXml(ModelNode model, OutputStream output) throws ConfigurationPersistenceException {
    }

    @Override
    public List<ModelNode> load() throws ConfigurationPersistenceException {
        return this.containerDefinition;
    }

    @Override
    public void successfulBoot() throws ConfigurationPersistenceException {
    }

    @Override
    public String snapshot() throws ConfigurationPersistenceException {
        return null;
    }

    @Override
    public SnapshotInfo listSnapshots() {
        return ConfigurationPersister.NULL_SNAPSHOT_INFO;
    }

    @Override
    public void deleteSnapshot(String name) {
    }

    @Override
    public void registerSubsystemWriter(String name, XMLElementWriter<SubsystemMarshallingContext> writer) {
        // writing is not supported
    }

    @Override
    public void unregisterSubsystemWriter(String name) {
    }
}
