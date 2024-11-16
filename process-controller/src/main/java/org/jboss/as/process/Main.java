/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import static java.security.AccessController.doPrivileged;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Handler;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.process.protocol.ProtocolServer;
import org.jboss.as.version.ProductConfig;
import org.jboss.as.version.Version;
import org.jboss.logging.MDC;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.jboss.modules.Module;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The main entry point for the process controller.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Main {

    public static String getVersionString() {
        return Version.AS_VERSION;
    }

    private static void usage(ProductConfig productConfig) {
        CommandLineArgumentUsageImpl.printUsage(productConfig, System.out);
    }

    private Main() {
    }

    public static final String HOST_CONTROLLER_PROCESS_NAME = "Host Controller";
    public static final String HOST_CONTROLLER_MODULE = "org.jboss.as.host-controller";

    public static void main(String[] args) throws IOException {

        start(args);
    }

    public static ProcessController start(String[] args) throws IOException {
        MDC.put("process", "process controller");

        String javaHome = WildFlySecurityManager.getPropertyPrivileged("java.home", ".");
        String jvmName = javaHome + "/bin/java";
        String jbossHome = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", ".");
        String modulePath = null;
        String bootJar = null;
        String bootModule = HOST_CONTROLLER_MODULE;
        ProductConfig productConfig = ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), jbossHome, null);
        final PCSocketConfig pcSocketConfig = new PCSocketConfig(productConfig);
        String currentWorkingDir = WildFlySecurityManager.getPropertyPrivileged("user.dir", null);

        final List<String> javaOptions = new ArrayList<String>();
        final List<String> smOptions = new ArrayList<String>();

        // target module is always SM
        // -mp is my module path
        // -jar is jboss-modules.jar in jboss-home
        // log config should be fixed loc

        // If the security manager is installed assume we need to use -secmgr
        boolean securityManagerEnabled = System.getSecurityManager() != null;
        OUT: for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-jvm".equals(arg)) {
                jvmName = args[++i];
            } else if ("-jboss-home".equals(arg)) {
                jbossHome = args[++i];
            } else if ("-mp".equals(arg)) {
                modulePath = args[++i];
            } else if ("-jar".equals(arg)) {
                bootJar = args[++i];
            } else if ("-secmgr".equals(arg)) {
                securityManagerEnabled = true;
            } else if ("--".equals(arg)) {
                for (i++; i < args.length; i++) {
                    arg = args[i];
                    if ("--".equals(arg)) {
                        for (i++; i < args.length; i++) {
                            arg = args[i];
                            if (handleHelpOrVersion(productConfig, arg, jbossHome)) {
                                return null;
                            } else if (pcSocketConfig.processPCSocketConfigArgument(arg, args, i)) {
                                if (pcSocketConfig.isParseFailed()) {
                                    return null;
                                }
                                i += pcSocketConfig.getArgIncrement();
                            } else if (arg.startsWith("-D" + CommandLineConstants.PREFER_IPV4_STACK + "=")) {
                                // AS7-5409 set the property for this process and pass it to HC via javaOptions
                                String val = parseValue(productConfig, arg, "-D" + CommandLineConstants.PREFER_IPV4_STACK);
                                WildFlySecurityManager.setPropertyPrivileged(CommandLineConstants.PREFER_IPV4_STACK, val);
                                addJavaOption(arg, javaOptions);
                            } else if (arg.startsWith("-D" + CommandLineConstants.PREFER_IPV6_ADDRESSES + "=")) {
                                // AS7-5409 set the property for this process and pass it to HC via javaOptions
                                String val = parseValue(productConfig, arg, "-D" + CommandLineConstants.PREFER_IPV6_ADDRESSES);
                                WildFlySecurityManager.setPropertyPrivileged(CommandLineConstants.PREFER_IPV6_ADDRESSES, val);
                                addJavaOption(arg, javaOptions);

                            } else {
                                addJavaOption(arg, smOptions);
                            }
                        }
                        break OUT;
                    } else if (handleHelpOrVersion(productConfig, arg, jbossHome)) {
                        // This would normally come in via the nested if ("--".equals(arg)) case above, but in case someone tweaks the
                        // script to set it directly, we've handled it
                        return null;
                    } else if (pcSocketConfig.processPCSocketConfigArgument(arg, args, i)) {
                        // This would normally come in via the nested if ("--".equals(arg)) case above, but in case someone tweaks the
                        // script to set it directly, we've handled it
                        if (pcSocketConfig.isParseFailed()) {
                            return null;
                        }
                        i += pcSocketConfig.getArgIncrement();
                    } else {
                        // Windows batch scripts can't filter out parameters, ignore the -Djava.security.manager system property
                        if (isJavaSecurityManagerConfigured(arg)) {
                            // Turn on the security manager
                            securityManagerEnabled = true;
                        } else {
                            addJavaOption(arg, javaOptions);
                        }
                    }
                }
                break OUT;
            } else if (handleHelpOrVersion(productConfig, arg, jbossHome)) {
                // This would normally come in via the if ("--".equals(arg)) cases above, but in case someone tweaks the
                // script to set it directly, we've handled it)
                return null;
            } else if (pcSocketConfig.processPCSocketConfigArgument(arg, args, i)) {
                // This would normally come in via the if ("--".equals(arg)) cases above, but in case someone tweaks the
                // script to set it directly, we've handled it
                if (pcSocketConfig.isParseFailed()) {
                    return null;
                }
                i += pcSocketConfig.getArgIncrement();
            } else {
                throw ProcessLogger.ROOT_LOGGER.invalidOption(arg);
            }
        }
        if (modulePath == null) {
            // if "-mp" (i.e. module path) wasn't part of the command line args, then check the system property.
            // if system property not set, then default to JBOSS_HOME/modules
            // TODO: jboss-modules setting module.path is not a reliable API; log a WARN or something if we get here
            modulePath = WildFlySecurityManager.getPropertyPrivileged("module.path", jbossHome + File.separator + "modules");
        }
        if (bootJar == null) {
            // if "-jar" wasn't part of the command line args, then default to JBOSS_HOME/jboss-modules.jar
            bootJar = jbossHome + File.separator + "jboss-modules.jar";
        }


        Handler consoleHandler = null;

        final Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                if (consoleHandler != null) {
                    // duplicate handlers
                    rootLogger.removeHandler(handler);
                } else {
                    consoleHandler = handler;
                    ((ConsoleHandler)consoleHandler).setWriter(new SynchronizedWriter(System.out));
                }
            }
        }

        final ProtocolServer.Configuration configuration = new ProtocolServer.Configuration();
        InetAddress pcInetAddress = InetAddress.getByName(pcSocketConfig.getBindAddress());
        InetSocketAddress pcInetSocketAddress = new InetSocketAddress(pcInetAddress, pcSocketConfig.getBindPort());
        configuration.setBindAddress(pcInetSocketAddress);
        configuration.setSocketFactory(ServerSocketFactory.getDefault());
        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
            public JBossThreadFactory run() {
                return new JBossThreadFactory(new ThreadGroup("ProcessController-threads"), Boolean.FALSE, null, "%G - %t", null, null);
            }
        });
        configuration.setThreadFactory(threadFactory);
        configuration.setReadExecutor(Executors.newCachedThreadPool(threadFactory));

        final ProcessController processController = new ProcessController(configuration, System.out, System.err);
        final InetSocketAddress boundAddress = processController.getServer().getBoundAddress();

        final List<String> initialCommand = new ArrayList<String>();
        initialCommand.add(jvmName);
        initialCommand.add("-D[" + HOST_CONTROLLER_PROCESS_NAME + "]");
        initialCommand.addAll(javaOptions);
        initialCommand.add("-jar");
        initialCommand.add(bootJar);
        // Optionally pass the security manager property to the host controller
        if (securityManagerEnabled){
            initialCommand.add("-secmgr");
        }
        initialCommand.add("-mp");
        initialCommand.add(modulePath);
        initialCommand.add(bootModule);
        // Optionally pass the security manager property to the process controller
        if (securityManagerEnabled){
            initialCommand.add("-secmgr");
        }
        initialCommand.add("-mp");  // Repeat the module path so HostController's Main sees it
        initialCommand.add(modulePath);
        initialCommand.add(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR);
        initialCommand.add(boundAddress.getAddress().getHostAddress());
        initialCommand.add(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT);
        initialCommand.add(Integer.toString(boundAddress.getPort()));
        initialCommand.addAll(smOptions);
        initialCommand.add("-D" + "jboss.home.dir=" + jbossHome);

        processController.addProcess(HOST_CONTROLLER_PROCESS_NAME, -1, initialCommand, Collections.<String, String>emptyMap(), currentWorkingDir, true, true);
        processController.startProcess(HOST_CONTROLLER_PROCESS_NAME);

        final Thread shutdownThread = new Thread(new Runnable() {
            public void run() {
                processController.shutdown();
            }
        }, "Shutdown thread");
        shutdownThread.setDaemon(false);
        Runtime.getRuntime().addShutdownHook(shutdownThread);

        return processController;
    }

    private static boolean isJavaSecurityManagerConfigured(final String arg) {
        // [WFCORE-7064] Setting SM is not allowed on JDK24+
        return Runtime.version().feature() < 24 && arg.startsWith("-Djava.security.manager")
                && !"-Djava.security.manager=allow".equals(arg)
                && !"-Djava.security.manager=disallow".equals(arg);
    }

    private static String parseValue(ProductConfig productConfig, final String arg, final String key) {
        String value = null;
        int splitPos = key.length();
        if (arg.length() <= splitPos + 1 || arg.charAt(splitPos) != '=') {
            System.out.println(ProcessLogger.ROOT_LOGGER.noArgValue(key));
            usage(productConfig);
        } else {
            value = arg.substring(splitPos + 1);
        }
        return value;
    }

    private static void addJavaOption(String option, List<String> javaOptions) {

        // Remove any existing -D options that this one replaces
        if (option.startsWith("-D")) {
            String key;
            int splitPos = option.indexOf('=');
            if (splitPos < 0) {
                key = option;
            } else {
                key = option.substring(0, splitPos);
            }
            for (Iterator<String> iter = javaOptions.iterator(); iter.hasNext();) {
                String existingOp = iter.next();
                if (existingOp.equals(key) || (existingOp.startsWith(key) && existingOp.indexOf('=') == key.length())) {
                    iter.remove();
                }
            }
        }

        javaOptions.add(option);
    }

    private static boolean handleHelpOrVersion(ProductConfig productConfig, String arg, String jbossHome) {
        if (CommandLineConstants.HELP.equals(arg) || CommandLineConstants.SHORT_HELP.equals(arg)
            || CommandLineConstants.OLD_HELP.equals(arg)) {
            usage(productConfig);
            return true;
        } else if (CommandLineConstants.VERSION.equals(arg) || CommandLineConstants.SHORT_VERSION.equals(arg)
                || CommandLineConstants.OLD_VERSION.equals(arg) || CommandLineConstants.OLD_SHORT_VERSION.equals(arg)) {
            System.out.println(ProductConfig.fromFilesystemSlot(Module.getBootModuleLoader(), jbossHome, null).getPrettyVersionString());
            return true;
        }
        return false;
    }

    private static class PCSocketConfig {
        private final ProductConfig productConfig;
        private String bindAddress;
        private int bindPort = 0;
        private int argIncrement = 0;
        private boolean parseFailed;

        private PCSocketConfig(ProductConfig productConfig) {
            this.productConfig = productConfig;
        }

        private String getBindAddress() {
            if (bindAddress != null) {
                return bindAddress;
            } else {
                boolean v4Stack = Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged(CommandLineConstants.PREFER_IPV4_STACK, "false"));
                boolean useV6 = !v4Stack && Boolean.parseBoolean(WildFlySecurityManager.getPropertyPrivileged(CommandLineConstants.PREFER_IPV6_ADDRESSES, "false"));
                return useV6 ? "::1" : "127.0.0.1";
            }
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

            if (CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR.equals(arg) || CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_ADDR.equals(arg)) {
                bindAddress = args[index +1];
                argIncrement = 1;
            } else if (arg.startsWith(CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR)) {
                String addr = parseValue(this.productConfig, arg, CommandLineConstants.PROCESS_CONTROLLER_BIND_ADDR);
                if (addr == null) {
                    parseFailed = true;
                } else {
                    bindAddress = addr;
                }
            } else if (arg.startsWith(CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_ADDR)) {
                String addr = parseValue(this.productConfig, arg, CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_ADDR);
                if (addr == null) {
                    parseFailed = true;
                } else {
                    bindAddress = addr;
                }
            } else if (CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT.equals(arg) || CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_PORT.equals(arg)) {
                bindPort = Integer.parseInt(args[index + 1]);
                argIncrement = 1;
            } else if (arg.startsWith(CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT)) {
                String port = parseValue(this.productConfig, arg, CommandLineConstants.PROCESS_CONTROLLER_BIND_PORT);
                if (port == null) {
                    parseFailed = true;
                } else {
                    bindPort = Integer.parseInt(port);
                }
            } else if (arg.startsWith(CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_PORT)) {
                String port = parseValue(this.productConfig, arg, CommandLineConstants.OLD_PROCESS_CONTROLLER_BIND_PORT);
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
    }
}
