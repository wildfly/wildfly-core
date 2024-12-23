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
        ServerEnvironment serverEnvironment = Main.determineEnvironment(configuration.getCmdArgs(),
                configuration.getSystemProperties(), configuration.getSystemEnv(),
                ServerEnvironment.LaunchType.EMBEDDED, elapsedTime).getServerEnvironment();
        if (serverEnvironment == null) {
            // Nothing to do
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
