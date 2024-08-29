/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.as.controller.persistence.AbstractConfigurationPersister;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.domain.HostControllerClient;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService;
import org.jboss.as.server.mgmt.domain.ServerBootOperationsService;
import org.jboss.as.server.parsing.StandaloneXmlSchemas;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.threads.AsyncFuture;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * This is the task used by the Host Controller and passed to a Server instance
 * in order to bootstrap it from a remote source process.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Mike M. Clark
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class ServerStartTask implements ServerTask, Serializable, ObjectInputValidation {

    private static final long serialVersionUID = -1037124182656400874L;

    private final String serverName;
    private final int portOffset;
    private final String hostControllerName;
    private final String home;
    private final List<ServiceActivator> startServices;
    private final List<ModelNode> updates;
    private final int initialOperationID;
    private final Properties properties = new Properties();
    private final boolean suspend;
    private final boolean gracefulStartup;

    public ServerStartTask(final String hostControllerName, final String serverName, final int portOffset, final int initialOperationID,
                           final List<ServiceActivator> startServices, final List<ModelNode> updates, final Map<String, String> launchProperties,
                           boolean suspend, boolean gracefulStartup) {
        assert serverName != null && serverName.length() > 0  : "Server name \"" + serverName + "\" is invalid; cannot be null or blank";
        assert hostControllerName != null && hostControllerName.length() > 0 : "Host Controller name \"" + hostControllerName + "\" is invalid; cannot be null or blank";

        this.serverName = serverName;
        this.portOffset = portOffset;
        this.startServices = startServices;
        this.updates = updates;
        this.initialOperationID = initialOperationID;
        this.hostControllerName = hostControllerName;
        this.suspend = suspend;
        this.gracefulStartup = gracefulStartup;

        this.home = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", null);
        String serverBaseDir = WildFlySecurityManager.getPropertyPrivileged("jboss.domain.servers.dir", null) + File.separatorChar + serverName;
        properties.setProperty(ServerEnvironment.SERVER_NAME, serverName);
        properties.setProperty(ServerEnvironment.HOME_DIR, home);
        properties.setProperty(ServerEnvironment.SERVER_BASE_DIR, serverBaseDir);
        properties.setProperty(ServerEnvironment.CONTROLLER_TEMP_DIR, WildFlySecurityManager.getPropertyPrivileged("jboss.domain.temp.dir", null));
        properties.setProperty(ServerEnvironment.DOMAIN_BASE_DIR, WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.DOMAIN_BASE_DIR, null));
        properties.setProperty(ServerEnvironment.DOMAIN_CONFIG_DIR, WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.DOMAIN_CONFIG_DIR, null));

        // Provide any other properties that standalone Main.determineEnvironment() would read
        // from system properties and pass in to ServerEnvironment
        setPropertyIfFound(launchProperties, ServerEnvironment.JAVA_EXT_DIRS, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.QUALIFIED_HOST_NAME, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.HOST_NAME, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.NODE_NAME, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.SERVER_DATA_DIR, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.SERVER_CONTENT_DIR, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.SERVER_LOG_DIR, properties);
        setPropertyIfFound(launchProperties, ServerEnvironment.SERVER_TEMP_DIR, properties);
        setPropertyIfFound(launchProperties, ProcessEnvironment.STABILITY, properties);
    }

    @Override
    public AsyncFuture<ServiceContainer> run(final List<ServiceActivator> runServices) {
        final Bootstrap bootstrap = Bootstrap.Factory.newInstance();
        final ProductConfig productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), home, properties);
        // Create server environment on the server, so that the system properties are getting initialized on the right side
        final ServerEnvironment providedEnvironment = new ServerEnvironment(hostControllerName, properties,
                WildFlySecurityManager.getSystemEnvironmentPrivileged(), null, null, ServerEnvironment.LaunchType.DOMAIN,
                RunningMode.NORMAL, productConfig, ElapsedTime.startingFromJvmStart(), suspend, gracefulStartup, null, null, null, null);
        DomainServerCommunicationServices.updateOperationID(initialOperationID);

        // TODO perhaps have ConfigurationPersisterFactory as a Service
        final List<ServiceActivator> services = new ArrayList<ServiceActivator>(startServices);
        final ServerBootOperationsService service = new ServerBootOperationsService();
        // ModelController.boot() will block on this future in order to get the boot updates.
        final Future<ModelNode> bootOperations = service.getFutureResult();
        final ServiceActivator activator = new ServiceActivator() {
            @Override
            public void activate(ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
                final ServiceTarget target = serviceActivatorContext.getServiceTarget();
                final ServiceBuilder sb = target.addService(ServiceName.JBOSS.append("server-boot-operations"), service);
                sb.requires(Services.JBOSS_AS);
                sb.addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, service.getServerController());
                sb.addDependency(HostControllerConnectionService.SERVICE_NAME, HostControllerClient.class, service.getClientInjector());
                sb.addDependency(Services.JBOSS_SERVER_EXECUTOR, Executor.class, service.getExecutorInjector());
                sb.install();
            }
        };
        services.add(activator);

        final Bootstrap.Configuration configuration = new Bootstrap.Configuration(providedEnvironment);
        final ExtensionRegistry extensionRegistry = configuration.getExtensionRegistry();
        final Bootstrap.ConfigurationPersisterFactory configurationPersisterFactory = new Bootstrap.ConfigurationPersisterFactory() {
            @Override
            public ExtensibleConfigurationPersister createConfigurationPersister(ServerEnvironment serverEnvironment, ExecutorService executorService) {
                StandaloneXmlSchemas standaloneXmlSchemas = new StandaloneXmlSchemas(serverEnvironment.getStability(), configuration.getModuleLoader(), executorService, extensionRegistry);
                ExtensibleConfigurationPersister persister = new AbstractConfigurationPersister(standaloneXmlSchemas.getCurrent()) {

                    private final PersistenceResource pr = new PersistenceResource() {

                        @Override
                        public void commit() {
                        }

                        @Override
                        public void rollback() {
                        }
                    };

                    @Override
                    public PersistenceResource store(final ModelNode model, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
                        return pr;
                    }

                    @Override
                    public List<ModelNode> load() throws ConfigurationPersistenceException {
                        try {
                            final ModelNode operations = bootOperations.get();
                            return operations.asList();
                        } catch (Exception e) {
                            throw new ConfigurationPersistenceException(e);
                        }
                    }
                };
                extensionRegistry.setWriterRegistry(persister);
                return persister;
            }
        };
        configuration.setConfigurationPersisterFactory(configurationPersisterFactory);
        return bootstrap.bootstrap(configuration, services);
    }

    @Override
    public void validateObject() throws InvalidObjectException {
        if (serverName == null) {
            throw ServerLogger.ROOT_LOGGER.invalidObject("serverName");
        }
        if(hostControllerName == null) {
            throw ServerLogger.ROOT_LOGGER.invalidObject("hostControllerName");
        }
        if (portOffset < 0) {
            throw ServerLogger.ROOT_LOGGER.invalidPortOffset();
        }
        if (updates == null) {
            throw ServerLogger.ROOT_LOGGER.invalidObject("updates");
        }
        if (startServices == null) {
            throw ServerLogger.ROOT_LOGGER.invalidObject("startServices");
        }
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        ois.registerValidation(this, 100);
    }

    static void setPropertyIfFound(final Map<String, String> launchProperties, final String key, final Properties properties) {
        if (launchProperties.containsKey(key)) {
            properties.setProperty(key, launchProperties.get(key));
        }
    }

}
