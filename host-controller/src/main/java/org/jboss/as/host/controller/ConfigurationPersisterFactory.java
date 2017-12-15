/*
* JBoss, Home of Professional Open Source.
* Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.host.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.xml.namespace.QName;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.BackupXmlConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.persistence.XmlConfigurationPersister;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.parsing.DomainXml;
import org.jboss.as.host.controller.parsing.HostXml;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * Factory methods to produce an {@link ExtensibleConfigurationPersister} for different Host Controller use cases.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ConfigurationPersisterFactory {

    static final String CACHED_DOMAIN_XML = "domain.cached-remote.xml";
    static final String CACHED_DOMAIN_XML_BOOTFILE = "domain.cached-remote.xml.boot";

    // host.xml
    public static ExtensibleConfigurationPersister createHostXmlConfigurationPersister(final ConfigurationFile file, final HostControllerEnvironment environment,
                                                                                       final ExecutorService executorService, final ExtensionRegistry hostExtensionRegistry,
                                                                                       final LocalHostControllerInfo localHostControllerInfo) {
        String defaultHostname = localHostControllerInfo.getLocalHostName();
        if (environment.getRunningModeControl().isReloaded()) {
            if (environment.getRunningModeControl().getReloadHostName() != null) {
                defaultHostname = environment.getRunningModeControl().getReloadHostName();
            }
        }
        HostXml hostXml = new HostXml(defaultHostname, environment.getRunningModeControl().getRunningMode(),
                environment.isUseCachedDc(), Module.getBootModuleLoader(), executorService, hostExtensionRegistry);
        BackupXmlConfigurationPersister persister = new BackupXmlConfigurationPersister(file, new QName(Namespace.CURRENT.getUriString(), "host"), hostXml, hostXml, false);
        for (Namespace namespace : Namespace.domainValues()) {
            if (!namespace.equals(Namespace.CURRENT)) {
                persister.registerAdditionalRootElement(new QName(namespace.getUriString(), "host"), hostXml);
            }
        }
        hostExtensionRegistry.setWriterRegistry(persister);
        return persister;
    }

    // domain.xml
    public static ExtensibleConfigurationPersister createDomainXmlConfigurationPersister(final ConfigurationFile file, ExecutorService executorService, ExtensionRegistry extensionRegistry, final HostControllerEnvironment environment) {
        DomainXml domainXml = new DomainXml(Module.getBootModuleLoader(), executorService, extensionRegistry);

        boolean suppressLoad = false;
        ConfigurationFile.InteractionPolicy policy = file.getInteractionPolicy();
        final boolean isReloaded = environment.getRunningModeControl().isReloaded();

        if (!isReloaded && (policy == ConfigurationFile.InteractionPolicy.NEW && (file.getBootFile().exists() && file.getBootFile().length() != 0))) {
            throw HostControllerLogger.ROOT_LOGGER.cannotOverwriteDomainXmlWithEmpty(file.getBootFile().getName());
        }

        if (!isReloaded && (policy == ConfigurationFile.InteractionPolicy.NEW || policy == ConfigurationFile.InteractionPolicy.DISCARD)) {
            suppressLoad = true;
        }

        BackupXmlConfigurationPersister persister = new BackupXmlConfigurationPersister(file, new QName(Namespace.CURRENT.getUriString(), "domain"), domainXml, domainXml, suppressLoad);
        for (Namespace namespace : Namespace.domainValues()) {
            if (!namespace.equals(Namespace.CURRENT)) {
                persister.registerAdditionalRootElement(new QName(namespace.getUriString(), "domain"), domainXml);
            }
        }
        extensionRegistry.setWriterRegistry(persister);
        return persister;
    }

    // --backup
    public static ExtensibleConfigurationPersister createRemoteBackupDomainXmlConfigurationPersister(final File configDir, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        DomainXml domainXml = new DomainXml(Module.getBootModuleLoader(), executorService, extensionRegistry);
        File bootFile = new File(configDir, CACHED_DOMAIN_XML_BOOTFILE);
        File file = new File(configDir, CACHED_DOMAIN_XML);
        BackupRemoteDomainXmlPersister persister = new BackupRemoteDomainXmlPersister(file, bootFile, new QName(Namespace.CURRENT.getUriString(), "domain"), domainXml, domainXml);
        for (Namespace namespace : Namespace.domainValues()) {
            if (!namespace.equals(Namespace.CURRENT)) {
                persister.registerAdditionalRootElement(new QName(namespace.getUriString(), "domain"), domainXml);
            }
        }
        extensionRegistry.setWriterRegistry(persister);
        return persister;
    }

    // --cached-dc, just use the same one as --backup, as we need to write changes as they occur.
    public static ExtensibleConfigurationPersister createCachedRemoteDomainXmlConfigurationPersister(final File configDir, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        return createRemoteBackupDomainXmlConfigurationPersister(configDir, executorService, extensionRegistry);
    }

    // slave=true
    public static ExtensibleConfigurationPersister createTransientDomainXmlConfigurationPersister(ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        DomainXml domainXml = new DomainXml(Module.getBootModuleLoader(), executorService, extensionRegistry);
        ExtensibleConfigurationPersister persister = new NullConfigurationPersister(domainXml);
        extensionRegistry.setWriterRegistry(persister);
        return persister;
    }

    /**
     * --backup
     */
    static class BackupRemoteDomainXmlPersister extends XmlConfigurationPersister {

        private final AtomicBoolean successfulBoot = new AtomicBoolean();
        private File file;
        private File bootFile;
        private XmlConfigurationPersister bootWriter;

        BackupRemoteDomainXmlPersister(File file, File bootFile, QName rootElement, XMLElementReader<List<ModelNode>> rootParser, XMLElementWriter<ModelMarshallingContext> rootDeparser) {
            super(file, rootElement, rootParser, rootDeparser);
            this.bootWriter = new XmlConfigurationPersister(bootFile, rootElement, rootParser, rootDeparser);
            this.file = file;
            this.bootFile = bootFile;
        }

        @Override
        public void registerAdditionalRootElement(final QName anotherRoot, final XMLElementReader<List<ModelNode>> parser){
            bootWriter.registerAdditionalRootElement(anotherRoot, parser);
            super.registerAdditionalRootElement(anotherRoot, parser);
        }

        @Override
        public void registerSubsystemWriter(String name, XMLElementWriter<SubsystemMarshallingContext> deparser) {
            bootWriter.registerSubsystemWriter(name, deparser);
            super.registerSubsystemWriter(name, deparser);
        }

        @Override
        public void registerSubsystemWriter(String name, Supplier<XMLElementWriter<SubsystemMarshallingContext>> writer) {
            bootWriter.registerSubsystemWriter(name, writer);
            super.registerSubsystemWriter(name, writer);
        }

        @Override
        public void unregisterSubsystemWriter(String name) {
            bootWriter.unregisterSubsystemWriter(name);
            super.unregisterSubsystemWriter(name);
        }

        @Override
        public List<ModelNode> load() throws ConfigurationPersistenceException {
            try {
                return super.load();
            } catch (ConfigurationPersistenceException e) {
                HostControllerLogger.ROOT_LOGGER.invalidRemoteBackupPersisterState();
                throw e;
            }
        }

        @Override
        public void successfulBoot() throws ConfigurationPersistenceException {
            if (successfulBoot.compareAndSet(false, true)) {
                try {
                    Files.move(bootFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    HostControllerLogger.ROOT_LOGGER.cannotRenameCachedDomainXmlOnBoot(bootFile.getName(), file.getName(), e.getMessage());
                    throw new ConfigurationPersistenceException(e);
                }
            }
        }

        public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
            if (!successfulBoot.get()) {
                return bootWriter.store(model, affectedAddresses);
            }
            return super.store(model, affectedAddresses);
        }
    }
}
