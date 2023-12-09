/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.operations.common.ProcessEnvironment;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.process.CommandLineArgumentUsageImpl;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.process.ProcessController;
import org.jboss.as.process.protocol.StreamUtils;
import org.jboss.as.process.stdin.Base64InputStream;
import org.jboss.as.server.SystemExiter;
import org.jboss.as.version.ProductConfig;
import org.jboss.logging.MDC;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.modules.Module;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The main-class entry point for the host controller process.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:kkhan@redhat.com">Kabir Khan</a>
 * @author Brian Stansberry
 */
public final class Main {
    // Capture System.out and System.err before they are redirected by STDIO
    private static final PrintStream STDOUT = System.out;
    private static final PrintStream STDERR = System.err;

    private static final String PROCESS_NAME = "-D[Host Controller]";

    /**
     * The main method.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) throws IOException {
        MDC.put("process", "host controller");


        // Grab copies of our streams.
        final InputStream in = System.in;
        //final PrintStream out = System.out;
        //final PrintStream err = System.err;

        byte[] pcAuthKey = new byte[ProcessController.AUTH_BYTES_ENCODED_LENGTH];
        try {
            StreamUtils.readFully(new Base64InputStream(System.in), pcAuthKey);
        } catch (IOException e) {
            STDERR.println(HostControllerLogger.ROOT_LOGGER.failedToReadAuthenticationKey(e));
            fail();
            return;
        }

        // Make sure our original stdio is properly captured.
        try {
            Class.forName(ConsoleHandler.class.getName(), true, ConsoleHandler.class.getClassLoader());
        } catch (Throwable ignored) {
        }

        // Install JBoss Stdio to avoid any nasty crosstalk.
        StdioContext.install();
        final StdioContext context = StdioContext.create(
            new NullInputStream(),
            new LoggingOutputStream(Logger.getLogger("stdout"), Level.INFO),
            new LoggingOutputStream(Logger.getLogger("stderr"), Level.ERROR)
        );
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(context));

        create(args, new String(pcAuthKey, StandardCharsets.US_ASCII));

        while (in.read() != -1) {}
        exit();
    }

    private Main() {
    }

    public static HostControllerBootstrap create(String[] args, final String authCode) {
        Main main = new Main();
        return main.boot(args, authCode);
    }

    private HostControllerBootstrap boot(String[] args, final String authCode) {
        try {
            // TODO make this settable via an embedding process
            final long startTime = Module.getStartTime();
            final HostControllerEnvironmentWrapper hostControllerEnvironmentWrapper = determineEnvironment(args, startTime);
            if (hostControllerEnvironmentWrapper.getHostControllerEnvironment() == null) {
                usage(hostControllerEnvironmentWrapper.getProductConfig()); // In case there was an error determining the environment print the usage
                if (hostControllerEnvironmentWrapper.getHostControllerEnvironmentStatus() == HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR) {
                    abort();
                } else {
                    SystemExiter.safeAbort();
                }
                return null;
            } else {
                try {
                    final HostControllerBootstrap hc = new HostControllerBootstrap(hostControllerEnvironmentWrapper.getHostControllerEnvironment(), authCode);
                    hc.bootstrap();
                    return hc;
                } catch(Throwable t) {
                    abort(t);
                    return null;
                }
            }
        } catch (Throwable t) {
            abort(t);
            return null;
        }
    }

    /**
     * Terminates process with an exit code that will trigger shutdown of the process controller as well if there
     * are no running servers. JVM shuts down with {@link ExitCodes#HOST_CONTROLLER_ABORT_EXIT_CODE}.
     * @param t the throwable that triggered abort
     */
    private static void abort(Throwable t) {
        try {
            if (t != null) {
                t.printStackTrace();
            }
        } finally {
            abort();
        }
    }

    private static void abort() {
        SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
    }

    /**
     * Terminates JVM with exit code: 0 - normal termination.
     */
    private static void exit(){
        SystemExiter.logAndExit(HostControllerLogger.ROOT_LOGGER::shuttingDownInResponseToProcessControllerSignal, ExitCodes.NORMAL);
    }

