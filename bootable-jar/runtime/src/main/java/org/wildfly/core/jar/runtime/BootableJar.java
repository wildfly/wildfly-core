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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
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
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;
import static org.wildfly.core.jar.runtime.Constants.JBOSS_SERVER_CONFIG_DIR;
import static org.wildfly.core.jar.runtime.Constants.JBOSS_SERVER_LOG_DIR;
import static org.wildfly.core.jar.runtime.Constants.LOG_BOOT_FILE_PROP;
import static org.wildfly.core.jar.runtime.Constants.LOG_MANAGER_CLASS;
import static org.wildfly.core.jar.runtime.Constants.LOG_MANAGER_PROP;
import static org.wildfly.core.jar.runtime.Constants.STANDALONE;
import static org.wildfly.core.jar.runtime.Constants.STANDALONE_CONFIG;
import org.wildfly.core.jar.runtime._private.BootableJarLogger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.wildfly.core.jar.runtime.Constants.CONFIGURATION;
import static org.wildfly.core.jar.runtime.Constants.DATA;
import static org.wildfly.core.jar.runtime.Constants.DEPLOYMENTS;
import static org.wildfly.core.jar.runtime.Constants.LOG;
import static org.wildfly.core.jar.runtime.Constants.LOGGING_PROPERTIES;
import static org.wildfly.core.jar.runtime.Constants.SERVER_LOG;
import static org.wildfly.core.jar.runtime.Constants.SERVER_STATE;
import static org.wildfly.core.jar.runtime.Constants.SHA1;
import static org.wildfly.core.jar.runtime.Constants.STOPPED;
import org.wildfly.core.jar.runtime.Server.ShutdownHandler;

/**
 *
 * @author jdenise
 */
public final class BootableJar implements ShutdownHandler {

    private static final String DEP_1 = "ff";
    private static final String DEP_2 = "00";

    private class ShutdownHook extends Thread {

        @Override
        public void run() {
            log.shuttingDown();
            waitAndClean();
        }
    }

    private BootableJarLogger log;

    private final Path jbossHome;
    private final List<String> startServerArgs = new ArrayList<>();
    private Server server;
    private final Arguments arguments;
    private final ModuleLoader loader;

    private BootableJar(Path jbossHome, Arguments arguments, ModuleLoader loader, long unzipTime) throws Exception {
        this.jbossHome = jbossHome;
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

        log.advertiseInstall(jbossHome, unzipTime + (System.currentTimeMillis() - t));
    }

    @Override
    public void shutdown(int status) {
        if (status == ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT) {
            log.cantRestartServer();
        }
        System.exit(status);
    }

    private void setupDeployment(Path deployment) throws Exception {
        Path deploymentDir = jbossHome.resolve(STANDALONE).resolve(DATA).resolve(CONTENT).resolve(DEP_1).resolve(DEP_2);

        Path target = deploymentDir.resolve(CONTENT);
        Files.createDirectories(deploymentDir);
        // Exploded deployment
        boolean isExploded = Files.isDirectory(deployment);
        updateConfig(jbossHome.resolve(STANDALONE).resolve(CONFIGURATION).resolve(STANDALONE_CONFIG),
                deployment.getFileName().toString(), isExploded);
        if (isExploded) {
            copyDirectory(deployment, target);
        } else {
            Files.copy(deployment, target);
        }
        log.installDeployment(deployment);
    }

    private static void updateConfig(Path configFile, String name, boolean isExploded) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(configFile.toFile());
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
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
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        StreamResult output = new StreamResult(configFile.toFile());
        DOMSource input = new DOMSource(document);

        transformer.transform(input, output);

    }

    private void copyDirectory(Path src, Path target) throws IOException {
        Files.walk(src).forEach(file -> {
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
                throw new RuntimeException(ex);
            }
        });
    }

    private void configureLogger() throws IOException {
        System.setProperty(LOG_MANAGER_PROP, LOG_MANAGER_CLASS);
        configureLogging();
        log = BootableJarLogger.ROOT_LOGGER;
    }

    private void configureLogging() throws IOException {
        if (!arguments.isVersion()) {
            LogContext ctx = configureLogContext();
            LogContext.setLogContextSelector(() -> {
                return ctx;
            });
        }
    }

    private LogContext configureLogContext() throws IOException {
        final Path baseDir = jbossHome.resolve(STANDALONE);
        String serverLogDir = System.getProperty(JBOSS_SERVER_LOG_DIR, null);
        if (serverLogDir == null) {
            serverLogDir = baseDir.resolve(LOG).toString();
            System.setProperty(JBOSS_SERVER_LOG_DIR, serverLogDir);
        }
        final String serverCfgDir = System.getProperty(JBOSS_SERVER_CONFIG_DIR, baseDir.resolve(CONFIGURATION).toString());
        final LogContext embeddedLogContext = LogContext.create();
        final Path bootLog = Paths.get(serverLogDir).resolve(SERVER_LOG);
        final Path loggingProperties = Paths.get(serverCfgDir).resolve(Paths.get(LOGGING_PROPERTIES));
        if (Files.exists(loggingProperties)) {
            try (final InputStream in = Files.newInputStream(loggingProperties)) {
                System.setProperty(LOG_BOOT_FILE_PROP, bootLog.toAbsolutePath().toString());
                PropertyConfigurator configurator = new PropertyConfigurator(embeddedLogContext);
                configurator.configure(in);
            }
        }
        return embeddedLogContext;
    }

    public void run() throws Exception {
        try {
            server = buildServer(startServerArgs);
        } catch (RuntimeException ex) {
            cleanup();
            throw ex;
        }

        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        server.start();
    }

    private void cleanup() {
        log.deletingHome(jbossHome);
        deleteDir(jbossHome);

    }

    private Server buildServer(List<String> args) throws IOException {
        String[] array = new String[args.size()];
        log.advertiseOptions(args);
        return Server.newSever(jbossHome, args.toArray(array), loader, this);
    }

    private void deleteDir(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                        log.cantDelete(file.toString(), ex);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (IOException ex) {
                        log.cantDelete(dir.toString(), ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }

    private void waitAndClean() {
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
                    log.unexpectedExceptionWhileShuttingDown(ex);
                }
            }
        } finally {
            cleanup();
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
        Arguments arguments;
        try {
            arguments = Arguments.parseArguments(args);
        } catch (Throwable ex) {
            System.err.println(ex);
            CmdUsage.printUsage(System.out);
            return;
        }
        if (arguments.isHelp()) {
            CmdUsage.printUsage(System.out);
            return;
        }
        BootableJar bootableJar = new BootableJar(jbossHome, arguments, moduleLoader, unzipTime);
        bootableJar.run();
    }

    static void setTccl(final ClassLoader cl) {
        Thread.currentThread().setContextClassLoader(cl);
    }
}
