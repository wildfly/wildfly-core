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

    static final String JBOSS_HOME = System.getProperty("jboss.home", System.getenv("JBOSS_HOME"));
    static final String MODULE_PATH = System.getProperty("module.path");
    static final String JVM_ARGS = System.getProperty("jvm.args", "-Xmx512m -XX:MaxPermSize=256m");
    static final String JBOSS_ARGS = System.getProperty("jboss.args");
    static final String JAVA_HOME = System.getProperty("java.home", System.getenv("JAVA_HOME"));
    static final String SERVER_CONFIG = System.getProperty("server.config", "standalone.xml");
    static final int MANAGEMENT_PORT = Integer.getInteger("management.port", 9990);
    static final String MANAGEMENT_ADDRESS = System.getProperty("management.address", "localhost");
    static final String MANAGEMENT_PROTOCOL = System.getProperty("management.protocol", "http-remoting");

    static final String SERVER_DEBUG = "wildfly.debug";
    static final int SERVER_DEBUG_PORT = Integer.getInteger("wildfly.debug.port", 8787);

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
                commandBuilder.setJavaOptions(JVM_ARGS.split("\\s+"));
            }
            if(Boolean.getBoolean(SERVER_DEBUG)) {
                commandBuilder.setDebug(true, SERVER_DEBUG_PORT);
            }

            //we are testing, of course we want assertions and set-up some other defaults
            commandBuilder.addJavaOption("-ea")
                    .setServerConfiguration(SERVER_CONFIG)
                    .setBindAddressHint("management", MANAGEMENT_ADDRESS);

            if (JBOSS_ARGS != null) {
                commandBuilder.addServerArguments(JBOSS_ARGS.split("\\s+"));
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