    /**
     * Terminates JVM with exit code: 1 - failed termination but not an abort situation.
     */
    private static void fail(){
        SystemExiter.abort(ExitCodes.FAILED);
    }

    private static void usage(ProductConfig productConfig) {
        CommandLineArgumentUsageImpl.printUsage(productConfig, STDOUT);
    }

    public static HostControllerEnvironmentWrapper determineEnvironment(String[] args, long startTime) {
        return determineEnvironment(args, startTime, ProcessType.HOST_CONTROLLER);
    }

    public static HostControllerEnvironmentWrapper determineEnvironment(String[] args, long startTime, ProcessType processType) {
        Integer pmPort = null;
        InetAddress pmAddress = null;
        final PCSocketConfig pcSocketConfig = new PCSocketConfig();
        String defaultJVM = null;
        boolean isRestart = false;
        boolean backupDomainFiles = false;
        boolean cachedDc = false;
        String domainConfig = null;
        String initialDomainConfig = null;
        String hostConfig = null;
        String initialHostConfig = null;
        RunningMode initialRunningMode = RunningMode.NORMAL;
        Map<String, String> hostSystemProperties = getHostSystemProperties();
        ProductConfig productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), WildFlySecurityManager.getPropertyPrivileged(HostControllerEnvironment.HOME_DIR, null), null);
        ConfigurationFile.InteractionPolicy hostConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.STANDARD;
        ConfigurationFile.InteractionPolicy domainConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.STANDARD;
        String modulePath = null;

        // Note the java.security.manager property shouldn't be set, but we'll check to ensure the security manager should be enabled
        boolean securityManagerEnabled = System.getSecurityManager() != null || isJavaSecurityManagerConfigured(hostSystemProperties);

        final int argsLength = args.length;
        for (int i = 0; i < argsLength; i++) {
            final String arg = args[i];

            try {
                if(PROCESS_NAME.equals(arg)) {
                    // Skip the process name
                } else if (CommandLineConstants.PROPERTIES.equals(arg) || CommandLineConstants.OLD_PROPERTIES.equals(arg)
                        || CommandLineConstants.SHORT_PROPERTIES.equals(arg)) {
                    // Set system properties from url/file
                    if (!processProperties(arg, args[++i], hostSystemProperties)) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec, hostSystemProperties)) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.SHORT_PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.SHORT_PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec, hostSystemProperties)) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                }  else if (arg.startsWith(CommandLineConstants.OLD_PROPERTIES)) {
                    String urlSpec = parseValue(arg, CommandLineConstants.OLD_PROPERTIES);
                    if (urlSpec == null || !processProperties(arg, urlSpec, hostSystemProperties)) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT.equals(arg)) {
                    final String port = args[++i];
                    try {
                        pmPort = Integer.valueOf(port);
                    } catch (NumberFormatException e) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.invalidValue(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT, "Integer", port, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT)) {
                    String val = parseValue(arg, CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    final Integer port = parsePort(val, CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT);
                    if (port == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    pmPort = port;
                } else if (CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR.equals(arg)) {
                    final String addr = args[++i];
                    try {
                        pmAddress = InetAddress.getByName(addr);
                    } catch (UnknownHostException e) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.unknownHostValue(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR, addr, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR)) {
                    final String val = parseValue(arg, CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    final InetAddress addr = parseAddress(val, arg);
                    if (addr == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    pmAddress = addr;
                } else if (pcSocketConfig.processPCSocketConfigArgument(arg, args, i)) {
                    if (pcSocketConfig.isParseFailed()) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    i += pcSocketConfig.getArgIncrement();
                } else if (CommandLineConstants.RESTART_HOST_CONTROLLER.equals(arg)) {
                    isRestart = true;
                } else if (CommandLineConstants.BACKUP_DC.equals(arg) || CommandLineConstants.OLD_BACKUP_DC.equals(arg)) {
                    backupDomainFiles = true;
                } else if (CommandLineConstants.CACHED_DC.equals(arg) || CommandLineConstants.OLD_CACHED_DC.equals(arg)) {
                    cachedDc = true;
                } else if(CommandLineConstants.DEFAULT_JVM.equals(arg) || CommandLineConstants.OLD_DEFAULT_JVM.equals(arg)) {
                    defaultJVM = checkValueIsNotAnArg(arg, args[++i]);
                    if (defaultJVM == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (CommandLineConstants.DOMAIN_CONFIG.equals(arg)
                        || CommandLineConstants.SHORT_DOMAIN_CONFIG.equals(arg)
                        || CommandLineConstants.OLD_DOMAIN_CONFIG.equals(arg)) {
                    domainConfig = checkValueIsNotAnArg(arg, args[++i]);
                    if (domainConfig == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.DOMAIN_CONFIG)) {
                    String val = parseValue(arg, CommandLineConstants.DOMAIN_CONFIG);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    domainConfig = val;
                } else if (arg.startsWith(CommandLineConstants.SHORT_DOMAIN_CONFIG)) {
                    String val = parseValue(arg, CommandLineConstants.SHORT_DOMAIN_CONFIG);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    domainConfig = val;
                } else if (arg.startsWith(CommandLineConstants.OLD_DOMAIN_CONFIG)) {
                    String val = parseValue(arg, CommandLineConstants.OLD_DOMAIN_CONFIG);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    domainConfig = val;
                } else if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER && arg.startsWith("--empty-host-config")) {
                    // don't reset to NEW if its already DISCARD
                    if (hostConfigInteractionPolicy != ConfigurationFile.InteractionPolicy.DISCARD) {
                        hostConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.NEW;
                    }
                } else if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER && arg.startsWith("--remove-existing-host-config")) {
                    hostConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.DISCARD;
                } else if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER && arg.startsWith("--empty-domain-config")) {
                    if (domainConfigInteractionPolicy != ConfigurationFile.InteractionPolicy.DISCARD) {
                        domainConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.NEW;
                    }
                } else if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER && arg.startsWith("--remove-existing-domain-config")) {
                    domainConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.DISCARD;
                } else if (arg.startsWith(CommandLineConstants.READ_ONLY_DOMAIN_CONFIG)) {
                    initialDomainConfig = parseValue(arg, CommandLineConstants.READ_ONLY_DOMAIN_CONFIG);
                    domainConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.READ_ONLY;
                    if (initialDomainConfig == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (CommandLineConstants.HOST_CONFIG.equals(arg) || CommandLineConstants.OLD_HOST_CONFIG.equals(arg)) {
                    hostConfig = checkValueIsNotAnArg(arg, args[++i]);
                    if (hostConfig == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.HOST_CONFIG)) {
                    String val = parseValue(arg, CommandLineConstants.HOST_CONFIG);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    hostConfig = val;
                } else if (arg.startsWith(CommandLineConstants.OLD_HOST_CONFIG)) {
                    String val = parseValue(arg, CommandLineConstants.OLD_HOST_CONFIG);
                    if (val == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    hostConfig = val;
                } else if (arg.startsWith(CommandLineConstants.READ_ONLY_HOST_CONFIG)) {
                    initialHostConfig = parseValue(arg, CommandLineConstants.READ_ONLY_HOST_CONFIG);
                    hostConfigInteractionPolicy = ConfigurationFile.InteractionPolicy.READ_ONLY;
                    if (initialHostConfig == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.startsWith(CommandLineConstants.PRIMARY_ADDRESS)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentExpected(arg, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : checkValueIsNotAnArg(arg, args[++i]);
                    if (value == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    value = fixPossibleIPv6URL(value);
                    hostSystemProperties.put(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, value);
                    WildFlySecurityManager.setPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_ADDRESS, value);
                } else if (arg.startsWith(CommandLineConstants.PRIMARY_PORT)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentExpected(arg, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : args[++i];
                    final Integer port = parsePort(value, CommandLineConstants.PRIMARY_PORT);
                    if (port == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }

                    hostSystemProperties.put(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_PORT, value);
                    WildFlySecurityManager.setPropertyPrivileged(HostControllerEnvironment.JBOSS_DOMAIN_PRIMARY_PORT, value);
                } else if (CommandLineConstants.ADMIN_ONLY.equals(arg)) {
                    initialRunningMode = RunningMode.ADMIN_ONLY;
                } else if (arg.startsWith(CommandLineConstants.SYS_PROP)) {

                    // set a system property
                    String name, value;
                    int idx = arg.indexOf("=");
                    if (idx == -1) {
                        name = arg.substring(2);
                        value = "true";
                    } else {
                        name = arg.substring(2, idx);
                        value = arg.substring(idx + 1, arg.length());
                    }
                    // Skip -Djava.security.manager, we don't want that added as a system property
                    if (!"java.security.manager".equals(name)) {
                        WildFlySecurityManager.setPropertyPrivileged(name, value);
                        hostSystemProperties.put(name, value);
                    }
                } else if (arg.startsWith(CommandLineConstants.PUBLIC_BIND_ADDRESS)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentExpected(arg, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : checkValueIsNotAnArg(arg, args[++i]);
                    if (value == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    value = fixPossibleIPv6URL(value);
                    String propertyName;
                    if (idx < 0) {
                        // -b xxx -bmanagement xxx
                        propertyName = arg.length() == 2 ? HostControllerEnvironment.JBOSS_BIND_ADDRESS : HostControllerEnvironment.JBOSS_BIND_ADDRESS_PREFIX + arg.substring(2);
                    } else if (idx == 2) {
                        // -b=xxx
                        propertyName = HostControllerEnvironment.JBOSS_BIND_ADDRESS;
                    } else {
                        // -bmanagement=xxx
                        propertyName =  HostControllerEnvironment.JBOSS_BIND_ADDRESS_PREFIX + arg.substring(2, idx);
                    }
                    hostSystemProperties.put(propertyName, value);
                    WildFlySecurityManager.setPropertyPrivileged(propertyName, value);
                } else if (arg.startsWith(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS)) {

                    int idx = arg.indexOf('=');
                    if (idx == arg.length() - 1) {
                        STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentExpected(arg, usageNote()));
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    String value = idx > -1 ? arg.substring(idx + 1) : checkValueIsNotAnArg(arg, args[++i]);
                    if (value == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    value = fixPossibleIPv6URL(value);
                    hostSystemProperties.put(HostControllerEnvironment.JBOSS_DEFAULT_MULTICAST_ADDRESS, value);
                    WildFlySecurityManager.setPropertyPrivileged(HostControllerEnvironment.JBOSS_DEFAULT_MULTICAST_ADDRESS, value);
                } else if (arg.equals(CommandLineConstants.MODULE_PATH)) {
                    modulePath = checkValueIsNotAnArg(arg, args[++i]);
                    if (modulePath == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                } else if (arg.equals(CommandLineConstants.SECMGR)) {
                    // Enable the security manager
                    securityManagerEnabled = true;
                } else if ((productConfig.getStabilitySet().size() > 1) && arg.startsWith(CommandLineConstants.STABILITY)) {
                    String stabilityName = (arg.length() == CommandLineConstants.STABILITY.length()) ? args[++i] : parseValue(arg, CommandLineConstants.STABILITY);
                    if (stabilityName == null) {
                        return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                    }
                    hostSystemProperties.put(ProcessEnvironment.STABILITY, stabilityName);
                } else {
                    STDERR.println(HostControllerLogger.ROOT_LOGGER.invalidOption(arg, usageNote()));
                    return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
                }
            } catch (IndexOutOfBoundsException e) {
                STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentExpected(arg, usageNote()));
                return new HostControllerEnvironmentWrapper(HostControllerEnvironmentWrapper.HostControllerEnvironmentStatus.ERROR, productConfig);
            }
        }
        // Recreate using system properties
        productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), WildFlySecurityManager.getPropertyPrivileged(HostControllerEnvironment.HOME_DIR, null), hostSystemProperties);

        return new HostControllerEnvironmentWrapper(new HostControllerEnvironment(hostSystemProperties, isRestart, modulePath,
                pmAddress, pmPort, pcSocketConfig.getBindAddress(), pcSocketConfig.getBindPort(), defaultJVM, domainConfig,
                initialDomainConfig, hostConfig, initialHostConfig, initialRunningMode, backupDomainFiles, cachedDc,
                productConfig, securityManagerEnabled, startTime, processType, hostConfigInteractionPolicy, domainConfigInteractionPolicy));
    }

    private static boolean isJavaSecurityManagerConfigured(final Map<String, String> props) {
        final String value = props.get("java.security.manager");
        return value != null && !"allow".equals(value) && !"disallow".equals(value);
    }

    private static String parseValue(final String arg, final String key) {
        int splitPos = key.length();
        if (arg.length() <= splitPos + 1 || arg.charAt(splitPos) != '=') {
            STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentHasNoValue(arg, usageNote()));
            return null;
        } else {
            return arg.substring(splitPos + 1);
        }
    }

    /**
     * Validates that param {@code value} does not begin with the character {@code -}. For use in cases where
     * the legal value for an argument would not begin with that character. Usage is to detect missing argument
     * values, where the command line includes another argument instead of the value for the last argument.
     *
     * @param argument the last argument, whose value should be {@code value}
     * @param value the next item in the command line arguments, which should be the value for {@code argument}
     * @return  {@code value} if it is valid, or {@code null} if it is not
     */
    private static String checkValueIsNotAnArg(String argument, String value) {
        if (value.startsWith("-")) {
            STDERR.println(HostControllerLogger.ROOT_LOGGER.argumentHasNoValue(argument, usageNote()));
            return null;
        }
        return value;
    }

    private static boolean processProperties(final String arg, final String urlSpec, Map<String, String> hostSystemProperties) {
         URL url = null;
         try {
             url = makeURL(urlSpec);
             Properties props = new Properties();
             props.load(url.openConnection().getInputStream());

             WildFlySecurityManager.getSystemPropertiesPrivileged().putAll(props);
             for (Map.Entry<Object, Object> entry : props.entrySet()) {
                 hostSystemProperties.put((String)entry.getKey(), (String)entry.getValue());
             }
             return true;
         } catch (MalformedURLException e) {
             STDERR.println(HostControllerLogger.ROOT_LOGGER.malformedUrl(arg, usageNote()));
             return false;
         } catch (IOException e) {
             STDERR.println(HostControllerLogger.ROOT_LOGGER.unableToLoadProperties(url, usageNote()));
             return false;
         }
    }

    private static Integer parsePort(final String value, final String key) {
         try {
             return Integer.valueOf(value);
         } catch (NumberFormatException e) {
             STDERR.println(HostControllerLogger.ROOT_LOGGER.invalidValue(key, "Integer", value, usageNote()));
             return null;
         }
    }

    private static InetAddress parseAddress(final String value, final String key) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            STDERR.println(HostControllerLogger.ROOT_LOGGER.unknownHostValue(key, value, usageNote()));
            return null;
        }
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

    private static Map<String, String> getHostSystemProperties() {
        final Map<String, String> hostSystemProperties = new HashMap<String, String>();
        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            String propertyName, propertyValue;
            for (String arg : runtime.getInputArguments()) {
                if (arg != null && arg.length() > 2 && arg.startsWith("-D")) {
                    arg = arg.substring(2);
                    int equalIndex = arg.indexOf("=");
                    if (equalIndex >= 0) {
                        //Things like -Djava.security.policy==/Users/kabir/tmp/permit.policy will end up here, and the extra '=' needs to be part of the value,
                        //see http://docs.oracle.com/javase/6/docs/technotes/guides/security/PolicyFiles.html
                        propertyName = arg.substring(0, equalIndex);
                        propertyValue = arg.substring(equalIndex + 1);
                    } else {
                        propertyName = arg;
                        propertyValue = null;
                    }
                    if (!hostSystemProperties.containsKey(propertyName)) {
                        hostSystemProperties.put(propertyName, propertyValue);
                    }
                }
            }
        } catch (Exception e) {
            STDERR.println(HostControllerLogger.ROOT_LOGGER.cannotAccessJvmInputArgument(e));
        }
        return hostSystemProperties;
    }

    private static String usageNote() {
        boolean isWindows = (WildFlySecurityManager.getPropertyPrivileged("os.name", null)).toLowerCase(Locale.ENGLISH).contains("windows");
        String command = isWindows ? "domain" : "domain.sh";
        return HostControllerLogger.ROOT_LOGGER.usageNote(command);
    }

    private static class PCSocketConfig {
        private final String defaultBindAddress;
        private InetAddress bindAddress;
        private int bindPort = 0;
        private int argIncrement = 0;
        private boolean parseFailed;
        private final UnknownHostException uhe;

        private PCSocketConfig() {
            boolean preferIPv6 = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged("java.net.preferIPv6Addresses", "false"));
            this.defaultBindAddress = preferIPv6 ? "::1" : "127.0.0.1";
            UnknownHostException toCache = null;
            try {
                bindAddress = InetAddress.getByName(defaultBindAddress);
            } catch (UnknownHostException e) {
                try {
                    bindAddress = InetAddressUtil.getLocalHost();
                } catch (UnknownHostException uhe) {
                    toCache = uhe;
                }
            }
            uhe = toCache;
        }

        private InetAddress getBindAddress() {
            if (bindAddress == null) {
                throw HostControllerLogger.ROOT_LOGGER.cannotObtainValidDefaultAddress(uhe, defaultBindAddress, CommandLineConstants.INTERPROCESS_HC_ADDRESS);
            }
            return bindAddress;
        }

        private int getBindPort() {
            return bindPort;
        }

        private int getArgIncrement() {
            return argIncrement;
        }

        private boolean isParseFailed() {
            return parseFailed;
        }

        private boolean processPCSocketConfigArgument(final String arg, final String[] args, final int index) {
            boolean isPCSocketArg = true;

            argIncrement = 0;

            if (CommandLineConstants.INTERPROCESS_HC_ADDRESS.equals(arg) || CommandLineConstants.OLD_INTERPROCESS_HC_ADDRESS.equals(arg)) {
                setBindAddress(arg, args[index +1]);
                argIncrement = 1;
            } else if (arg.startsWith(CommandLineConstants.INTERPROCESS_HC_ADDRESS)) {
                String addr = parseValue(arg, CommandLineConstants.INTERPROCESS_HC_ADDRESS);
                if (addr == null) {
                    parseFailed = true;
                } else {
                    setBindAddress(arg, addr);
                }
            } else if (arg.startsWith(CommandLineConstants.OLD_INTERPROCESS_HC_ADDRESS)) {
                String addr = parseValue(arg, CommandLineConstants.OLD_INTERPROCESS_HC_ADDRESS);
                if (addr == null) {
                    parseFailed = true;
                } else {
                    setBindAddress(arg, addr);
                }
            } else if (CommandLineConstants.INTERPROCESS_HC_PORT.equals(arg) || CommandLineConstants.OLD_INTERPROCESS_HC_PORT.equals(arg)) {
                bindPort = Integer.parseInt(args[index + 1]);
                argIncrement = 1;
            } else if (arg.startsWith(CommandLineConstants.INTERPROCESS_HC_PORT)) {
                String port = parseValue(arg, CommandLineConstants.INTERPROCESS_HC_PORT);
                if (port == null) {
                    parseFailed = true;
                } else {
                    bindPort = Integer.parseInt(port);
                }
            } else if (arg.startsWith(CommandLineConstants.OLD_INTERPROCESS_HC_PORT)) {
                String port = parseValue(arg, CommandLineConstants.OLD_INTERPROCESS_HC_PORT);
                if (port == null) {
                    parseFailed = true;
                } else {
                    bindPort = Integer.parseInt(port);
                }
            } else {
                isPCSocketArg = false;
            }

            return isPCSocketArg;
        }

        private void setBindAddress(String key, String value) {
            try {
                bindAddress = InetAddress.getByName(value);
            } catch (UnknownHostException e) {
                parseFailed = true;
                STDERR.println(HostControllerLogger.ROOT_LOGGER.invalidValue(key, "InetAddress", value, usageNote()));
            }
        }
    }
}
