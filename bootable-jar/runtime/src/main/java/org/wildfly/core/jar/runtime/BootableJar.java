/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import static java.security.AccessController.doPrivileged;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.jboss.as.controller.client.ModelControllerClient;
import static org.jboss.as.controller.client.helpers.ClientConstants.ADDRESS;
import static org.jboss.as.controller.client.helpers.ClientConstants.ARCHIVE;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.version.ProductConfig;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;
import static org.wildfly.core.jar.runtime.Constants.LOG_BOOT_FILE_PROP;
import static org.wildfly.core.jar.runtime.Constants.LOG_MANAGER_CLASS;
import static org.wildfly.core.jar.runtime.Constants.LOG_MANAGER_PROP;
import static org.wildfly.core.jar.runtime.Constants.STANDALONE_CONFIG;

import org.jboss.modules.ModuleLoggerFinder;
import org.jboss.modules.log.JDKModuleLogger;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wildfly.common.xml.DocumentBuilderFactoryUtil;
import org.wildfly.common.xml.TransformerFactoryUtil;
import static org.wildfly.core.jar.runtime.Constants.DEPLOYMENTS;
import static org.wildfly.core.jar.runtime.Constants.LOGGING_PROPERTIES;
import static org.wildfly.core.jar.runtime.Constants.SERVER_LOG;
import static org.wildfly.core.jar.runtime.Constants.SERVER_STATE;
import static org.wildfly.core.jar.runtime.Constants.SHA1;
import static org.wildfly.core.jar.runtime.Constants.STOPPED;
import org.wildfly.core.jar.runtime.Server.ShutdownHandler;

