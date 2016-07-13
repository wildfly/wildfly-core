package org.wildfly.core.testrunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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

    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile Server server;

    public void start(final String serverConfig, boolean adminMode) {
        start(serverConfig, adminMode, System.out);
    }

    public void start(final String serverConfig, boolean adminMode, PrintStream out) {
        if (started.compareAndSet(false, true)) {
            server = new Server();
            if (serverConfig != null) {
                server.setServerConfig(serverConfig);
            }
            server.setAdminMode(adminMode);
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
        start(null, false, out);
    }

    public void startInAdminMode(){
        start(null, true);
    }


    public void stop() {
        if (server != null) {
            try {
                server.stop();
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

    public void reload(int timeout) {
        server.reload(false, timeout);
    }

    public void reload(boolean adminMode, int timeout) {
        server.reload(adminMode, timeout);
    }

    public void reload(boolean adminMode, int timeout, String serverConfig) {
        server.reload(adminMode, timeout, serverConfig);
    }

    public void reload(String serverConfig) {
        server.reload(false, 30 * 1000, serverConfig);
    }

    public void waitForLiveServerToReload(int timeout){
        server.waitForLiveServerToReload(timeout);
    }
}
