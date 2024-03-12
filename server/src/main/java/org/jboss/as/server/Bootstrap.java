/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.namespace.QName;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.BackupXmlConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.server.controller.git.GitConfigurationPersister;
import org.jboss.as.controller.persistence.XmlConfigurationPersister;
import org.jboss.as.server.parsing.StandaloneXml;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.threads.AsyncFuture;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * The application server bootstrap interface.  Get a new instance via {@link Factory#newInstance()}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Thomas.Diesler@jboss.com
 */
public interface Bootstrap {

    /**
     * Bootstrap a new server instance, providing a {@code Future} that will provide the
     * server's MSC {@link ServiceContainer} once the root service for the server is started.
     * <strong>Note:</strong> The future will provide its value before the full server boot is
     * complete. To await the full boot, use {@link #startup(Configuration, List)}.
     *
     * @param configuration the server configuration
     * @param extraServices additional services to start and stop with the server instance
     * @return the future service container
     */
    AsyncFuture<ServiceContainer> bootstrap(Configuration configuration, List<ServiceActivator> extraServices);

    /**
     * Calls {@link #bootstrap(Configuration, List)} to bootstrap the container. The value for the returned future
     * becomes available when all installed services have been started/failed.
     *
     * @param configuration the server configuration
     * @param extraServices additional services to start and stop with the server instance
     * @return the future service container
     */
    AsyncFuture<ServiceContainer> startup(Configuration configuration, List<ServiceActivator> extraServices);

    /**
     * Alerts this bootstrap instance that a failure has occurred during bootstrap or startup and it should
     * clean up resources.
     */
    void failed();

    /**
     * The configuration for server bootstrap.
     */
    final class Configuration {

        private final ServerEnvironment serverEnvironment;
        private final RunningModeControl runningModeControl;
        private final ExtensionRegistry extensionRegistry;
        private final CapabilityRegistry capabilityRegistry;
        private final ManagedAuditLogger auditLogger;
        private final DelegatingConfigurableAuthorizer authorizer;
        private final ManagementSecurityIdentitySupplier securityIdentitySupplier;
        private ModuleLoader moduleLoader = Module.getBootModuleLoader();
        private ConfigurationPersisterFactory configurationPersisterFactory;
        private long startTime;

        public Configuration(final ServerEnvironment serverEnvironment) {
            assert serverEnvironment != null : "serverEnvironment is null";
            this.serverEnvironment = serverEnvironment;
            this.runningModeControl = serverEnvironment.getRunningModeControl();
            this.auditLogger = serverEnvironment.createAuditLogger();
            this.authorizer = new DelegatingConfigurableAuthorizer();
            this.securityIdentitySupplier = new ManagementSecurityIdentitySupplier();
            this.extensionRegistry = ExtensionRegistry.builder(serverEnvironment.getLaunchType().getProcessType())
                    .withRunningModeControl(this.runningModeControl)
                    .withStability(serverEnvironment.getStability())
                    .withAuditLogger(this.auditLogger)
                    .withAuthorizer(this.authorizer)
                    .withSecurityIdentitySupplier(this.securityIdentitySupplier)
                    .build();
            this.capabilityRegistry = new CapabilityRegistry(true);
            this.startTime = serverEnvironment.getStartTime();
        }

        private Configuration(final Configuration original, ServerEnvironment serverEnvironment) {
            this.serverEnvironment = serverEnvironment;
            this.runningModeControl = original.runningModeControl;
            this.extensionRegistry = original.extensionRegistry;
            this.capabilityRegistry = original.capabilityRegistry;
            this.auditLogger = original.auditLogger;
            this.authorizer = original.authorizer;
            this.securityIdentitySupplier = original.securityIdentitySupplier;
            this.moduleLoader = original.moduleLoader;
            this.configurationPersisterFactory = original.configurationPersisterFactory;
            this.startTime = original.startTime;
        }

        /**
         * Get the server environment.
         *
         * @return the server environment. Will not be {@code null}
         */
        public ServerEnvironment getServerEnvironment() {
            return serverEnvironment;
        }

        /**
         * Get the server's running mode control.
         * @return the running mode control. Will not be {@code null}
         */
        RunningModeControl getRunningModeControl() {
            return runningModeControl;
        }

        /**
         * Get the extension registry.
         *
         * @return the extension registry. Will not be {@code null}
         */
        public ExtensionRegistry getExtensionRegistry() {
            return extensionRegistry;
        }

        /**
         * Get the capability registry.
         *
         * @return the capability registry. Will not be {@code null}
         */
        public CapabilityRegistry getCapabilityRegistry() {
            return capabilityRegistry;
        }

        /**
         * Get the auditLogger
         *
         * @return the auditLogger
         */
        public ManagedAuditLogger getAuditLogger() {
            return auditLogger;
        }

        /**
         * Get the authorizer
         *
         * @return the authorizer
         */
        public DelegatingConfigurableAuthorizer getAuthorizer() {
            return authorizer;
        }

        /**
         * Get the {@link SecurityIdentity} supplier.
         *
         * @return the {@link SecurityIdentity} supplier.
         */
        public ManagementSecurityIdentitySupplier getSecurityIdentitySupplier() {
            return securityIdentitySupplier;
        }

        /**
         * Get the application server module loader.
         *
         * @return the module loader
         */
        public ModuleLoader getModuleLoader() {
            return moduleLoader;
        }

        /**
         * Set the application server module loader.
         *
         * @param moduleLoader the module loader
         */
        public void setModuleLoader(final ModuleLoader moduleLoader) {
            assert moduleLoader != null : "moduleLoader is null";
            this.moduleLoader = moduleLoader;
        }

        /**
         * Get the factory for the configuration persister to use.
         *
         * @return the configuration persister factory
         */
        public synchronized ConfigurationPersisterFactory getConfigurationPersisterFactory() {
            if (configurationPersisterFactory == null) {
                configurationPersisterFactory = new ConfigurationPersisterFactory() {
                    @Override
                    public ExtensibleConfigurationPersister createConfigurationPersister(ServerEnvironment serverEnvironment, ExecutorService executorService) {
                        ConfigurationFile configurationFile = serverEnvironment.getServerConfigurationFile();
                        if (runningModeControl.isReloaded()) {
                            configurationFile.resetBootFile(runningModeControl.isUseCurrentConfig(), runningModeControl.getAndClearNewBootFileName());
                        }
                        QName rootElement = new QName(Namespace.CURRENT.getUriString(), "server");
                        StandaloneXml parser = new StandaloneXml(Module.getBootModuleLoader(), executorService, extensionRegistry);
                        XmlConfigurationPersister persister;
                        if (configurationFile.useGit()) {
                            persister = new GitConfigurationPersister(serverEnvironment.getGitRepository(), configurationFile, rootElement, parser, parser,
                                    runningModeControl.isReloaded());
                        } else {
                            persister = new BackupXmlConfigurationPersister(configurationFile, rootElement, parser, parser,
                                    runningModeControl.isReloaded(), serverEnvironment.getLaunchType() == ServerEnvironment.LaunchType.EMBEDDED);
                        }
                        for (Namespace namespace : Namespace.domainValues()) {
                            if (!namespace.equals(Namespace.CURRENT)) {
                                persister.registerAdditionalRootElement(new QName(namespace.getUriString(), "server"), parser);
                            }
                        }
                        extensionRegistry.setWriterRegistry(persister);
                        return persister;
                    }
                };
            }
            return configurationPersisterFactory;
        }

        /**
         * Set the configuration persister factory to use.
         *
         * @param configurationPersisterFactory the configuration persister factory
         */
        public synchronized void setConfigurationPersisterFactory(final ConfigurationPersisterFactory configurationPersisterFactory) {
            this.configurationPersisterFactory = configurationPersisterFactory;
        }

        /**
         * Get the server start time to report in the logs.
         *
         * @return the server start time
         */
        public long getStartTime() {
            return startTime;
        }

        Configuration recalculateForReload(RunningModeControl runningModeControl) {
            if (runningModeControl.isReloaded()) {
                ServerEnvironment recalculatedServerEnvironment = serverEnvironment.recalculateForReload(runningModeControl);
                if (recalculatedServerEnvironment != serverEnvironment) {
                    return new Configuration(this, recalculatedServerEnvironment);
                }
            }
            return this;
        }
    }

    /** A factory for the {@link ExtensibleConfigurationPersister} to be used by this server */
    interface ConfigurationPersisterFactory {
        /**
         *
         * @param serverEnvironment the server environment. Cannot be {@code null}
         * @param executorService an executor service the configuration persister can use.
         *                        May be {@code null} if asynchronous work is not supported
         * @return the configuration persister. Will not be {@code null}
         */
        ExtensibleConfigurationPersister createConfigurationPersister(final ServerEnvironment serverEnvironment, final ExecutorService executorService);
    }

    /**
     * The factory for creating new instances of {@link org.jboss.as.server.Bootstrap}.
     */
    final class Factory {

        private Factory() {
        }

        /**
         * Create a new instance.
         *
         * @return the new bootstrap instance
         */
        public static Bootstrap newInstance() {
            return new BootstrapImpl();
        }
    }
}
