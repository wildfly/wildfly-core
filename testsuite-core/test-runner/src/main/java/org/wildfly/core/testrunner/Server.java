package org.wildfly.core.testrunner;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * encapsulation of a server process
 *
 * @author Stuart Douglas
 */
public class Server {

    static final String JBOSS_HOME = System.getProperty("jboss.home", System.getenv("JBOSS_HOME"));
    static final String MODULE_PATH = System.getProperty("module.path");
    static final String JVM_ARGS = System.getProperty("jvm.args");
    static final String JBOSS_ARGS = System.getProperty("jboss.args");
    static final String JAVA_HOME = System.getProperty("java.home", System.getenv("JAVA_HOME"));
    static final String SERVER_CONFIG = System.getProperty("server.config", "standalone.xml");
    static final int MANAGEMENT_PORT = Integer.getInteger("management.port", 9990);
    static final String MANAGEMENT_ADDRESS = System.getProperty("management.address", "localhost");
    static final String MANAGEMENT_PROTOCOL = System.getProperty("management.protocol", "http-remoting");
    static final Path PROCESS_LOG_FILE = Paths.get(System.getProperty("process.log.file", "process.log"));

    private final Logger log = Logger.getLogger(Server.class.getName());
    private Thread shutdownThread;

    private volatile Process process;
    private volatile ManagementClient client;

    protected void start() {
        try {
            final Path jbossHomeDir = Paths.get(JBOSS_HOME);
            if (Files.notExists(jbossHomeDir) && !Files.isDirectory(jbossHomeDir)) {
                throw new IllegalStateException("Cannot find: " + jbossHomeDir);
            }

            final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHomeDir);

            if (MODULE_PATH != null && !MODULE_PATH.isEmpty()) {
                commandBuilder.setModuleDirs(MODULE_PATH.split(Pattern.quote(File.pathSeparator)));
            }

            commandBuilder.setJavaHome(JAVA_HOME);
            if (JVM_ARGS != null) {
                commandBuilder.setJvmArgs(JVM_ARGS.split("\\s+"));
            }

            //we are testing, of course we want assertions
            commandBuilder.addJvmArg("-ea")
                    .setServerConfiguration(SERVER_CONFIG);

            if (JBOSS_ARGS != null) {
                commandBuilder.addServerArguments(JBOSS_ARGS.split("\\s+"));
            }

            log.info("Starting container with: " + commandBuilder.build());
            process = Launcher.of(commandBuilder)
                    // Redirect the output and error stream to a file
                    .setRedirectErrorStream(true)
                    .redirectOutput(PROCESS_LOG_FILE)
                    .launch();
            final Process proc = process;
            shutdownThread = ProcessHelper.addShutdownHook(proc);


            ModelControllerClient modelControllerClient = null;
            try {
                modelControllerClient = ModelControllerClient.Factory.create(
                        MANAGEMENT_PROTOCOL,
                        MANAGEMENT_ADDRESS,
                        MANAGEMENT_PORT,
                        Authentication.getCallbackHandler());
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            client = new ManagementClient(modelControllerClient, MANAGEMENT_ADDRESS, MANAGEMENT_PORT, MANAGEMENT_PROTOCOL);

            long startupTimeout = 30;
            long timeout = startupTimeout * 1000;
            boolean serverAvailable = false;
            long sleep = 1000;
            while (timeout > 0 && !serverAvailable) {
                long before = System.currentTimeMillis();
                serverAvailable = client.isServerInRunningState();
                timeout -= (System.currentTimeMillis() - before);
                if (!serverAvailable) {
                    if (ProcessHelper.processHasDied(proc))
                        break;
                    Thread.sleep(sleep);
                    timeout -= sleep;
                    sleep = Math.max(sleep / 2, 100);
                }
            }
            if (!serverAvailable) {
                ProcessHelper.destroyProcess(proc);
                throw new RuntimeException("Managed server was not started within 30s");
            }

        } catch (Exception e) {
            throw new RuntimeException("Could not start container", e);
        }
    }

    protected void stop() {
        if (shutdownThread != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownThread);
            shutdownThread = null;
        }
        try {
            if (process != null) {
                Thread shutdown = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long timeout = System.currentTimeMillis() + 10000;
                        while (process != null && System.currentTimeMillis() < timeout) {
                            try {
                                process.exitValue();
                                process = null;
                            } catch (IllegalThreadStateException e) {

                            }
                        }

                        // The process hasn't shutdown within 60 seconds. Terminate forcibly.
                        if (process != null) {
                            process.destroy();
                        }
                    }
                });
                shutdown.start();

                // AS7-6620: Create the shutdown operation and run it asynchronously and wait for process to terminate
                client.getControllerClient().executeAsync(Operations.createOperation("shutdown"), null);

                process.waitFor();
                process = null;

                shutdown.interrupt();
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
        }
    }

    public ManagementClient getClient() {
        return client;
    }

}
