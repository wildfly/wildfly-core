package org.wildfly.core.testrunner;

import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.SERVER_CONFIG;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * encapsulation of a server process
 *
 * @author Stuart Douglas
 * @author Tomaz Cerar
 */
public class Server {

    // Bootable jar
    private final String bootableJar = System.getProperty("wildfly.bootable.jar.jar");
    private final String installDir = System.getProperty("wildfly.bootable.jar.install.dir");
    private final Boolean isBootableJar = Boolean.getBoolean("wildfly.bootable.jar");

    public static final String LEGACY_JAVA_HOME = "legacy.java.home";

    private final String jbossHome = System.getProperty("jboss.home", System.getenv("JBOSS_HOME"));
    private final String modulePath = System.getProperty("module.path");
    private final String jvmArgs = System.getProperty("jvm.args", "-Xmx512m -XX:MaxMetaspaceSize=256m");
    private final String jbossArgs = System.getProperty("jboss.args");
    private final String javaHome = System.getProperty("java.home", System.getenv("JAVA_HOME"));
    //Use this when specifying an older java to be used for running the server
    private final String legacyJavaHome = System.getProperty(LEGACY_JAVA_HOME);
    private String serverConfig = System.getProperty("server.config", "standalone.xml");
    private final int managementPort = Integer.getInteger("management.port", 9990);
    private final String managementAddress = System.getProperty("management.address", "localhost");
    private final String managementProtocol = System.getProperty("management.protocol", "remote+http");

    // timeouts
    private final int startupTimeout = Integer.getInteger("server.startup.timeout", 30);
    private final int stopTimeout = Integer.getInteger("server.stop.timeout", 10);

    private final String serverDebug = "wildfly.debug";
    private final int serverDebugPort = Integer.getInteger("wildfly.debug.port", 8787);
    private StartMode startMode = StartMode.NORMAL;

    private final Logger log = Logger.getLogger(Server.class.getName());
    private Thread shutdownThread;

    private volatile Process process;
    private final ManagementClient client = new ManagementClient(new DelegatingModelControllerClient(ServerClientProvider.INSTANCE), managementAddress, managementPort, managementProtocol);
    private final URI authConfigUri;
    private final boolean readOnly;

    // git backed configuration
    private String gitRepository;
    private String gitBranch;
    private String gitAuthConfiguration;

    public Server() {
        this(null, false);
    }

    /**
     * Creates a new server.
     * <p>
     * If the {@code authConfigUri} is defined the path will be used to authenticate the
     * {@link ModelControllerClient}.
     * </p>
     *
     * @param authConfigUri the path to the {@code wildfly-config.xml} to use or {@code null}
     */
    public Server(final URI authConfigUri) {
        this(authConfigUri, false);
    }

    /**
     * Creates a new server.
     * <p>
     * If the {@code authConfigUri} is defined the path will be used to authenticate the
     * {@link ModelControllerClient}.
     * </p>
     *
     * @param authConfigUri the path to the {@code wildfly-config.xml} to use or {@code null}
     * @param readOnly {@code true} to start the server in read-only mode
     */
    public Server(final URI authConfigUri, boolean readOnly) {
        this.authConfigUri = authConfigUri;
        this.readOnly = readOnly;
    }

