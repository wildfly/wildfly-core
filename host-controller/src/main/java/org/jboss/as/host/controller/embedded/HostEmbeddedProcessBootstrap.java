/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.embedded;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerEnvironmentWrapper;
import org.jboss.as.host.controller.Main;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.server.ElapsedTime;
import org.jboss.as.server.embedded.AbstractEmbeddedProcessBootstrap;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceContainer;
import org.wildfly.core.embedded.spi.EmbeddedProcessBootstrapConfiguration;

public final class HostEmbeddedProcessBootstrap extends AbstractEmbeddedProcessBootstrap {

    private static final String MODULE_PATH = "-mp";
    private static final String PC_ADDRESS = "--pc-address";
    private static final String PC_PORT = "--pc-port";

    private static final String SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR = "jboss.domain.deployment.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR = "jboss.domain.log.dir";

    private static final String[] DOMAIN_KEYS = {
            SYSPROP_KEY_JBOSS_DOMAIN_BASE_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_DEPLOYMENT_DIR,
            SYSPROP_KEY_JBOSS_DOMAIN_TEMP_DIR, SYSPROP_KEY_JBOSS_DOMAIN_LOG_DIR, SYSPROP_KEY_JBOSS_DOMAIN_CONFIG_DIR
    };

    @Override
    public Type getType() {
        return Type.HOST_CONTROLLER;
    }

    @Override
    protected Future<ServiceContainer> bootstrapEmbeddedProcess(ElapsedTime elapsedTime,
                                                      EmbeddedProcessBootstrapConfiguration configuration,
                                                      ServiceActivator... extraServices) throws Exception {
        // Determine the HostControllerEnvironment
        HostControllerEnvironment environment = createHostControllerEnvironment(configuration.getJBossHome(), configuration.getCmdArgs(), elapsedTime);
        if (environment == null) {
            // configuration.getCmdArgs() must have wanted --help or --version or the like
            return null;
        }

        final byte[] authBytes = new byte[16];
        new Random(new SecureRandom().nextLong()).nextBytes(authBytes);
        final String pcAuthCode = Base64.getEncoder().encodeToString(authBytes);

        EmbeddedHostControllerBootstrap hostControllerBootstrap = new EmbeddedHostControllerBootstrap(environment, pcAuthCode);
        try {
            return hostControllerBootstrap.bootstrap(extraServices);
        } catch (Exception ex) {
            hostControllerBootstrap.failed();
            throw ex;
        }
    }

    private static HostControllerEnvironment createHostControllerEnvironment(File jbossHome, String[] cmdargs,
                                                                             ElapsedTime elapsedTime) {
        SecurityActions.setPropertyPrivileged(HostControllerEnvironment.HOME_DIR, jbossHome.getAbsolutePath());

        List<String> cmds = new ArrayList<>(Arrays.asList(cmdargs));

        // these are for compatibility with Main.determineEnvironment / HostControllerEnvironment
        // Once WFCORE-938 is resolved, --admin-only will allow a connection back to the DC for slaves,
        // and support a method for setting the domain master address outside of -Djboss.domain.primary.address
        // so we'll probably need a command line argument for this if it's not specified as a system prop
        if (SecurityActions.getPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, null) == null) {
            SecurityActions.setPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, "127.0.0.1");
        }
        cmds.add(MODULE_PATH);
        cmds.add(SecurityActions.getPropertyPrivileged("module.path", ""));
        cmds.add(PC_ADDRESS);
        cmds.add("0");
        cmds.add(PC_PORT);
        cmds.add("0");
        // this used to be set in the embedded-hc specific env setup, WFCORE-938 will add support for --admin-only=false
        cmds.add("--admin-only");

        for (final String prop : DOMAIN_KEYS) {
            // if we've started with any jboss.domain.base.dir etc, copy those in here.
            String value = SecurityActions.getPropertyPrivileged(prop, null);
            if (value != null)
                cmds.add("-D" + prop + "=" + value);
        }
        HostControllerEnvironmentWrapper wrapper = Main.determineEnvironment(cmds.toArray(new String[0]), elapsedTime, ProcessType.EMBEDDED_HOST_CONTROLLER);
        if (wrapper.getHostControllerEnvironmentStatus() == HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR) {
            // I considered passing the cmdArgs to this, but I don't want to possibly leak anything sensitive.
            // Main.determineEnvironment writes problems it finds to stdout so info is available that way.
            throw HostControllerLogger.ROOT_LOGGER.cannotCreateHostControllerEnvironment();

        }
        return wrapper.getHostControllerEnvironment();
    }
}