/**
 *
 * @author jdenise
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class BootableJar implements ShutdownHandler {

    private static final String DEP_1 = "ff";
    private static final String DEP_2 = "00";

    private BootableJarLogger log;

    private final BootableEnvironment environment;
    private final List<String> startServerArgs = new ArrayList<>();
    private Server server;
    private final Arguments arguments;
    private final ModuleLoader loader;
    private final Path pidFile;

    private BootableJar(BootableEnvironment environment, Arguments arguments, ModuleLoader loader, long unzipTime) throws Exception {
        this.environment = environment;
        this.arguments = arguments;
        this.loader = loader;
        startServerArgs.addAll(arguments.getServerArguments());
        startServerArgs.add(CommandLineConstants.READ_ONLY_SERVER_CONFIG + "=" + STANDALONE_CONFIG);

        // logging needs to be configured before other components have a chance to initialize a logger
        configureLogger();
        long t = System.currentTimeMillis();
        if (arguments.getDeployment() != null) {
            setupDeployment(arguments.getDeployment());
        }

        log.advertiseInstall(environment.getJBossHome(), unzipTime + (System.currentTimeMillis() - t));
        pidFile = environment.getPidFile();
    }

    @Override
    public void shutdown(int status) {
        if (status == ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT) {
            log.cantRestartServer();
        }
        System.exit(status);
    }

    private void setupDeployment(Path deployment) throws Exception {
        Path deploymentDir = environment.resolveContentDir(DEP_1, DEP_2);

        Path target = deploymentDir.resolve(CONTENT);
        Files.createDirectories(deploymentDir);
        // Exploded deployment
        boolean isExploded = Files.isDirectory(deployment);
        updateConfig(environment.resolveConfigurationDir(STANDALONE_CONFIG),
                deployment.getFileName().toString(), isExploded);
        if (isExploded) {
            copyDirectory(deployment, target);
        } else {
            Files.copy(deployment, target);
        }
        log.installDeployment(deployment);
    }

    private static void updateConfig(Path configFile, String name, boolean isExploded) throws Exception {
        try (FileInputStream fileInputStream = new FileInputStream(configFile.toFile())) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactoryUtil.create();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            Element root = document.getDocumentElement();

            NodeList lst = root.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                Node n = lst.item(i);
                if (n instanceof Element) {
                    if (DEPLOYMENTS.equals(n.getNodeName())) {
                        throw BootableJarLogger.ROOT_LOGGER.deploymentAlreadyExist();
                    }
                }
            }
            Element deployments = document.createElement(DEPLOYMENTS);
            Element deployment = document.createElement(DEPLOYMENT);
            Element content = document.createElement(CONTENT);
            content.setAttribute(SHA1, DEP_1 + DEP_2);
            if (isExploded) {
                content.setAttribute(ARCHIVE, "false");
            }
            deployment.appendChild(content);
            deployment.setAttribute(NAME, name);
            deployment.setAttribute(RUNTIME_NAME, name);
            deployments.appendChild(deployment);

            root.appendChild(deployments);
            Transformer transformer = TransformerFactoryUtil.create().newTransformer();
            StreamResult output = new StreamResult(configFile.toFile());
            DOMSource input = new DOMSource(document);

            transformer.transform(input, output);
        }
    }

    private void copyDirectory(Path src, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(file -> {
                try {
                    Path targetFile = target.resolve(src.relativize(file));
                    if (Files.isDirectory(file)) {
                        if (!Files.exists(targetFile)) {
                            Files.createDirectory(targetFile);
                        }
                    } else {
                        Files.copy(file, targetFile);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
    }

    private void configureLogger() throws IOException {
        environment.setSystemProperty(LOG_MANAGER_PROP, LOG_MANAGER_CLASS);
        configureLogging();
        log = BootableJarLogger.ROOT_LOGGER;
    }

    private void configureLogging() throws IOException {
        if (!arguments.isVersion()) {
            // Load the boot configuration properties
            loadBootConfigProperties();
            LogContext ctx = configureLogContext();
            // Use our own LogContextSelector which returns the configured context.
            LogContext.setLogContextSelector(() -> ctx);
            // Set a new JDK module logger to replace the default NoopModuleLogger
            Module.setModuleLogger(new JDKModuleLogger());

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
    }

    private LogContext configureLogContext() throws IOException {
        // Create our own log context instead of using the default system log context. This is useful for cases when the
        // LogManager.readConfiguration() may be invoked it will not override the current configuration.
        final LogContext logContext = LogContext.create();
        final Path bootLog = environment.resolveLogDir(SERVER_LOG);
        final Path loggingProperties = environment.resolveConfigurationDir(LOGGING_PROPERTIES);
        if (Files.exists(loggingProperties)) {
            try (final InputStream in = Files.newInputStream(loggingProperties)) {
                environment.setSystemProperty(LOG_BOOT_FILE_PROP, bootLog.toAbsolutePath().toString());
                // The LogManager.readConfiguration() uses the LogContext.getSystemLogContext(). Since we create our
                // own LogContext we need to configure the context and attach the configurator to the root logger. The
                // logging subsystem will use this configurator to determine what resources may need to be reconfigured.
                PropertyConfigurator configurator = new PropertyConfigurator(logContext);
                configurator.configure(in);
                logContext.getLogger("").attach(Configurator.ATTACHMENT_KEY, configurator);
            }
        }
        return logContext;
    }

    public void run() throws Exception {
        Path script = arguments.getCLIScript();
        if (script != null) {
            long id = System.currentTimeMillis();
            Path markerDir = environment.getTmpDir().resolve(id + "-cli-boot-hook-dir");
            Path outputFile = environment.getTmpDir().resolve(id + "-cli-boot-hook-output-file.txt");
            Files.createDirectories(markerDir);
            startServerArgs.add("--start-mode=admin-only");
            startServerArgs.add("-Dorg.wildfly.internal.cli.boot.hook.script=" + script.toAbsolutePath().toString());
            startServerArgs.add("-Dorg.wildfly.internal.cli.boot.hook.marker.dir=" + markerDir.toAbsolutePath().toString());
            startServerArgs.add("-Dorg.wildfly.internal.cli.boot.hook.script.output.file=" + outputFile.toAbsolutePath().toString());
        }

        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        server = buildServer(startServerArgs);

        if (Files.notExists(pidFile)) {
            Files.write(pidFile, Collections.singleton(Long.toString(org.wildfly.common.os.Process.getProcessId())), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } else {
            throw log.pidFileAlreadyExists(pidFile, environment.getJBossHome());
        }
        server.start();
    }

    private Server buildServer(List<String> args) throws IOException {
        String[] array = new String[args.size()];
        log.advertiseOptions(args);
        return Server.newSever(args.toArray(array), loader, this);
    }

    private void loadBootConfigProperties() throws IOException {
        final Path configFile = environment.resolveConfigurationDir( "boot-config.properties");
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                final Properties properties = new Properties();
                properties.load(reader);
                // Set the system properties if they are not already defined
                for (String key : properties.stringPropertyNames()) {
                    // Note this overrides any previously set system property. This is what the system-property resource
                    // does and this should behave the same.
                    environment.setSystemProperty(key, properties.getProperty(key));
                }
            }
        }
    }

    /**
     * Modular entry point.
     *
     * @param jbossHome Server home directory.
     * @param args User provided arguments.
     * @param moduleLoader JBoss modules loader.
     * @param moduleClassLoader Bootable jar module classloader
     * @param unzipTime Time spent to unzip the server.
     * @throws Exception
     */
    public static void run(Path jbossHome, List<String> args, ModuleLoader moduleLoader, ModuleClassLoader moduleClassLoader, Long unzipTime) throws Exception {
        setTccl(moduleClassLoader);
        // Initialize the environment
        final BootableEnvironment environment = BootableEnvironment.of(jbossHome);
        ProductConfig productConfig = ProductConfig.fromFilesystemSlot(moduleLoader, jbossHome.toString(), null);
        Arguments arguments;
        try {
            arguments = Arguments.parseArguments(args, environment);
        } catch (Throwable ex) {
            System.err.println(ex);
            CmdUsage.printUsage(productConfig, System.out);
            return;
        }
        if (arguments.isHelp()) {
            CmdUsage.printUsage(productConfig, System.out);
            return;
        }

        // Side effect is to initialise Log Manager
        BootableJar bootableJar = new BootableJar(environment, arguments, moduleLoader, unzipTime);

        // First, activate the ModuleLoggerFinder
        configureModuleFinder(moduleClassLoader);

        // At this point we can configure JMX
        configureJMX(moduleClassLoader, bootableJar.log);

        // Automatic loading of Security providers.
        // Needed for logic that requires access to providers prior elytron subsystem is configured.
        final ServiceLoader<Provider> providerServiceLoader = ServiceLoader.load(Provider.class, moduleClassLoader);
        SecurityManager sm = System.getSecurityManager();
        Iterator<Provider> iterator = providerServiceLoader.iterator();
        for (;;) try {
            if (! (iterator.hasNext())) break;
            final Provider provider = iterator.next();
            if (sm == null) {
                new AddProviderAction(provider).run();
            } else {
                final Class<? extends Provider> providerClass = provider.getClass();
                // each provider needs permission to install itself
                doPrivileged(new AddProviderAction(provider), getProviderContext(providerClass));
            }
        } catch (ServiceConfigurationError | RuntimeException e) {
            bootableJar.log.securityProviderFailed(e);
        }
        bootableJar.run();
    }

    private static void configureJMX(ModuleClassLoader moduleClassLoader, BootableJarLogger log) throws Exception {
        final String mbeanServerBuilderName = getServiceName(moduleClassLoader, "javax.management.MBeanServerBuilder");
        if (mbeanServerBuilderName != null) {
            System.setProperty("javax.management.builder.initial", mbeanServerBuilderName);
            // Initialize the platform mbean server
            ManagementFactory.getPlatformMBeanServer();
        }

        ModuleLoader.installMBeanServer();
    }

    /**
     * This is a temporary workaround for WFCORE-6712 until MODULES-447 is resolved
     *
     * @param classLoader the class loader to pass to the activation of the {@link ModuleLoggerFinder}
     */
    private static void configureModuleFinder(final ClassLoader classLoader) {
        // Please note, once MODULES-447 is resolved, this method can be removed and replaced with the public call.
        final Method method;
        if (System.getSecurityManager() == null) {
            try {
                method = ModuleLoggerFinder.class.getDeclaredMethod("activate", ClassLoader.class);
                method.trySetAccessible();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        } else {
            method = doPrivileged((PrivilegedAction<Method>) () -> {
                try {
                    final Method result = ModuleLoggerFinder.class.getDeclaredMethod("activate", ClassLoader.class);
                    result.trySetAccessible();
                    return result;
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        try {
            method.invoke(null, classLoader);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getServiceName(ClassLoader classLoader, String className) throws IOException {
        try (final InputStream stream = classLoader.getResourceAsStream("META-INF/services/" + className)) {
            if (stream == null) {
                return null;
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                final int i = line.indexOf('#');
                if (i != -1) {
                    line = line.substring(0, i);
                }
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                return line;
            }
            return null;
        }
    }

    static void setTccl(final ClassLoader cl) {
        Thread.currentThread().setContextClassLoader(cl);
    }

    private class ShutdownHook extends Thread {

        @Override
        public void run() {
            log.shuttingDown();
            final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                final Thread thread = new Thread(r);
                thread.setName("installation-cleaner");
                return thread;
            });
            final InstallationCleaner cleaner = new InstallationCleaner(environment, log);
            executor.submit(cleaner);
            if (Files.exists(pidFile)) {
                waitForShutdown();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(environment.getTimeout(), TimeUnit.SECONDS)) {
                    // For some reason we've timed out. The deletion should likely be executing.
                    // We can't start a new cleanup to force it. On Windows we would have the side effect to have 2 cleaner processes to
                    // be executed, with the risk that a new installation has been installed and the new cleaner cleaning the new installation
                    log.cleanupTimeout(environment.getTimeout(), environment.getJBossHome());
                }
            } catch (InterruptedException e) {
                // The task has been interrupted, leaving
                log.cleanupTimeout(environment.getTimeout(), environment.getJBossHome());
            }
        }

        private void waitForShutdown() {
            try {
                // Give max 10 seconds for the server to stop before to delete jbossHome.
                ModelNode mn = new ModelNode();
                mn.get(ADDRESS);
                mn.get(OP).set(READ_ATTRIBUTE_OPERATION);
                mn.get(NAME).set(SERVER_STATE);
                for (int i = 0; i < 10; i++) {
                    try {
                        ModelControllerClient client = server.getModelControllerClient();
                        if (client != null) {
                            ModelNode ret = client.execute(mn);
                            if (ret.hasDefined(RESULT)) {
                                String val = ret.get(RESULT).asString();
                                if (STOPPED.equals(val)) {
                                    log.serverStopped();
                                    break;
                                } else {
                                    log.serverNotStopped();
                                }
                            }
                            Thread.sleep(1000);
                        } else {
                            log.nullController();
                            break;
                        }
                    } catch (Exception ex) {
                        throw log.unexpectedExceptionWhileShuttingDown(ex);
                    }
                }
            } finally {
                try {
                    Files.deleteIfExists(pidFile);
                    log.debugf("Deleted PID file %s", pidFile);
                } catch (IOException e) {
                    log.cantDelete(pidFile.toString(), e);
                }
            }
        }
    }

    static final class AddProviderAction implements PrivilegedAction<Void> {
        private final Provider provider;

        AddProviderAction(final Provider provider) {
            this.provider = provider;
        }

        public Void run() {
            Security.addProvider(provider);
            return null;
        }
    }

    private static AccessControlContext getProviderContext(final Class<? extends Provider> providerClass) {
        return new AccessControlContext(new ProtectionDomain[]{providerClass.getProtectionDomain()});
    }

}
