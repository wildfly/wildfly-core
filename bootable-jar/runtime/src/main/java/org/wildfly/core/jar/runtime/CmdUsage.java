/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.jar.runtime;

import java.io.PrintStream;

import org.jboss.as.process.CommandLineArgumentUsage;
import org.jboss.as.process.CommandLineConstants;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 *
 * @author jdenise
 */
final class CmdUsage extends CommandLineArgumentUsage {
    public static void init() {

        addArguments(Constants.DEPLOYMENT_ARG + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argDeployment());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argPublicBindAddress());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "<interface>=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argInterfaceBindAddress());

        addArguments(CommandLineConstants.SYS_PROP + "<name>[=<value>]");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argSystem());

        addArguments(CommandLineConstants.HELP);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argHelp());

        instructions.add(BootableJarLogger.ROOT_LOGGER.argInstallation());
        addArguments(Constants.INSTALL_DIR_ARG + "=<value>");

        addArguments(CommandLineConstants.PROPERTIES + "=<url>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argProperties());

        addArguments(CommandLineConstants.SECURITY_PROP + "<name>[=<value>]");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argSecurityProperty());

        addArguments(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + "=<value>");
        instructions.add(BootableJarLogger.ROOT_LOGGER.argDefaultMulticastAddress());

        addArguments(CommandLineConstants.VERSION);
        instructions.add(BootableJarLogger.ROOT_LOGGER.argVersion());
    }

    public static void printUsage(final PrintStream out) {
        init();
        out.print(customUsage("java -jar <bootable jar>"));
    }
}
