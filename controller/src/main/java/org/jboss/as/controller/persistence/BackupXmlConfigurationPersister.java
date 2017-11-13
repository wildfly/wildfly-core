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

package org.jboss.as.controller.persistence;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.namespace.QName;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * An XML configuration persister which backs up the old file before overwriting it.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class BackupXmlConfigurationPersister extends XmlConfigurationPersister {

    private ConfigurationFile configurationFile;
    private final AtomicBoolean successfulBoot = new AtomicBoolean();

    /**
     * Construct a new instance.
     *
     * @param file the configuration base file
     * @param rootElement the root element of the configuration file
     * @param rootParser the root model parser
     * @param rootDeparser the root model deparser
     */
    public BackupXmlConfigurationPersister(final ConfigurationFile file, final QName rootElement, final XMLElementReader<List<ModelNode>> rootParser, final XMLElementWriter<ModelMarshallingContext> rootDeparser) {
        super(file.getBootFile(), rootElement, rootParser, rootDeparser, false);
        this.configurationFile = file;
    }

    /**
     * Construct a new instance.
     *
     * @param file the configuration base file
     * @param rootElement the root element of the configuration file
     * @param rootParser the root model parser
     * @param rootDeparser the root model deparser
     */
    public BackupXmlConfigurationPersister(final ConfigurationFile file, final QName rootElement, final XMLElementReader<List<ModelNode>> rootParser, final XMLElementWriter<ModelMarshallingContext> rootDeparser, final boolean supressLoad) {
        super(file.getBootFile(), rootElement, rootParser, rootDeparser, supressLoad);
        this.configurationFile = file;
    }

    /**
     * Construct a new instance.
     *  @param file the configuration base file
     * @param rootElement the root element of the configuration file
     * @param rootParser the root model parser
     * @param rootDeparser the root model deparser
     * @param reload {@code true} if this is a reload
     * @param allowEmpty {@code true} if it is ok if {@code ConfigurationFile.getBootFile()} points to an empty file
     */
    public BackupXmlConfigurationPersister(final ConfigurationFile file, final QName rootElement, final XMLElementReader<List<ModelNode>> rootParser,
                                           final XMLElementWriter<ModelMarshallingContext> rootDeparser, boolean reload, boolean allowEmpty) {
        super(file.getBootFile(), rootElement, rootParser, rootDeparser, isSuppressLoad(file, reload, allowEmpty));
        this.configurationFile = file;
    }

    private static boolean isSuppressLoad(ConfigurationFile configurationFile, boolean reload, boolean allowEmpty) {
        // We suppress load in two conditions:
        // 1) Initial boot where the interaction policy says to ignore the config
        // 2) Any case where allowEmpty is true and the boot file is empty
        if (allowEmpty && configurationFile.getBootFile().length() == 0) {
            return true;
        }
        ConfigurationFile.InteractionPolicy interactionPolicy = configurationFile.getInteractionPolicy();
        boolean initialEmpty = (interactionPolicy == ConfigurationFile.InteractionPolicy.NEW
                || interactionPolicy == ConfigurationFile.InteractionPolicy.DISCARD);
        return initialEmpty && !reload;
    }

    public void registerAdditionalRootElement(final QName anotherRoot, final XMLElementReader<List<ModelNode>> parser){
        super.registerAdditionalRootElement(anotherRoot, parser);
    }

    @Override
    public void successfulBoot() throws ConfigurationPersistenceException {
        if(successfulBoot.compareAndSet(false, true)) {
            configurationFile.successfulBoot();
        }
    }

    /**
     * Overrides the default behavior to return {@code false} until {@link #successfulBoot()} has been called.
     *
     * @return {@code true} if {@link #successfulBoot()} has been called; {@code false} otherwise
     */
    @Override
    public boolean isPersisting() {
        return successfulBoot.get();
    }

    @Override
    public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
        if(!successfulBoot.get()) {
            return new PersistenceResource() {
                public void commit() {
                }

                public void rollback() {
                }
            };
        }
        return new ConfigurationFilePersistenceResource(model, configurationFile, this);
    }

    @Override
    public String snapshot() throws ConfigurationPersistenceException {
        return configurationFile.snapshot();
    }

    @Override
    public SnapshotInfo listSnapshots() {
        return configurationFile.listSnapshots();
    }

    @Override
    public void deleteSnapshot(final String name) {
        configurationFile.deleteSnapshot(name);
    }
}
