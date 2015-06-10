package org.wildfly.core.testrunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * encapsulation of a server process
 *
 * @author Stuart Douglas
 */
public class Server {

    private final String jbossHome = System.getProperty("jboss.home", System.getenv("JBOSS_HOME"));
    private final String modulePath = System.getProperty("module.path");
    private final String jvmArgs = System.getProperty("jvm.args", "-Xmx512m");
    private final String jbossArgs = System.getProperty("jboss.args");
    private final String javaHome = System.getProperty("java.home", System.getenv("JAVA_HOME"));
    //Use this when specifying an older java to be used for running the server
    private final String legacyJavaHome = System.getProperty("legacy.java.home");
    private final String serverConfig = System.getProperty("server.config", "standalone.xml");
    private final int managementPort = Integer.getInteger("management.port", 9990);
    private final String managementAddress = System.getProperty("management.address", "localhost");
    private final String managementProtocol = System.getProperty("management.protocol", "http-remoting");

    private final String serverDebug = "wildfly.debug";
    private final int serverDebugPort = Integer.getInteger("wildfly.debug.port", 8787);

    private final Logger log = Logger.getLogger(Server.class.getName());
    private Thread shutdownThread;

    private volatile Process process;
    private volatile ManagementClient client;


    private static boolean processHasDied(final Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            // good
            return false;
        }
    }

    protected void start() {
        try {
            final Path jbossHomeDir = Paths.get(jbossHome);
            if (Files.notExists(jbossHomeDir) && !Files.isDirectory(jbossHomeDir)) {
                throw new IllegalStateException("Cannot find: " + jbossHomeDir);
            }

            final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(jbossHomeDir);

            if (modulePath != null && !modulePath.isEmpty()) {
                commandBuilder.setModuleDirs(modulePath.split(Pattern.quote(File.pathSeparator)));
            }

            commandBuilder.setJavaHome(legacyJavaHome == null ? javaHome : legacyJavaHome);
            if (jvmArgs != null) {
                commandBuilder.setJavaOptions(jvmArgs.split("\\s+"));
            }
            if(Boolean.getBoolean(serverDebug)) {
                commandBuilder.setDebug(true, serverDebugPort);
            }

            //we are testing, of course we want assertions and set-up some other defaults
            commandBuilder.addJavaOption("-ea")
                    .setServerConfiguration(serverConfig)
                    .setBindAddressHint("management", managementAddress);

            if (jbossArgs != null) {
                commandBuilder.addServerArguments(jbossArgs.split("\\s+"));
            }
            StringBuilder builder = new StringBuilder("Starting container with: ");
            for(String arg : commandBuilder.build()) {
                builder.append(arg).append(" ");
            }
            log.info(builder.toString());
            process = Launcher.of(commandBuilder)
                    // Redirect the output and error stream to a file
                    .setRedirectErrorStream(true)
                    .launch();
            new Thread(new ConsoleConsumer(process.getInputStream(), System.out)).start();
            final Process proc = process;
            shutdownThread = ProcessHelper.addShutdownHook(proc);


            ModelControllerClient modelControllerClient = null;
            try {
                modelControllerClient = ModelControllerClient.Factory.create(
                        managementProtocol,
                        managementAddress,
                        managementPort,
                        Authentication.getCallbackHandler());
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            client = new ManagementClient(modelControllerClient, managementAddress, managementPort, managementProtocol);

            long startupTimeout = 30;
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
                throw new RuntimeException("Managed server was not started within 30s");
            }

        } catch (Exception e) {
            safeCloseClient();
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

                try {
                    // AS7-6620: Create the shutdown operation and run it asynchronously and wait for process to terminate
                    client.getControllerClient().executeAsync(Operations.createOperation("shutdown"), null);
                } catch (AssertionError e) {
                    //ignore as this can only fail if shutdown is already in progress
                }

                if (process != null) {
                    process.waitFor();
                    process = null;
                }

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

    private void safeCloseClient() {
        try {
            if (client != null) {
                client.close();
                client = null;
            }
        } catch (final Exception e) {
            Logger.getLogger(this.getClass().getName()).warnf(e, "Caught exception closing ModelControllerClient");
        }
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


}
