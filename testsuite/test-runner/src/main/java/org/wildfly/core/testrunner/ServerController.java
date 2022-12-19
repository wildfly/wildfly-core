package org.wildfly.core.testrunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ServerController {

    private static final int RESTART_PROCESS_FROM_STARTUP_SCRIPT = 10;

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile Server server;

    /**
     * @deprecated use the startMode variant instead
     */
    @Deprecated
    public void start(final String serverConfig, boolean adminMode) {
        start(serverConfig, adminMode ? Server.StartMode.ADMIN_ONLY : Server.StartMode.NORMAL);
    }

    /**
     * Starts the server. If the {@code authConfigUri} is not {@code null} the resource will be used for
     * authentication of the {@link org.jboss.as.controller.client.ModelControllerClient}.
     *
     * @param authConfigUri the path to the {@code wildfly-config.xml} or {@code null}
     */
    public void start(final URI authConfigUri) {
        start(null, authConfigUri);
    }

    /**
     * Starts the server. If the {@code authConfigUri} is not {@code null} the resource will be used for
     * authentication of the {@link org.jboss.as.controller.client.ModelControllerClient}.
     *
     * @param serverConfig  the configuration file to use or {@code null} to use the default
     * @param authConfigUri the path to the {@code wildfly-config.xml} or {@code null}
     */
    public void start(final String serverConfig, final URI authConfigUri) {
        start(serverConfig, authConfigUri, Server.StartMode.NORMAL, System.out, false);
    }

    public void start(final String serverConfig, Server.StartMode startMode) {
        start(serverConfig, startMode, System.out);
    }

    public void start(final String serverConfig, Server.StartMode startMode, PrintStream out) {
        start(serverConfig, null, startMode, out, false);
    }

    /**
     * Stats the server.
     * <p>
     * If the {@code authConfigUri} is not {@code null} the resource will be used for authentication of the
     * {@link org.jboss.as.controller.client.ModelControllerClient}.
     * </p>
     *
     * @param serverConfig  the configuration file to use or {@code null} to use the default
     * @param authConfigUri the path to the {@code wildfly-config.xml} or {@code null}
     * @param startMode     the mode to start the server in
     * @param out           the print stream used to consume the {@code stdout} and {@code stderr} streams
     * @param readOnly
     */
    public void start(final String serverConfig, final URI authConfigUri, Server.StartMode startMode, PrintStream out, boolean readOnly) {
     start(serverConfig, authConfigUri, startMode, out, readOnly, null, null, null, null, null);
    }

    /**
     * Stats the server.
     * <p>
     * If the {@code authConfigUri} is not {@code null} the resource will be used for authentication of the
     * {@link org.jboss.as.controller.client.ModelControllerClient}.
     * </p>
     *
     * @param serverConfig  the configuration file to use or {@code null} to use the default
     * @param authConfigUri the path to the {@code wildfly-config.xml} or {@code null}
     * @param startMode     the mode to start the server in
     * @param out           the print stream used to consume the {@code stdout} and {@code stderr} streams
     * @param readOnly
     * @param gitRepository the git repository to clone to get the server configuration.
     * @param gitBranch     the git branch to use to get the server configuration
     * @param gitAuthConfig the path
     */
    public void start(final String serverConfig, final URI authConfigUri, Server.StartMode startMode, PrintStream out,
                      boolean readOnly, final String gitRepository, final String gitBranch, final String gitAuthConfig) {
        start(serverConfig, authConfigUri, startMode, out, readOnly, gitRepository, gitBranch, gitAuthConfig, null, null);
    }

    /**
     * Stats the server.
     * <p>
     * If the {@code authConfigUri} is not {@code null} the resource will be used for authentication of the
     * {@link org.jboss.as.controller.client.ModelControllerClient}.
     * </p>
     *
     * @param serverConfig  the configuration file to use or {@code null} to use the default
     * @param authConfigUri the path to the {@code wildfly-config.xml} or {@code null}
     * @param startMode     the mode to start the server in
     * @param out           the print stream used to consume the {@code stdout} and {@code stderr} streams
     * @param readOnly
     * @param gitRepository the git repository to clone to get the server configuration.
     * @param gitBranch     the git branch to use to get the server configuration
     * @param gitAuthConfig the path
     * @param configDir     Server configuration directory
     * @param yamlFiles     YAML files
     */
    public void start(final String serverConfig, final URI authConfigUri, Server.StartMode startMode, PrintStream out,
                      boolean readOnly, final String gitRepository, final String gitBranch, final String gitAuthConfig,
                      Path configDir, Path[] yamlFiles) {
        if (started.compareAndSet(false, true)) {
            server = new Server(authConfigUri, readOnly);
            server.setYamlFiles(yamlFiles);
            if (serverConfig != null) {
                server.setServerConfig(serverConfig);
            }
            if (gitRepository != null) {
                server.setGitRepository(gitRepository, gitBranch, gitAuthConfig);
            }
            if (configDir != null) {
                server.setConfigDir(configDir);
            }
            server.setStartMode(startMode);
            try {
                server.start(out);
            } catch (final Throwable t) {
                // failed to start
                server = null;
                started.set(false);
                throw t;
            }
        }
    }

    public void start() {
        start(System.out);
    }

    public void start(PrintStream out) {
        start(null, Server.StartMode.NORMAL, out);
    }

    public void startInAdminMode(){
        start(null, Server.StartMode.ADMIN_ONLY);
    }

    public void startReadOnly(){
        start(null, null, Server.StartMode.NORMAL, System.out, true);
    }

    public void startReadOnly(Path configDir){
        start(null, null, Server.StartMode.NORMAL, System.out, true, null, null, null, configDir, null);
    }

    public void startSuspended() {
        start(null, Server.StartMode.SUSPEND);
    }

    public void startGitBackedConfiguration(final String gitRepository, final String gitBranch, final String gitAuthConfig) {
        start(null, null, Server.StartMode.NORMAL, System.out, false, gitRepository, gitBranch, gitAuthConfig);
    }

    public void startYamlExtension(final Path[] yamlFiles) {
        start(null, null, Server.StartMode.NORMAL, System.out, false, null, null, null, null, yamlFiles);
    }

    public void stop() {
        stop(false);
    }

    public void stop(boolean forcibly) {
        if (server != null) {
            try {
                server.stop(forcibly);
            } finally {
                server = null;
                started.set(false);
            }
        }
    }

    public boolean isStarted() {
        return (server != null);
    }

    public ManagementClient getClient() {
        return server.getClient();
    }

    public ServerDeploymentHelper getDeploymentHelper() {
        return new ServerDeploymentHelper(server.getClient().getControllerClient());
    }

    public void deploy(final Archive<?> archive, final String runtimeName) throws ServerDeploymentHelper.ServerDeploymentException {
        getDeploymentHelper().deploy(runtimeName, archive.as(ZipExporter.class).exportAsInputStream());
    }

    public void deploy(final Path deployment) throws ServerDeploymentHelper.ServerDeploymentException, IOException {
        final ServerDeploymentHelper helper = getDeploymentHelper();
        try (InputStream in = Files.newInputStream(deployment)) {
            helper.deploy(deployment.getFileName().toString(), in);
        }

    }

    public void undeploy(final String runtimeName) throws ServerDeploymentHelper.ServerDeploymentException {
        final ServerDeploymentHelper helper = getDeploymentHelper();
        helper.undeploy(runtimeName);
    }

    /**
     * Reloads server and wait for it to come back
     */
    public void reload() {
        server.reload(false, 30 * 1000); //by default reload in normal mode with timeout of 30 seconds
    }

    public void reload(Server.StartMode startMode) {
        server.reload(startMode, 30 * 1000); //by default reload in normal mode with timeout of 30 seconds
    }

    public void reload(int timeout) {
        server.reload(false, timeout);
    }

    public void reload(boolean adminMode, int timeout) {
        server.reload(adminMode, timeout);
    }

    public void reload(boolean adminMode, int timeout, String serverConfig) {
        server.reload(adminMode, timeout, serverConfig);
    }

    public void reload(Server.StartMode startMode, int timeout) {
        server.reload(startMode, timeout);
    }

    public void reload(Server.StartMode startMode, int timeout, String serverConfig) {
        server.reload(startMode, timeout, serverConfig);
    }
    public void reload(String serverConfig) {
        server.reload(false, 30 * 1000, serverConfig);
    }

    public void waitForLiveServerToReload(int timeout){
        server.waitForLiveServerToReload(timeout);
    }

    /**
     * Spawns a thread to execute a given {@code restartTrigger} task and then starts a new server
     * process once the existing server exits with the usual restart exit code.
     *
     * @param restartTrigger a runnable that will result in the existing server process exiting with the
     *                       restart exit code. Cannot be {@code null}
     * @param timeout maximum time in ms to wait for the server process to exit
     * @param restart whether to restart server after being successfully stopped
     *
     * @throws RuntimeException if {@code restartTrigger} throws an exception
     * @throws AssertionError if {@code restartTrigger} throws an AssertionError, if the server does not stop within
     *                        {@code timeout} ms, or if the server's exit code is not the one expected.
     */
    public void handleServerRestart(Runnable restartTrigger, long timeout, boolean restart) {
        Thread restartThread = new Thread(restartTrigger);
        restartThread.start();
        server.waitForServerToStop(timeout, RESTART_PROCESS_FROM_STARTUP_SCRIPT);
        if (restart) {
            server.start();
        }
    }

}