    private static boolean processHasDied(final Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            // good
            return false;
        }
    }

    /**
     * Sets server config to use
     * @param serverConfig
     */
    public void setServerConfig(String serverConfig) {
        this.serverConfig = serverConfig;
    }

    public void setStartMode(StartMode startMode) {
        this.startMode = startMode;
    }

    /**
     * Enable git backed configuration repository
     *
     * @param gitRepository
     * @param gitBranch
     * @param gitAuthConfig
     */
    public void setGitRepository(final String gitRepository, final String gitBranch, final String gitAuthConfig) {
        Objects.requireNonNull(gitRepository);

        this.gitRepository = gitRepository;
        this.gitBranch = gitBranch;
        this.gitAuthConfiguration = gitAuthConfig;
    }

    protected void start() {
        start(System.out);
    }

    protected void start(PrintStream out) {
        try {
            CommandBuilder cbuilder = null;
            if (isBootableJar) {
                final Path bootableJarPath = Paths.get(bootableJar);
                if (Files.notExists(bootableJarPath) || Files.isDirectory(bootableJarPath)) {
                    throw new IllegalStateException("Cannot find: " + bootableJar);
                }
                final BootableJarCommandBuilder commandBuilder = BootableJarCommandBuilder.of(bootableJarPath);
                commandBuilder.setInstallDir(Paths.get(installDir));
                cbuilder = commandBuilder;

                commandBuilder.setJavaHome(legacyJavaHome == null ? javaHome : legacyJavaHome);
                if (jvmArgs != null) {
                    commandBuilder.setJavaOptions(jvmArgs.split("\\s+"));
                }
                if (Boolean.getBoolean(serverDebug)) {
                    commandBuilder.setDebug(true, serverDebugPort);
                }

                //we are testing, of course we want assertions and set-up some other defaults
                commandBuilder.addJavaOption("-ea")
                        .setBindAddressHint("management", managementAddress);

                if (jbossArgs != null) {
                    commandBuilder.addServerArguments(jbossArgs.split("\\s+"));
                }
                commandBuilder.addServerArgument("-D[Standalone]");
            } else {
                final Path jbossHomeDir = Paths.get(jbossHome);
                if (Files.notExists(jbossHomeDir) && !Files.isDirectory(jbossHomeDir)) {
                    throw new IllegalStateException("Cannot find: " + jbossHomeDir);
                }

                final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHomeDir);
                cbuilder = commandBuilder;
                if (modulePath != null && !modulePath.isEmpty()) {
                    commandBuilder.setModuleDirs(modulePath.split(Pattern.quote(File.pathSeparator)));
                }

                commandBuilder.setJavaHome(legacyJavaHome == null ? javaHome : legacyJavaHome);
                if (jvmArgs != null) {
                    commandBuilder.setJavaOptions(jvmArgs.split("\\s+"));
                }
                if (Boolean.getBoolean(serverDebug)) {
                    commandBuilder.setDebug(true, serverDebugPort);
                }

                if (this.startMode == StartMode.ADMIN_ONLY) {
                    commandBuilder.setAdminOnly();
                } else if (this.startMode == StartMode.SUSPEND) {
                    commandBuilder.setStartSuspended();
                }

                if (readOnly) {
                    commandBuilder.setServerReadOnlyConfiguration(serverConfig);
                } else {
                    commandBuilder.setServerConfiguration(serverConfig);
                }

                if (gitRepository != null) {
                    commandBuilder.setGitRepository(gitRepository, gitBranch, gitAuthConfiguration);
                }

                //we are testing, of course we want assertions and set-up some other defaults
                commandBuilder.addJavaOption("-ea")
                        .setBindAddressHint("management", managementAddress);

                if (jbossArgs != null) {
                    commandBuilder.addServerArguments(jbossArgs.split("\\s+"));
                }
                commandBuilder.addServerArgument("-D[Standalone]");
            }
            StringBuilder builder = new StringBuilder("Starting container with: ");
            for (String arg : cbuilder.build()) {
                builder.append(arg).append(" ");
            }
            log.info(builder.toString());
            process = Launcher.of(cbuilder)
                    // Redirect the output and error stream to a file
                    .setRedirectErrorStream(true)
                    .launch();
            new Thread(new ConsoleConsumer(process.getInputStream(), out)).start();
            final Process proc = process;
            shutdownThread = ProcessHelper.addShutdownHook(proc);

            createClient();

            long timeout = startupTimeout * 1000;
            boolean serverAvailable = false;
            long sleep = 1000;
            while (timeout > 0 && !serverAvailable) {
                long before = System.currentTimeMillis();
                serverAvailable = client.isServerInRunningState();
                timeout -= (System.currentTimeMillis() - before);
                if (!serverAvailable) {
                    if (processHasDied(proc))
                        break;
                    Thread.sleep(sleep);
                    timeout -= sleep;
                    sleep = Math.max(sleep / 2, 100);
                }
            }
            if (!serverAvailable) {
                destroyProcess();
                throw new RuntimeException("Managed server was not started within " + startupTimeout + " seconds.");
            }

        } catch (Exception e) {
            safeCloseClient();
            throw new RuntimeException("Could not start container", e);
        }
    }

    protected void stop(boolean forcibly) {
        if (shutdownThread != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownThread);
            shutdownThread = null;
        }
        try {
            if (process != null) {
                if (! forcibly) {
                    try {
                        // AS7-6620: Create the shutdown operation and run it asynchronously and wait for process to terminate
                        client.getControllerClient().executeAsync(Operations.createOperation("shutdown"), null);
                    } catch (AssertionError | RuntimeException e) {
                        //ignore as this can only fail if shutdown is already in progress
                    }
                }

                if (forcibly || !process.waitFor(stopTimeout, TimeUnit.SECONDS)) {
                    // The process hasn't shutdown within the specified timeout. Terminate forcibly.
                    process.destroy();
                    process.waitFor();
                }

                process = null;
            }
        } catch (Exception e) {
            try {
                if (process != null) {
                    process.destroy();
                    process.waitFor();
                }
            } catch (Exception ignore) {
            }
            throw new RuntimeException("Could not stop container", e);
        } finally {
            safeCloseClient();
        }
    }

    private int destroyProcess() {
        if (process == null)
            return 0;
        process.destroy();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ManagementClient getClient() {
        return client;
    }

    private void createClient() {
        ServerClientProvider.INSTANCE.setClient(createModelControllerClient());
    }

    private ModelControllerClient createModelControllerClient() {
        final ModelControllerClientConfiguration.Builder builder = new ModelControllerClientConfiguration.Builder()
                .setProtocol(managementProtocol)
                .setHostName(managementAddress)
                .setPort(managementPort);
        if (authConfigUri == null) {
            builder.setHandler(Authentication.getCallbackHandler());
        } else {
            builder.setAuthenticationConfigUri(authConfigUri);
        }
        return ModelControllerClient.Factory.create(builder.build());
    }

    private void safeCloseClient() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (final Exception e) {
            Logger.getLogger(this.getClass().getName()).warnf(e, "Caught exception closing ModelControllerClient");
        }
    }

    /**
     * Reload current server
     * This method makes sure client on server is still operational after reload is done.
     * @param adminOnly tells server to boot in admin only mode or normal
     * @param timeout time in miliseconds to wait for server to come back after reload
     */
    public void reload(boolean adminOnly, int timeout) {
        reload(adminOnly ? StartMode.ADMIN_ONLY : StartMode.NORMAL, timeout);
    }

    /**
     * Reload current server
     * This method makes sure client on server is still operational after reload is done.
     * @param startMode tells server to boot in admin only mode, suspended or normal
     * @param timeout time in miliseconds to wait for server to come back after reload
     */
    public void reload(StartMode startMode, int timeout) {
        reload(startMode, timeout, null);
    }

    /**
     * Reload current server
     * This method makes sure client on server is still operational after reload is done.
     * @param adminOnly tells server to boot in admin only mode or normal
     * @param timeout time in miliseconds to wait for server to come back after reload
     */
    public void reload(boolean adminOnly, int timeout, String serverConfig) {
        executeReload(adminOnly ? StartMode.ADMIN_ONLY : StartMode.NORMAL, serverConfig);
        waitForLiveServerToReload(timeout); //30 seconds
    }
    /**
     * Reload current server
     * This method makes sure client on server is still operational after reload is done.
     * @param startMode tells server to boot in admin only mode, suspended or normal
     * @param timeout time in miliseconds to wait for server to come back after reload
     */
    public void reload(StartMode startMode, int timeout, String serverConfig) {
        executeReload(startMode, serverConfig);
        waitForLiveServerToReload(timeout); //30 seconds
    }

    private void executeReload(StartMode startMode, String serverConfig) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set("reload");
        if(startMode == StartMode.ADMIN_ONLY) {
            operation.get("admin-only").set(true);
        } else if(startMode == StartMode.SUSPEND) {
            operation.get("start-mode").set("suspend");
        }
        if (serverConfig != null) {
            operation.get(SERVER_CONFIG).set(serverConfig);
        }
        try {
            ModelNode result = client.getControllerClient().execute(operation);
            Assert.assertEquals("success", result.get(ClientConstants.OUTCOME).asString());
        } catch (IOException e) {
            final Throwable cause = e.getCause();
            if (!(cause instanceof ExecutionException) && !(cause instanceof CancellationException) && !(cause instanceof SocketException) ) {
                throw new RuntimeException(e);
            } // else ignore, this might happen if the channel gets closed before we got the response
        }
    }

    private void recreateClient(){
        safeCloseClient();
        ServerClientProvider.INSTANCE.setClient(createModelControllerClient());
    }

    void waitForLiveServerToReload(int timeout) {
        long start = System.currentTimeMillis();
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("server-state");
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            recreateClient();
            ModelControllerClient liveClient = client.getControllerClient();
            try {
                ModelNode result = liveClient.execute(operation);
                if ("running".equals(result.get(RESULT).asString())) {
                    return;
                }
            } catch (IOException e) {
            }

        }
        fail("Live Server did not reload in the imparted time.");
    }


    /**
     * Runnable that consumes the output of the process. If nothing consumes the output the AS will hang on some
     * platforms
     *
     * @author Stuart Douglas
     */
    private class ConsoleConsumer implements Runnable {
        private final InputStream source;
        private final PrintStream target;

        private ConsoleConsumer(final InputStream source, final PrintStream target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public void run() {
            final InputStream source = this.source;
            try {
                byte[] buf = new byte[32];
                int num;
                // Do not try reading a line cos it considers '\r' end of line
                while ((num = source.read(buf)) != -1) {
                    target.write(buf, 0, num);
                }
            } catch (IOException ignore) {
            }
        }
    }

    private static class ServerClientProvider implements DelegatingModelControllerClient.DelegateProvider {

        static final ServerClientProvider INSTANCE = new ServerClientProvider();
        private final AtomicReference<ModelControllerClient> client = new AtomicReference<>();

        void setClient(final ModelControllerClient client) {
            assert client != null;
            this.client.set(client);
        }

        @Override
        public ModelControllerClient getDelegate() {
            final ModelControllerClient result = client.get();
            if (result == null) {
                throw new IllegalStateException("The client has been closed");
            }
            return result;
        }
    }


    public enum StartMode {
        NORMAL,
        ADMIN_ONLY,
        SUSPEND
    }

}
