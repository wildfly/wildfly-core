/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import java.io.PrintStream;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;

import org.jboss.as.process.CommandLineArgumentUsage;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.version.ProductConfig;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 *
 * @author jdenise
 */
final class CmdUsage extends CommandLineArgumentUsage {
    public static void init(ProductConfig productConfig) {

        addArguments(Constants.DEPLOYMENT_ARG + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argDeployment());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argPublicBindAddress());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "<interface>=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argInterfaceBindAddress());

        addArguments(CommandLineConstants.SYS_PROP + "<name>[=<value>]");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argSystem());

        addArguments(Constants.CLI_SCRIPT_ARG + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argCliScript());

        addArguments(Constants.DISPLAY_GALLEON_CONFIG_ARG);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argDisplayGalleonConfig());

        addArguments(CommandLineConstants.HELP);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argHelp());

        instructions.add(BootableJarLogger.ROOT_LOGGER.argInstallation());
        addArguments(Constants.INSTALL_DIR_ARG + "=<value>");

        addArguments(CommandLineConstants.PROPERTIES + "=<url>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argProperties());

        addArguments(CommandLineConstants.SECMGR);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argSecurityManager());

        if (productConfig.getStabilitySet().size() > 1) {
            addArguments(CommandLineConstants.STABILITY + "=<value>");
            instructions.add(BootableJarLogger.ROOT_LOGGER.argStability(productConfig.getStabilitySet(), productConfig.getDefaultStability()));
        }

        addArguments(CommandLineConstants.SECURITY_PROP + "<name>[=<value>]");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argSecurityProperty());

        addArguments(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argDefaultMulticastAddress());

        addArguments(CommandLineConstants.VERSION);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argVersion());

        if(ConfigurationExtensionFactory.isConfigurationExtensionSupported()) {
            addArguments(ConfigurationExtensionFactory.getCommandLineUsageArguments());
            instructions.add(ConfigurationExtensionFactory.getCommandLineInstructions());
        }
    }

    public static void printUsage(ProductConfig config, final PrintStream out) {
        init(config);
        out.print(customUsage("java -jar <bootable jar>"));
    }
}
