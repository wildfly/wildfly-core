/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.embedded;

import java.util.List;
import java.util.concurrent.Future;

import org.jboss.as.server.Bootstrap;
import org.jboss.as.server.ElapsedTime;
import org.jboss.as.server.Main;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentWrapper;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;

public final class StandaloneEmbeddedProcessBootstrap extends AbstractEmbeddedProcessBootstrap {


    @Override
    public Type getType() {
        return Type.STANDALONE_SERVER;
    }

    @Override
    protected Future<ServiceContainer> bootstrapEmbeddedProcess(ElapsedTime elapsedTime,
                                                      EmbeddedProcessBootstrapConfiguration configuration,
                                                      ServiceActivator... extraServices) {


        // Determine the ServerEnvironment
        ServerEnvironmentWrapper wrapper = Main.determineEnvironment(configuration.getCmdArgs(),
                configuration.getSystemProperties(), configuration.getSystemEnv(),
                ServerEnvironment.LaunchType.EMBEDDED, elapsedTime);
        ServerEnvironment serverEnvironment = wrapper.getServerEnvironment();
        if (serverEnvironment == null) {
            if (wrapper.getServerEnvironmentStatus() == ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR) {
                // I considered passing the cmdArgs to this but I don't want to possibly leak anything sensitive.
                // Main.determineEnvironment writes problems it finds to stdout so info is available that way.
                throw ServerLogger.ROOT_LOGGER.cannotCreateServerEnvironment();
            }
            // else configuration.getCmdArgs() must have wanted --help or --version or the like
            return null;
        }

        Bootstrap bootstrap = Bootstrap.Factory.newInstance();
        try {
            Bootstrap.Configuration bootstrapConfiguration = new Bootstrap.Configuration(serverEnvironment);
            bootstrapConfiguration.setModuleLoader(configuration.getModuleLoader());
            return bootstrap.startup(bootstrapConfiguration, List.of(extraServices));
        } catch (Exception ex) {
            bootstrap.failed();
            throw ex;
        }
    }
}
