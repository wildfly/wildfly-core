/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import java.io.PrintStream;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;

import org.jboss.as.process.CommandLineArgumentUsage;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.server.logging.ServerLogger;

public class CommandLineArgumentUsageImpl extends CommandLineArgumentUsage {

    public static void init(){

        addArguments(CommandLineConstants.ADMIN_ONLY);
        instructions.add(ServerLogger.ROOT_LOGGER.argAdminOnly());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + " <value>", CommandLineConstants.PUBLIC_BIND_ADDRESS + "=<value>");
        instructions.add(ServerLogger.ROOT_LOGGER.argPublicBindAddress());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "<interface>=<value>");
        instructions.add(ServerLogger.ROOT_LOGGER.argInterfaceBindAddress());

        addArguments(CommandLineConstants.SHORT_SERVER_CONFIG + " <config>", CommandLineConstants.SHORT_SERVER_CONFIG + "=<config>");
        instructions.add(ServerLogger.ROOT_LOGGER.argShortServerConfig());

        addArguments(CommandLineConstants.DEBUG + " [<port>]");
        instructions.add(ServerLogger.ROOT_LOGGER.argDebugPort());

        addArguments(CommandLineConstants.SYS_PROP + "<name>[=<value>]");
        instructions.add(ServerLogger.ROOT_LOGGER.argSystem());

        addArguments(CommandLineConstants.SHORT_HELP, CommandLineConstants.HELP);
        instructions.add(ServerLogger.ROOT_LOGGER.argHelp());

        addArguments(CommandLineConstants.READ_ONLY_SERVER_CONFIG + "=<config>");
        instructions.add(ServerLogger.ROOT_LOGGER.argReadOnlyServerConfig());

        addArguments(CommandLineConstants.SHORT_PROPERTIES + " <url>", CommandLineConstants.SHORT_PROPERTIES + "=<url>", CommandLineConstants.PROPERTIES + "=<url>");
        instructions.add(ServerLogger.ROOT_LOGGER.argProperties() );

        addArguments(CommandLineConstants.SECURITY_PROP + "<name>[=<value>]");
        instructions.add(ServerLogger.ROOT_LOGGER.argSecurityProperty());

        addArguments(CommandLineConstants.SERVER_CONFIG + "=<config>");
        instructions.add(ServerLogger.ROOT_LOGGER.argServerConfig());

        addArguments( CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + " <value>", CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + "=<value>");
        instructions.add(ServerLogger.ROOT_LOGGER.argDefaultMulticastAddress());

        addArguments(CommandLineConstants.SHORT_VERSION, CommandLineConstants.OLD_SHORT_VERSION, CommandLineConstants.VERSION);
        instructions.add(ServerLogger.ROOT_LOGGER.argVersion());

        addArguments(CommandLineConstants.SECMGR);
        instructions.add(ServerLogger.ROOT_LOGGER.argSecMgr());

        addArguments(CommandLineConstants.START_MODE);
        instructions.add(ServerLogger.ROOT_LOGGER.argStartMode());

        addArguments(CommandLineConstants.GRACEFUL_STARTUP+"=<value>");
        instructions.add(ServerLogger.ROOT_LOGGER.argGracefulStartup());

        addArguments(CommandLineConstants.GIT_REPO + " <repo_url>", CommandLineConstants.GIT_REPO + "=<repo_url>");
        instructions.add(ServerLogger.ROOT_LOGGER.argGitRepo());

        addArguments(CommandLineConstants.GIT_BRANCH + " <branch>", CommandLineConstants.GIT_BRANCH + "=<branch>");
        instructions.add(ServerLogger.ROOT_LOGGER.argGitBranch());

        addArguments(CommandLineConstants.GIT_AUTH + " <auth_config>", CommandLineConstants.GIT_AUTH + "=<auth_config>");
        instructions.add(ServerLogger.ROOT_LOGGER.argGitAuth());

        if(ConfigurationExtensionFactory.isConfigurationExtensionSupported()) {
            addArguments(ConfigurationExtensionFactory.getCommandLineUsageArguments());
            instructions.add(ConfigurationExtensionFactory.getCommandLineInstructions());
        }

    }

    public static void printUsage(final PrintStream out) {
        init();
        out.print(usage("standalone"));
    }

}
