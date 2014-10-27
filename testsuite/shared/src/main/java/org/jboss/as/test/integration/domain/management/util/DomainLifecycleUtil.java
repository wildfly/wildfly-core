/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.test.integration.domain.management.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.sasl.util.UsernamePasswordHashUtil;
import org.wildfly.core.launcher.DomainCommandBuilder;

/**
 * Utility for controlling the lifecycle of a domain.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainLifecycleUtil {

    public static final String SLAVE_HOST_PASSWORD = "slave_us3r_password";

    private static final ThreadFactory threadFactory = new AsyncThreadFactory();

    private final Logger log = Logger.getLogger(DomainLifecycleUtil.class.getName());

    // The ProcessController process wrapper
    private ProcessWrapper process;
    // The connection to the HC, which can be shared across multiple clients
    private DomainTestConnection connection;
    // A shared domain client
    private DomainTestClient domainClient;

//    private Map<ServerIdentity, ControlledProcessState.State> serverStatuses = new HashMap<ServerIdentity, ControlledProcessState.State>();
    private ExecutorService executor;

    private final WildFlyManagedConfiguration configuration;
    private final DomainControllerClientConfig clientConfiguration;
    private final PathAddress address;
    private final boolean closeClientConfig;

    public DomainLifecycleUtil(final WildFlyManagedConfiguration configuration) throws IOException {
        this(configuration, DomainControllerClientConfig.create(), true);
    }

    public DomainLifecycleUtil(final WildFlyManagedConfiguration configuration, final DomainControllerClientConfig clientConfiguration) {
        this(configuration, clientConfiguration, false);
    }

    private DomainLifecycleUtil(final WildFlyManagedConfiguration configuration,
                                final DomainControllerClientConfig clientConfiguration, final boolean closeClientConfig) {
        assert configuration != null : "configuration is null";
        assert clientConfiguration != null : "clientConfiguration is null";
        this.configuration = configuration;
        this.clientConfiguration = clientConfiguration;
        this.address = PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.HOST, configuration.getHostName()));
        this.closeClientConfig = closeClientConfig;
    }

    public WildFlyManagedConfiguration getConfiguration() {
        return configuration;
    }

    public PathAddress getAddress() {
        return address;
    }

    public void start() {
        try {
            configuration.validate();

            final String address = NetworkUtils.formatPossibleIpv6Address(configuration.getHostControllerManagementAddress());
            final int port = configuration.getHostControllerManagementPort();
            final URI connectionURI = new URI(configuration.getHostControllerManagementProtocol() + "://"
                    + address + ":" + port);
            // Create the connection - this will try to connect on the first request
            connection = clientConfiguration.createConnection(connectionURI, configuration.getCallbackHandler());

            final DomainCommandBuilder commandBuilder;
            final String jbossHome = configuration.getJbossHome();
            if (configuration.getControllerJavaHome() == null) {
                commandBuilder = DomainCommandBuilder.of(jbossHome);
            } else {
                commandBuilder = DomainCommandBuilder.of(jbossHome, configuration.getControllerJavaHome());
            }

            final String jbossOptions = System.getProperty("jboss.options");
            if (jbossOptions != null) {
                final String[] javaOpts = jbossOptions.split("\\s+");
                commandBuilder.setHostControllerJavaOptions(javaOpts)
                        .setProcessControllerJavaOptions(javaOpts);
            }
            if (configuration.getJavaVmArguments() != null) {
                final String[] javaOpts = configuration.getJavaVmArguments().split("\\s+");
                commandBuilder.addHostControllerJavaOptions(javaOpts)
                        .addProcessControllerJavaOptions(javaOpts);
            }

            // Set the Java Home for the servers
            if (configuration.getJavaHome() != null) {
                commandBuilder.setServerJavaHome(configuration.getJavaHome());
            }

            if (configuration.getDomainDirectory() != null) {
                commandBuilder.setBaseDirectory(configuration.getDomainDirectory());
            }

            if (configuration.getModulePath() != null && !configuration.getModulePath().isEmpty()) {
                commandBuilder.setModuleDirs(configuration.getModulePath().split(Pattern.quote(File.pathSeparator)));
            }

            final Path domainDir = commandBuilder.getBaseDirectory();
            final Path configDir = commandBuilder.getConfigurationDirectory();

            if (configuration.getMgmtUsersFile() != null) {
                copyConfigFile(configuration.getMgmtUsersFile(), configDir, null);
            } else {
                // No point backing up the file in a test scenario, just write what we need.
                final String text = "slave=" + new UsernamePasswordHashUtil().generateHashedHexURP("slave", "ManagementRealm", SLAVE_HOST_PASSWORD.toCharArray());
                createFile(configDir.resolve("mgmt-users.properties"), text);
            }
            if (configuration.getMgmtGroupsFile() != null) {
                copyConfigFile(configuration.getMgmtGroupsFile(), configDir, null);
            } else {
                // Put out empty mgmt-groups.properties.
                createFile(configDir.resolve("mgmt-groups.properties"), "# Management groups");
            }
            // Put out empty application realm properties files so servers don't complain
            createFile(configDir.resolve("application-users.properties"), "# Application users");
            createFile(configDir.resolve("application-roles.properties"), "# Application roles");
            // Copy the logging.properties file
            copyConfigFile(Paths.get(jbossHome, "domain", "configuration", "logging.properties"), configDir, null);

            final List<String> ipv6Args = new ArrayList<>();
            TestSuiteEnvironment.getIpv6Args(ipv6Args);
            commandBuilder.addHostControllerJavaOptions(ipv6Args)
                    .addProcessControllerJavaOptions(ipv6Args);

            if (configuration.getHostCommandLineProperties() != null) {
                commandBuilder.addHostControllerJavaOptions(configuration.getHostCommandLineProperties().split("\\s+"));
            }
            if (configuration.isAdminOnly()) {
                commandBuilder.setAdminOnly();
            }
            if (configuration.isBackupDC()) {
                commandBuilder.setBackup();
            }
            if (configuration.isCachedDC()) {
                commandBuilder.setCachedDomainController();
            }

            if (configuration.getDomainConfigFile() != null) {
                final String prefix = configuration.isCachedDC() ? null : "testing-";
                final String name = copyConfigFile(configuration.getDomainConfigFile(), configDir, prefix);
                if (configuration.isReadOnlyDomain()) {
                    commandBuilder.setReadOnlyDomainConfiguration(name);
                } else if (!configuration.isCachedDC()) {
                    commandBuilder.setDomainConfiguration(name);
                }
            }
            if (configuration.getHostConfigFile() != null) {
                final String name = copyConfigFile(configuration.getHostConfigFile(), configDir);
                if (configuration.isReadOnlyHost()) {
                    commandBuilder.setReadOnlyHostConfiguration(name);
                } else {
                    commandBuilder.setHostConfiguration(name);
                }
            }
            if (configuration.getHostControllerManagementAddress() != null) {
                commandBuilder.setInterProcessHostControllerAddress(configuration.getHostControllerManagementAddress());
                commandBuilder.setProcessControllerAddress(configuration.getHostControllerManagementAddress());
            }
            // the process working dir
            final String workingDir = domainDir.toString();

            // Start the process
            final ProcessWrapper wrapper = new ProcessWrapper(configuration.getHostName(), commandBuilder, Collections.<String, String>emptyMap(), workingDir);
            wrapper.start();
            process = wrapper;

            long start = System.currentTimeMillis();
            if (!configuration.isAdminOnly()) {
                // Wait a bit to let HC get going
                TimeUnit.SECONDS.sleep(2);
                // Wait for the servers to be started
                awaitServers(start);
                log.info("All servers started in " + (System.currentTimeMillis() - start) + " ms");
            }
            // Wait for the HC to be in running state. Normally if all servers are started, this is redundant
            // but there may not be any servers or we may be in --admin-only mode
            awaitHostController(start);
            log.info("HostController started in " + (System.currentTimeMillis() - start) + " ms");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not start container", e);
        }

    }

    public Future<Void> startAsync() {
        Callable<Void> c = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                start();
                return null;
            }
        };

        return getExecutorService().submit(c);
    }

    public int getProcessExitCode() {
        return process.getExitValue();
    }

    /**
     * Stop and wait for the process to exit.
     */
    public synchronized void stop() {
        RuntimeException toThrow = null;
        try {
            if (process != null) {
                process.stop();
                process.waitFor();
                process = null;
            }
        } catch (Exception e) {
            toThrow = new RuntimeException("Could not stop container", e);
        } finally {
            closeConnection();
            final ExecutorService exec = executor;
            if (exec != null) {
                exec.shutdownNow();
                executor = null;
            }
            if (closeClientConfig) {
                try {
                    clientConfiguration.close();
                } catch (Exception e) {
                    if (toThrow == null) {
                        toThrow = new RuntimeException("Could not stop client configuration", e);
                    }
                }
            }
        }

        if (toThrow != null) {
            throw toThrow;
        }
    }

    public Future<Void> stopAsync() {
        Callable<Void> c = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                stop();
                return null;
            }
        };

        return Executors.newSingleThreadExecutor(threadFactory).submit(c);
    }

    /**
     * Execute an operation and wait until the connection is closed. This is only useful for :reload and :shutdown operations.
     *
     * @param operation the operation to execute
     * @return the operation result
     * @throws IOException for any error
     */
    public ModelNode executeAwaitConnectionClosed(final ModelNode operation) throws IOException {
        final DomainTestClient client = internalGetOrCreateClient();
        final Channel channel = client.getChannel();
        if( null == channel )
            throw new IllegalStateException("Didn't get a remoting channel from the DomainTestClient.");
        final Connection ref = channel.getConnection();
        ModelNode result = new ModelNode();
        try {
            result = client.execute(operation);
            // IN case the operation wasn't successful, don't bother waiting
            if(! "success".equals(result.get("outcome").asString())) {
                return result;
            }
        } catch(Exception e) {
            if(e instanceof IOException) {
                final Throwable cause = e.getCause();
                if(cause instanceof ExecutionException) {
                    // ignore, this might happen if the channel gets closed before we got the response
                } else {
                    throw (IOException) e;
                }
            } else {
                throw new RuntimeException(e);
            }
        }
        try {
            if(channel != null) {
                // Wait for the channel to close
                channel.awaitClosed();
            }
            // Wait for the connection to be closed
            connection.awaitConnectionClosed(ref);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    /**
     * Try to connect to the host controller.
     *
     * @throws IOException
     */
    public void connect() throws IOException {
        connect(30, TimeUnit.SECONDS);
    }

    /**
     * Try to connect to the host controller.
     *
     * @param timeout the timeout
     * @param timeUnit the timeUnit
     */
    public void connect(final long timeout, final TimeUnit timeUnit) throws IOException {
        final DomainTestConnection connection = this.connection;
        if(connection == null) {
            throw new IllegalStateException();
        }
        final long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        for(;;) {
            long remaining = deadline - System.currentTimeMillis();
            if(remaining <= 0) {
                return;
            }
            try {
                // Open a connection
                connection.connect();
                return;
            } catch (IOException e) {
                remaining = deadline - System.currentTimeMillis();
                if(remaining <= 0) {
                    throw e;
                }
            }
        }
    }

    /**
     * Create a new model controller client. The client can (and should) be closed without affecting other usages.
     *
     * @return the domain client
     */
    public DomainClient createDomainClient() {
        final DomainTestConnection connection = this.connection;
        if(connection == null) {
            throw new IllegalStateException();
        }
        return DomainClient.Factory.create(connection.createClient());
    }

    /**
     * Get a shared domain client.
     *
     * @return the domain client
     */
    public synchronized DomainClient getDomainClient() {
        return DomainClient.Factory.create(internalGetOrCreateClient());
    }

    /** Wait for all auto-start servers for the host to reach {@link ControlledProcessState.State#RUNNING} */
    public void awaitServers(long start) throws InterruptedException, TimeoutException {

        boolean serversAvailable = false;
        long deadline = start + configuration.getStartupTimeoutInSeconds() * 1000;
        while (!serversAvailable && getProcessExitCode() < 0) {
            long remaining = deadline - System.currentTimeMillis();
            if(remaining <= 0) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(250);

            serversAvailable = areServersStarted();
        }

        if (!serversAvailable) {
            throw new TimeoutException(String.format("Managed servers were not started within [%d] seconds", configuration.getStartupTimeoutInSeconds()));
        }
    }

    public void awaitHostController(long start) throws InterruptedException, TimeoutException {

        boolean hcAvailable = false;
        long deadline = start + configuration.getStartupTimeoutInSeconds() * 1000;
        while (!hcAvailable && getProcessExitCode() < 0) {
            long remaining = deadline - System.currentTimeMillis();
            if(remaining <= 0) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(250);
            hcAvailable = isHostControllerStarted();
        }
        if (!hcAvailable) {
            throw new TimeoutException(String.format("HostController was not started within [%d] seconds", configuration.getStartupTimeoutInSeconds()));
        }
    }

    private synchronized DomainTestClient internalGetOrCreateClient() {
        // Perhaps get rid of the shared client...
        if (domainClient == null) {
            try {
                domainClient = connection.createClient();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return domainClient;
    }

    private synchronized ExecutorService getExecutorService() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(threadFactory);
        }
        return executor;
    }

    public boolean areServersStarted() {
        try {
            Map<ServerIdentity, ControlledProcessState.State> statuses = getServerStatuses();
            for (Map.Entry<ServerIdentity, ControlledProcessState.State> entry : statuses.entrySet()) {
                switch (entry.getValue()) {
                    case RUNNING:
                        continue;
                    default:
                        log.log(Level.INFO, entry.getKey() + " status is " + entry.getValue());
                        return false;
                }
            }
//            serverStatuses.putAll(statuses);
            return true;
        } catch (Exception ignored) {
            // ignore, as we will get exceptions until the management comm services start
        }
        return false;
    }

    public boolean isHostControllerStarted() {
        try {
            ModelNode address = new ModelNode();
            address.add("host", configuration.getHostName());

            ControlledProcessState.State status = Enum.valueOf(ControlledProcessState.State.class, readAttribute("host-state", address).asString().toUpperCase(Locale.ENGLISH));
            return status == ControlledProcessState.State.RUNNING;
        } catch (Exception ignored) {
            //
        }
        return false;
    }

    private synchronized void closeConnection() {
        if (connection != null) {
            try {
                domainClient = null;
                connection.close();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Caught exception closing DomainTestConnection", e);
            }
        }
    }

    private Map<ServerIdentity, ControlledProcessState.State> getServerStatuses() {

        Map<ServerIdentity, ControlledProcessState.State> result = new HashMap<ServerIdentity, ControlledProcessState.State>();
        ModelNode op = new ModelNode();
        op.get("operation").set("read-children-names");
        op.get("child-type").set("server-config");
        op.get("address").add("host", configuration.getHostName());
        ModelNode opResult = executeForResult(new OperationBuilder(op).build());
        Set<String> servers = new HashSet<String>();
        for (ModelNode server : opResult.asList()) {
            servers.add(server.asString());
        }
        for (String server : servers) {
            ModelNode address = new ModelNode();
            address.add("host", configuration.getHostName());
            address.add("server-config", server);
            String group = readAttribute("group", address).resolve().asString();
            if (!readAttribute("auto-start", address).resolve().asBoolean()) {
                continue;
            }
            // Make sure the server is started before trying to contact it
            final ServerIdentity id = new ServerIdentity(configuration.getHostName(), group, server);
            if (!readAttribute("status", address).asString().equals("STARTED")) {
                result.put(id, ControlledProcessState.State.STARTING);
                continue;
            }

            address = new ModelNode();
            address.add("host", configuration.getHostName());
            address.add("server", server);

            ControlledProcessState.State status = Enum.valueOf(ControlledProcessState.State.class, readAttribute("server-state", address).asString().toUpperCase(Locale.ENGLISH));
            result.put(id, status);
        }

        return result;
    }

    private ModelNode readAttribute(String name, ModelNode address) {
        ModelNode op = new ModelNode();
        op.get("operation").set("read-attribute");
        op.get("address").set(address);
        op.get("name").set(name);
        return executeForResult(new OperationBuilder(op).build());
    }

    public ModelNode executeForResult(ModelNode op) {
        return executeForResult(new OperationBuilder(op).build());
    }

    public ModelNode executeForResult(Operation op) {
        try {
            ModelNode result = getDomainClient().execute(op);
            if (result.hasDefined("outcome") && "success".equals(result.get("outcome").asString())) {
                return result.get("result");
            } else if (result.hasDefined("failure-description")) {
                throw new RuntimeException(result.get("failure-description").toString());
            } else if (result.hasDefined("domain-failure-description")) {
                throw new RuntimeException(result.get("domain-failure-description").toString());
            } else if (result.hasDefined("host-failure-descriptions")) {
                throw new RuntimeException(result.get("host-failure-descriptions").toString());
            } else {
                throw new RuntimeException("Operation outcome is " + result.get("outcome").asString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class AsyncThreadFactory implements ThreadFactory {

        private int threadCount;

        @Override
        public Thread newThread(Runnable r) {

            Thread t = new Thread(r, DomainLifecycleUtil.class.getSimpleName() + "-" + (++threadCount));
            t.setDaemon(true);
            return t;
        }
    }

    private static String copyConfigFile(final String file, final String dir) {
        return copyConfigFile(file, dir, "testing-");
    }

    private static String copyConfigFile(final String file, final Path dir) {
        return copyConfigFile(Paths.get(file), dir, "testing-");
    }

    private static String copyConfigFile(final String file, final String dir, final String prefix) {
        return copyConfigFile(Paths.get(file), Paths.get(dir), prefix);
    }

    private static String copyConfigFile(final String file, final Path dir, final String prefix) {
        return copyConfigFile(Paths.get(file), dir, prefix);
    }

    private static String copyConfigFile(final Path file, final Path dir, final String prefix) {
        final String p = prefix == null ? "" : prefix;
        final Path to = dir.resolve(p + file.getFileName());
        try {
            return Files.copy(file, to, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING).getFileName().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createFile(final Path file, final String line) throws IOException {
        try (final PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8), true)) {
            pw.println(line);
        }
    }

}
