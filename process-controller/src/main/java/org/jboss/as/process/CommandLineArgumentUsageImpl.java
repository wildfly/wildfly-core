/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import java.io.PrintStream;
import java.util.EnumSet;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.version.Quality;

public class CommandLineArgumentUsageImpl extends CommandLineArgumentUsage {

    public static void init(){

        addArguments(CommandLineConstants.ADMIN_ONLY);
        instructions.add(ProcessLogger.ROOT_LOGGER.argAdminOnly());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + " <value>", CommandLineConstants.PUBLIC_BIND_ADDRESS + "=<value>" );
        instructions.add(ProcessLogger.ROOT_LOGGER.argPublicBindAddress());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "<interface>=<value>" );
        instructions.add(ProcessLogger.ROOT_LOGGER.argInterfaceBindAddress());

        addArguments(CommandLineConstants.BACKUP_DC);
        instructions.add(ProcessLogger.ROOT_LOGGER.argBackup());

        addArguments(CommandLineConstants.SHORT_DOMAIN_CONFIG + " <config>", CommandLineConstants.SHORT_DOMAIN_CONFIG + "=<config>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argShortDomainConfig());

        addArguments(CommandLineConstants.CACHED_DC);
        instructions.add(ProcessLogger.ROOT_LOGGER.argCachedDc());

        addArguments(CommandLineConstants.SYS_PROP + "<name>[=<value>]");
        instructions.add(ProcessLogger.ROOT_LOGGER.argSystem());

        addArguments(CommandLineConstants.DOMAIN_CONFIG + "=<config>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argDomainConfig());

        addArguments(CommandLineConstants.SHORT_HELP, CommandLineConstants.HELP);
        instructions.add(ProcessLogger.ROOT_LOGGER.argHelp());

        addArguments(CommandLineConstants.HOST_CONFIG + "=<config>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argHostConfig());

        addArguments(CommandLineConstants.INTERPROCESS_HC_ADDRESS + "=<address>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argInterProcessHcAddress());

        addArguments(CommandLineConstants.INTERPROCESS_HC_PORT + "=<port>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argInterProcessHcPort());

        addArguments(CommandLineConstants.PRIMARY_ADDRESS +"=<address>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argMasterAddress());

        addArguments(CommandLineConstants.PRIMARY_PORT + "=<port>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argMasterPort());

        addArguments(CommandLineConstants.READ_ONLY_DOMAIN_CONFIG + "=<config>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argReadOnlyDomainConfig());

        addArguments(CommandLineConstants.READ_ONLY_HOST_CONFIG + "=<config>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argReadOnlyHostConfig());

        addArguments(CommandLineConstants.SHORT_PROPERTIES + " <url>", CommandLineConstants.SHORT_PROPERTIES + "=<url>", CommandLineConstants.PROPERTIES + "=<url>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argProperties());

        addArguments(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR + "=<address>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argPcAddress());

        addArguments(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT + "=<port>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argPcPort());

        addArguments(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + " <value>", CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + "=<value>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argDefaultMulticastAddress());

        addArguments(CommandLineConstants.OLD_SHORT_VERSION, CommandLineConstants.SHORT_VERSION, CommandLineConstants.VERSION);
        instructions.add(ProcessLogger.ROOT_LOGGER.argVersion());

        addArguments(CommandLineConstants.SECMGR);
        instructions.add(ProcessLogger.ROOT_LOGGER.argSecMgr());

        addArguments(CommandLineConstants.QUALITY + "=<value>");
        instructions.add(ProcessLogger.ROOT_LOGGER.argQuality(EnumSet.allOf(Quality.class), Quality.DEFAULT));
    }

    public static void printUsage(final PrintStream out) {
        init();
        out.print(usage("domain"));
    }
}
