/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GIT_MASTER_BRANCH;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.persistence.ConfigurationExtensionFactory;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.ProductConfig;
import org.jboss.modules.Module;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The main-class entry point for standalone server instances.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author John Bailey
 * @author Brian Stansberry
 * @author Anil Saldhana
 */
public final class Main {
    // Capture System.out and System.err before they are redirected by STDIO
    private static final PrintStream STDOUT = System.out;
    private static final PrintStream STDERR = System.err;

    private static void usage() {
        CommandLineArgumentUsageImpl.printUsage(STDOUT);
    }

    private Main() {
    }

    /**
     * The main method.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        try {
            if (java.util.logging.LogManager.getLogManager().getClass().getName().equals("org.jboss.logmanager.LogManager")) {
                // Make sure our original stdio is properly captured.
                try {
                    Class.forName(org.jboss.logmanager.handlers.ConsoleHandler.class.getName(), true, org.jboss.logmanager.handlers.ConsoleHandler.class.getClassLoader());
                } catch (Throwable ignored) {
                }
                // Install JBoss Stdio to avoid any nasty crosstalk, after command line arguments are processed.
                StdioContext.install();
                final StdioContext context = StdioContext.create(
                        new NullInputStream(),
                        new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stdout"), org.jboss.logmanager.Level.INFO),
                        new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stderr"), org.jboss.logmanager.Level.ERROR)
                );
                StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(context));
            }

            Module.registerURLStreamHandlerFactoryModule(Module.getBootModuleLoader().loadModule("org.jboss.vfs"));
            ServerEnvironmentWrapper serverEnvironmentWrapper = determineEnvironment(args, WildFlySecurityManager.getSystemPropertiesPrivileged(),
                    WildFlySecurityManager.getSystemEnvironmentPrivileged(), ServerEnvironment.LaunchType.STANDALONE,
                    Module.getStartTime());
            if (serverEnvironmentWrapper.getServerEnvironment() == null) {
                if (serverEnvironmentWrapper.getServerEnvironmentStatus() == ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR) {
                    abort(null);
                } else {
                    SystemExiter.safeAbort();
                }
            } else {
                final Bootstrap bootstrap = Bootstrap.Factory.newInstance();
                final Bootstrap.Configuration configuration = new Bootstrap.Configuration(serverEnvironmentWrapper.getServerEnvironment());
                configuration.setModuleLoader(Module.getBootModuleLoader());
                bootstrap.bootstrap(configuration, Collections.emptyList()).get();
            }
        } catch (Throwable t) {
            abort(t);
        }
    }

    private static void abort(Throwable t) {
        try {
            if (t != null) {
                t.printStackTrace(STDERR);
            }
        } finally {
            SystemExiter.abort(ExitCodes.FAILED);
        }
    }

    /**
     * Establish the {@link ServerEnvironment} object for this server.
     * @param args any command line arguments passed to the process main method
     * @param systemProperties system properties
     * @param systemEnvironment environment variables
     * @param launchType how the process was launched
     * @param startTime time in ms since the epoch when the process was considered to be started
     * @return the ServerEnvironment object
     */
    public static ServerEnvironmentWrapper determineEnvironment(String[] args, Properties systemProperties, Map<String, String> systemEnvironment,
                                                         ServerEnvironment.LaunchType launchType, long startTime) {
        final int argsLength = args.length;
        String serverConfig = null;
        String gitRepository = null;
        String gitBranch = GIT_MASTER_BRANCH;
        String gitAuthConfiguration = null;
        String supplementalConfiguration = null;
        RunningMode runningMode = RunningMode.NORMAL;
        ProductConfig productConfig;
        ConfigurationFile.InteractionPolicy configInteractionPolicy = ConfigurationFile.InteractionPolicy.STANDARD;
        boolean startSuspended = false;
        boolean startGracefully = true;
        boolean removeConfig = false;
        boolean startModeSet = false;
        for (int i = 0; i < argsLength; i++) {
            final String arg = args[i];
            try {
                if (CommandLineConstants.VERSION.equals(arg) || CommandLineConstants.SHORT_VERSION.equals(arg)
                        || CommandLineConstants.OLD_VERSION.equals(arg) || CommandLineConstants.OLD_SHORT_VERSION.equals(arg)) {
                    productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.HOME_DIR, null), null);
                    STDOUT.println(productConfig.getPrettyVersionString());
                    return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.NORMAL);
                } else if (CommandLineConstants.HELP.equals(arg) || CommandLineConstants.SHORT_HELP.equals(arg) || CommandLineConstants.OLD_HELP.equals(arg)) {
                    usage();
                    return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.NORMAL);
                } else if (CommandLineConstants.SERVER_CONFIG.equals(arg) || CommandLineConstants.SHORT_SERVER_CONFIG.equals(arg)
                        || CommandLineConstants.OLD_SERVER_CONFIG.equals(arg)) {
                    assertSingleConfig(serverConfig);
                    serverConfig = args[++i];
                } else if (arg.startsWith(CommandLineConstants.SERVER_CONFIG)) {
                    assertSingleConfig(serverConfig);
                    serverConfig = parseValue(arg, CommandLineConstants.SERVER_CONFIG);
                    if (serverConfig == null) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.SHORT_SERVER_CONFIG)) {
                    assertSingleConfig(serverConfig);
                    serverConfig = parseValue(arg, CommandLineConstants.SHORT_SERVER_CONFIG);
                    if (serverConfig == null) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.READ_ONLY_SERVER_CONFIG)) {
                    assertSingleConfig(serverConfig);
                    serverConfig = parseValue(arg, CommandLineConstants.READ_ONLY_SERVER_CONFIG);
                    if (serverConfig == null) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    configInteractionPolicy = ConfigurationFile.InteractionPolicy.READ_ONLY;
                } else if (arg.startsWith(CommandLineConstants.OLD_SERVER_CONFIG)) {
                    serverConfig = parseValue(arg, CommandLineConstants.OLD_SERVER_CONFIG);
                    if (serverConfig == null) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith("--internal-empty-config")) {
                    assert launchType == ServerEnvironment.LaunchType.EMBEDDED;
                    configInteractionPolicy = removeConfig ? ConfigurationFile.InteractionPolicy.DISCARD : ConfigurationFile.InteractionPolicy.NEW;
                } else if (arg.startsWith("--internal-remove-config")) {
                    assert launchType == ServerEnvironment.LaunchType.EMBEDDED;
                    removeConfig = true;
                    if (configInteractionPolicy == ConfigurationFile.InteractionPolicy.NEW) {
                        configInteractionPolicy = ConfigurationFile.InteractionPolicy.DISCARD;
                    }
                } else if (CommandLineConstants.PROPERTIES.equals(arg) || CommandLineConstants.OLD_PROPERTIES.equals(arg)
                        || CommandLineConstants.SHORT_PROPERTIES.equals(arg)) {
                    // Set system properties from url/file
                    if (!processProperties(arg, args[++i],systemProperties)) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec,systemProperties)) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.SHORT_PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.SHORT_PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec,systemProperties)) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                }  else if (arg.startsWith(CommandLineConstants.OLD_PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.OLD_PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec,systemProperties)) {
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.SYS_PROP)) {

                    // set a system property
                    String name, value;
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                        name = arg.substring(2);
                        value = "true";
                    } else {
                        name = arg.substring(2, idx);
                        value = arg.substring(idx + 1);
                    }
                    systemProperties.setProperty(name, value);
                } else if (arg.startsWith(CommandLineConstants.PUBLIC_BIND_ADDRESS)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.noArgValue(arg));
                        usage();
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : args[++i];
                    value = fixPossibleIPv6URL(value);
                    String propertyName = null;
                    if (idx < 0) {
                        // -b xxx -bmanagement xxx
                        propertyName = arg.length() == 2 ? ServerEnvironment.JBOSS_BIND_ADDRESS : ServerEnvironment.JBOSS_BIND_ADDRESS_PREFIX + arg.substring(2);
                    } else if (idx == 2) {
                        // -b=xxx
                        propertyName = ServerEnvironment.JBOSS_BIND_ADDRESS;
                    } else {
                        // -bmanagement=xxx
                        propertyName =  ServerEnvironment.JBOSS_BIND_ADDRESS_PREFIX + arg.substring(2, idx);
                    }
                    systemProperties.setProperty(propertyName, value);
                } else if (arg.startsWith(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                        usage();
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : args[++i];
                    value = fixPossibleIPv6URL(value);

                    systemProperties.setProperty(ServerEnvironment.JBOSS_DEFAULT_MULTICAST_ADDRESS, value);
                } else if (CommandLineConstants.ADMIN_ONLY.equals(arg)) {
                    if(startModeSet) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.cannotSetBothAdminOnlyAndStartMode());
                        usage();
                        return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    startModeSet = true;
                    runningMode = RunningMode.ADMIN_ONLY;
                } else if (arg.startsWith(CommandLineConstants.SECURITY_PROP)) {
                    //Value can be a comma separated key value pair
                    //Drop the first 2 characters
                    String token = arg.substring(2);
                    processSecurityProperties(token,systemProperties);
                } else if (arg.startsWith(CommandLineConstants.START_MODE)) {
                    if (startModeSet) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.cannotSetBothAdminOnlyAndStartMode());
                        usage();
                        return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    startModeSet = true;
                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.noArgValue(arg));
                        usage();
                        return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : args[++i];
                    value = value.toLowerCase(Locale.ENGLISH);
                    switch (value) {
                        case CommandLineConstants.ADMIN_ONLY_MODE:
                            runningMode = RunningMode.ADMIN_ONLY;
                            break;
                        case CommandLineConstants.SUSPEND_MODE:
                            startSuspended = true;
                            break;
                        case CommandLineConstants.NORMAL_MODE:
                            break;
                        default:
                            STDERR.println(ServerLogger.ROOT_LOGGER.unknownStartMode(value));
                            usage();
                            return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.startsWith(CommandLineConstants.GRACEFUL_STARTUP)) {
                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(ServerLogger.ROOT_LOGGER.noArgValue(arg));
                        usage();
                        return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                    String value = (idx > -1 ? arg.substring(idx + 1) : args[++i])
                            .toLowerCase(Locale.ENGLISH);
                    if ("true".equals(value)) {
                        startGracefully = true;
                    } else if ("false".equals(value)) {
                        startGracefully = false;
                    } else {
                        STDERR.println(ServerLogger.ROOT_LOGGER.invalidCommandLineOption(arg));
                        usage();
                        return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                    }
                } else if (arg.equals(CommandLineConstants.DEBUG)) { // Need to process the debug options as they cannot be filtered out in Windows
                    // The next option may or may not be a port. Assume if it's a number and doesn't start with a - it's the port
                    final int next = i + 1;
                    if (next < argsLength) {
                        final String nextArg = args[next];
                        if (!nextArg.startsWith("-")) {
                            try {
                                Integer.parseInt(nextArg);
                                i++;
                            } catch (NumberFormatException ignore) {
                            }
                        }
                    }
                } else if (arg.equals(CommandLineConstants.SECMGR)) {
                    // do nothing, just need to filter out as Windows batch scripts cannot filter it out
                } else if(arg.startsWith(CommandLineConstants.GIT_REPO)) {
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                        final int next = i + 1;
                        if (next < argsLength) {
                            gitRepository = args[next];
                            i++;
                        } else {
                            STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                            usage();
                            return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                        }
                    } else {
                        gitRepository = arg.substring(idx + 1);
                    }
                } else if(arg.startsWith(CommandLineConstants.GIT_AUTH)) {
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                       final int next = i + 1;
                        if (next < argsLength) {
                            gitAuthConfiguration = args[next];
                            i++;
                        } else {
                            STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                            usage();
                            return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                        }
                    } else {
                        gitAuthConfiguration = arg.substring(idx + 1);
                    }
                } else if(arg.startsWith(CommandLineConstants.GIT_BRANCH)) {
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                       final int next = i + 1;
                        if (next < argsLength) {
                            gitBranch = args[next];
                            i++;
                        } else {
                            STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                            usage();
                            return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                        }
                    } else {
                        gitBranch = arg.substring(idx + 1);
                    }
                } else if(ConfigurationExtensionFactory.isConfigurationExtensionSupported()
                        && ConfigurationExtensionFactory.commandLineContainsArgument(arg)) {
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                       final int next = i + 1;
                        if (next < argsLength) {
                            supplementalConfiguration = args[next];
                            i++;
                        } else {
                            STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                            usage();
                            return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                        }
                    } else {
                        supplementalConfiguration = arg.substring(idx + 1);
                    }
                } else {
                    STDERR.println(ServerLogger.ROOT_LOGGER.invalidCommandLineOption(arg));
                    usage();
                    return new ServerEnvironmentWrapper (ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
                }
            } catch (IndexOutOfBoundsException e) {
                STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(arg));
                usage();
                return new ServerEnvironmentWrapper(ServerEnvironmentWrapper.ServerEnvironmentStatus.ERROR);
            }
        }

        String hostControllerName = null; // No host controller unless in domain mode.
        productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.HOME_DIR, null), systemProperties);
        return new ServerEnvironmentWrapper(new ServerEnvironment(hostControllerName, systemProperties, systemEnvironment,
                serverConfig, configInteractionPolicy, launchType, runningMode, productConfig, startTime, startSuspended,
                startGracefully, gitRepository, gitBranch, gitAuthConfiguration, supplementalConfiguration));
    }

    private static void assertSingleConfig(String serverConfig) {
        if (serverConfig != null) {
            throw ServerLogger.ROOT_LOGGER.cannotHaveBothInitialServerConfigAndServerConfig();
        }
    }

    private static String parseValue(final String arg, final String key) {
        String value = null;
        int splitPos = key.length();
        if (arg.length() <= splitPos + 1 || arg.charAt(splitPos) != '=') {
            usage();
        } else {
            value = arg.substring(splitPos + 1);
        }
        return value;
    }

    private static String fixPossibleIPv6URL(String val) {
        String result = val;
        if (val != null && val.length() > 2
                && val.charAt(0) == '[' && val.charAt(val.length() - 1) == ']'
                && val.contains(":")) {
            result = val.substring(1, val.length() - 1);
        }
        return result;
    }

    private static boolean processProperties(final String arg, final String urlSpec, Properties systemProperties) {
         URL url = null;
         try {
             url = makeURL(urlSpec);
             systemProperties.load(url.openConnection().getInputStream());
             return true;
         } catch (MalformedURLException e) {
             STDERR.println(ServerLogger.ROOT_LOGGER.malformedCommandLineURL(urlSpec, arg));
             usage();
             return false;
         } catch (IOException e) {
             STDERR.println(ServerLogger.ROOT_LOGGER.unableToLoadProperties(url));
             usage();
             return false;
         }
    }

    private static URL makeURL(String urlspec) throws MalformedURLException {
        urlspec = urlspec.trim();

        URL url;

        try {
            url = new URL(urlspec);
            if (url.getProtocol().equals("file")) {
                // make sure the file is absolute & canonical file url
                File file = new File(url.getFile()).getCanonicalFile();
                url = file.toURI().toURL();
            }
        } catch (Exception e) {
            // make sure we have an absolute & canonical file url
            try {
                File file = new File(urlspec).getCanonicalFile();
                url = file.toURI().toURL();
            } catch (Exception n) {
                throw new MalformedURLException(n.toString());
            }
        }

        return url;
    }

    private static void processSecurityProperties(String secProperties, Properties systemProperties){
        StringTokenizer tokens = new StringTokenizer(secProperties, ",");
        while(tokens.hasMoreTokens()){
            String token = tokens.nextToken();

            int idx = token.indexOf('=');
            if (idx == token.length() - 1) {
                STDERR.println(ServerLogger.ROOT_LOGGER.valueExpectedForCommandLineOption(secProperties));
                usage();
                return;
            }
            String value = token.substring(idx + 1);
            String key = token.substring(0, idx);
            systemProperties.setProperty(key, value);
        }
    }
}
