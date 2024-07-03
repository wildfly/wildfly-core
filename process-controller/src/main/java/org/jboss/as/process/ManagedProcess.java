/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

import static java.lang.Thread.holdsLock;
import static org.jboss.as.process.CommandLineConstants.MANAGED_PROCESS_SYSTEM_ERROR_TO_LOG;
import static org.jboss.as.process.CommandLineConstants.MANAGED_PROCESS_SYSTEM_OUT_TO_LOG;
import static org.jboss.as.process.protocol.StreamUtils.copyStream;
import static org.jboss.as.process.protocol.StreamUtils.safeClose;
import static org.jboss.as.process.protocol.StreamUtils.writeBoolean;
import static org.jboss.as.process.protocol.StreamUtils.writeInt;
import static org.jboss.as.process.protocol.StreamUtils.writeUTFZBytes;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jboss.as.process.logging.ProcessLogger;
import org.jboss.as.process.stdin.Base64OutputStream;
import org.jboss.logging.Logger;
import org.wildfly.common.Assert;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * A managed process.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
final class ManagedProcess {
    // If true, the managed process standard error will be captured in the process controller log file until the managed
    // process has installed the JBoss Stdio context, otherwise the output will be redirected to the Process Controller
    // standard error.
    private static final boolean MANAGED_PROCESS_SYSTEM_ERROR_TO_PROCESS_CONTROLLER_LOG = Boolean.parseBoolean(
            WildFlySecurityManager.getPropertyPrivileged(MANAGED_PROCESS_SYSTEM_ERROR_TO_LOG, "true")
    );
    // If true, the managed process standard output will be captured in the process controller log file until the managed
    // process has installed the JBoss Stdio context, otherwise the output will be redirected to the Process Controller
    // standard output.
    private static final boolean MANAGED_PROCESS_SYSTEM_OUT_TO_PROCESS_CONTROLLER_LOG = Boolean.parseBoolean(
            WildFlySecurityManager.getPropertyPrivileged(MANAGED_PROCESS_SYSTEM_OUT_TO_LOG, "true")
    );

    private final String processName;
    private final List<String> command;
    private final Map<String, String> env;
    private final String workingDirectory;
    private final ProcessLogger logStatus;
    private final ProcessLogger logSystemErr;
    private final ProcessLogger logSystemOut;
    private final Object lock;

    private final ProcessController processController;
    private final String pcAuthKey;
    private final boolean isPrivileged;
    private final RespawnPolicy respawnPolicy;
    private final int id;

    private OutputStream stdin;
    private volatile State state = State.DOWN;
    private volatile Thread joinThread;
    private Process process;
    private boolean shutdown;
    private boolean stopRequested = false;
    private final AtomicInteger respawnCount = new AtomicInteger(0);

    public String getPCAuthKey() {
        return pcAuthKey;
    }

    public boolean isPrivileged() {
        return isPrivileged;
    }

    public boolean isRunning() {
        return (state == State.STARTED) || (state == State.STOPPING);
    }

    public boolean isStopping() {
        return state == State.STOPPING;
    }

    enum State {
        DOWN,
        STARTED,
        STOPPING,
        ;
    }

    ManagedProcess(final String processName, final int id, final List<String> command, final Map<String, String> env, final String workingDirectory, final Object lock, final ProcessController controller, final String pcAuthKey, final boolean privileged, final boolean respawn) {
        Assert.checkNotNullParam("processName", processName);
        Assert.checkNotNullParam("command", command);
        Assert.checkNotNullParam("env", env);
        Assert.checkNotNullParam("workingDirectory", workingDirectory);
        Assert.checkNotNullParam("lock", lock);
        Assert.checkNotNullParam("controller", controller);
        Assert.checkNotNullParam("pcAuthKey", pcAuthKey);
        if (pcAuthKey.length() != ProcessController.AUTH_BYTES_ENCODED_LENGTH) {
            throw ProcessLogger.ROOT_LOGGER.invalidLength("pcAuthKey");
        }
        this.processName = processName;
        this.id = id;
        this.command = command;
        this.env = env;
        this.workingDirectory = workingDirectory;
        this.lock = lock;
        processController = controller;
        this.pcAuthKey = pcAuthKey;
        isPrivileged = privileged;
        respawnPolicy = respawn ? RespawnPolicy.RESPAWN : RespawnPolicy.NONE;
        logStatus = Logger.getMessageLogger(MethodHandles.lookup(), ProcessLogger.class, "org.jboss.as.process." + processName + ".status");
        logSystemErr = Logger.getMessageLogger(MethodHandles.lookup(), ProcessLogger.class, "org.jboss.as.process." + processName + ".system.stderr");
        logSystemOut = Logger.getMessageLogger(MethodHandles.lookup(), ProcessLogger.class, "org.jboss.as.process." + processName + ".system.stdout");
    }

    int incrementAndGetRespawnCount() {
        return respawnCount.incrementAndGet();
    }

    int resetRespawnCount() {
        return respawnCount.getAndSet(0);
    }

    public String getProcessName() {
        return processName;
    }

    public void start() {
        synchronized (lock) {
            if (state != State.DOWN) {
                logStatus.debugf("Attempted to start already-running process '%s'", processName);
                return;
            }
            resetRespawnCount();
            doStart(false);
        }
    }

    public void sendStdin(final InputStream msg) throws IOException {
        assert holdsLock(lock); // Call under lock
        try {
            // WFLY-2697 All writing is in Base64
            Base64OutputStream base64 = getBase64OutputStream(stdin);
            copyStream(msg, base64);
            base64.close(); // not flush(). close() writes extra data to the stream allowing Base64 input stream
                            // to distinguish end of message
        } catch (IOException e) {
            logStatus.failedToSendDataBytes(e, processName);
            throw e;
        }
    }

    public void reconnect(String scheme, String hostName, int port, boolean managementSubsystemEndpoint, String serverAuthToken) {
        assert holdsLock(lock); // Call under lock
        try {
            // WFLY-2697 All writing is in Base64
            Base64OutputStream base64 = getBase64OutputStream(stdin);
            writeUTFZBytes(base64, scheme);
            writeUTFZBytes(base64, hostName);
            writeInt(base64, port);
            writeBoolean(base64, managementSubsystemEndpoint);
            writeUTFZBytes(base64, serverAuthToken);
            base64.close(); // not flush(). close() writes extra data to the stream allowing Base64 input stream
                            // to distinguish end of message
        } catch (IOException e) {
            if(state == State.STARTED) {
                // Only log in case the process is still running
                logStatus.failedToSendReconnect(e, processName);
            }
        }
    }

    void doStart(boolean restart) {
        // Call under lock
        assert holdsLock(lock);
        stopRequested = false;
        final List<String> command = new ArrayList<String>(this.command);
        if(restart) {
            //Add the restart flag to the HC process if we are respawning it
            command.add(CommandLineConstants.PROCESS_RESTARTED);
        }
        logStatus.startingProcess(processName);
        logStatus.debugf("Process name='%s' command='%s' workingDirectory='%s'", processName, command, workingDirectory);
        List<String> list = new ArrayList<>();
        for (String c : command) {
            String trim = c.trim();
            list.add(trim);
        }
        final ProcessBuilder builder = new ProcessBuilder(list);
        builder.environment().putAll(env);
        builder.directory(new File(workingDirectory));
        final Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            processController.operationFailed(processName, ProcessMessageHandler.OperationType.START);
            logStatus.failedToStartProcess(e,processName);
            return;
        }
        final long startTime = System.currentTimeMillis();
        final OutputStream stdin = process.getOutputStream();
        final InputStream stderr = process.getErrorStream();
        final InputStream stdout = process.getInputStream();
        final Thread stderrThread = new Thread( new ReadTask(stderr, processController.getStderr(),
                MANAGED_PROCESS_SYSTEM_ERROR_TO_PROCESS_CONTROLLER_LOG,
                logSystemErr::error)
        );
        stderrThread.setName(String.format("stderr for %s", processName));
        stderrThread.start();
        final Thread stdoutThread = new Thread(new ReadTask(stdout, processController.getStdout(),
                MANAGED_PROCESS_SYSTEM_OUT_TO_PROCESS_CONTROLLER_LOG,
                logSystemOut::info)
        );
        stdoutThread.setName(String.format("stdout for %s", processName));
        stdoutThread.start();

        joinThread = new Thread(new JoinTask(startTime));
        joinThread.setName(String.format("reaper for %s", processName));
        joinThread.start();
        boolean ok = false;
        try {
            // WFLY-2697 All writing is in Base64
            OutputStream base64 = getBase64OutputStream(stdin);
            base64.write(pcAuthKey.getBytes(StandardCharsets.US_ASCII));
            base64.close(); // not flush(). close() writes extra data to the stream allowing Base64 input stream
                            // to distinguish end of message
            ok = true;
        } catch (Exception e) {
            logStatus.failedToSendAuthKey(processName, e);
        }

        this.process = process;
        this.stdin = stdin;

        if(ok) {
            state = State.STARTED;
            processController.processStarted(processName);
        } else {
            processController.operationFailed(processName, ProcessMessageHandler.OperationType.START);
        }
    }

    public void stop() {
        synchronized (lock) {
            if (state != State.STARTED) {
                logStatus.debugf("Attempted to stop already-stopping or down process '%s'", processName);
                return;
            }
            logStatus.stoppingProcess(processName);
            stopRequested = true;
            safeClose(stdin);
            state = State.STOPPING;
        }
    }

    public void destroy() {
        synchronized (lock) {
            Thread jt = joinThread;
            if(state != State.STOPPING) {
                stop(); // Try to stop before destroying the process
            }

            final long timeout = 5000;
            if (state != State.DOWN && jt != null) {
                try {
                    // Give stop() a small amount of time to work,
                    // in case the user asked for a destroy when a normal stop
                    // was sufficient. But the base assumption is the destroy
                    // is needed
                    jt.join(timeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (state != State.DOWN || jt == null || jt.isAlive()) { // Cover all bases just to be robust
                logStatus.destroyingProcess(processName, timeout);
                process.destroyForcibly();
            }
        }
    }

    public void kill() {
        synchronized (lock) {
            Thread jt = joinThread;
            if(state != State.STOPPING) {
                stop(); // Try to stop before killing the process
            }

            final long timeout = 5000;
            if (state != State.DOWN && jt != null) {
                try {
                    // Give stop() a small amount of time to work,
                    // in case the user asked for a kill when a normal stop
                    // was sufficient. But the base assumption is the kill
                    // is needed
                    jt.join(timeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (state != State.DOWN || jt == null || jt.isAlive()) { // Cover all bases just to be robust
                logStatus.attemptingToKillProcess(processName, timeout);
                if (!ProcessUtils.killProcess(processName, id)) {
                    // Fallback to destroy if kill is not available
                    logStatus.failedToKillProcess(processName);
                    process.destroyForcibly();
                }
            }
        }
    }

    public void shutdown() {
        synchronized (lock) {
            if(shutdown) {
                return;
            }
            shutdown = true;
            if (state == State.STARTED) {
                logStatus.stoppingProcess(processName);
                stopRequested = true;
                safeClose(stdin);
                state = State.STOPPING;
            } else if (state == State.STOPPING) {
                return;
            } else {
                new Thread() {
                    @Override
                    public void run() {
                        processController.removeProcess(processName);
                    }
                }.start();
            }
        }
    }

    void respawn() {
        synchronized (lock) {
            if (state != State.DOWN) {
                logStatus.debugf("Attempted to respawn already-running process '%s'", processName);
                return;
            }
            doStart(true);
        }
    }

    private static Base64OutputStream getBase64OutputStream(OutputStream toWrap) {
        // We'll call close on Base64OutputStream at the end of each message
        // to serve as a delimiter. Don't let that close the underlying stream.
        OutputStream nonclosing = new FilterOutputStream(toWrap) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
        return new Base64OutputStream(nonclosing);
    }

    private final class JoinTask implements Runnable {
        private final long startTime;

        public JoinTask(final long startTime) {
            this.startTime = startTime;
        }

        public void run() {
            final Process process;
            synchronized (lock) {
                process = ManagedProcess.this.process;
            }
            int exitCode;
            for (;;) try {
                exitCode = process.waitFor();
                logStatus.processFinished(processName, exitCode);
                break;
            } catch (InterruptedException e) {
                // ignore
            }
            boolean respawn = false;
            boolean slowRespawn = false;
            boolean unlimitedRespawn = false;
            int respawnCount = 0;
            synchronized (lock) {

                final long endTime = System.currentTimeMillis();
                processController.processStopped(processName, endTime - startTime);
                state = State.DOWN;

                if (shutdown) {
                    processController.removeProcess(processName);
                } else if (isPrivileged() && exitCode == ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE) {
                    // Host Controller abort. See if there are other running processes the HC
                    // needs to manage. If so we must restart the HC.
                    if (processController.getOngoingProcessCount() > 1) {
                        respawn = true;
                        respawnCount = ManagedProcess.this.incrementAndGetRespawnCount();
                        unlimitedRespawn = true;
                        // We already have servers, so this isn't an abort in the early stages of the
                        // initial HC boot. Likely it's due to a problem in a reload, which will require
                        // some sort of user intervention to resolve. So there is no point in immediately
                        // respawning and spamming the logs.
                        slowRespawn = true;
                    } else {
                        processController.removeProcess(processName);
                        new Thread(new Runnable() {
                            public void run() {
                                processController.shutdown();
                                System.exit(ExitCodes.NORMAL);
                            }
                        }).start();
                    }
                } else if (isPrivileged() && exitCode == ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT) {
                    // Host Controller restart via exit code picked up by script
                    processController.removeProcess(processName);
                    new Thread(new Runnable() {
                        public void run() {
                            processController.shutdown();
                            System.exit(ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT);
                        }
                    }).start();

                } else {
                    if(! stopRequested) {
                        respawn = true;
                        respawnCount = ManagedProcess.this.incrementAndGetRespawnCount();
                        if (isPrivileged() && processController.getOngoingProcessCount() > 1) {
                            // This is an HC with live servers to manage, so never give up on
                            // restarting
                            unlimitedRespawn = true;
                        }
                    }
                }
                stopRequested = false;
            }
            if(respawn) {
                respawnPolicy.respawn(respawnCount, ManagedProcess.this, slowRespawn, unlimitedRespawn);
            }
        }
    }

    private final class ReadTask implements Runnable {
        private final InputStream source;
        private final PrintStream target;
        private boolean useLog;
        private final Consumer<String> logConsumer;

        private ReadTask(final InputStream source, final PrintStream target, boolean useLog, Consumer<String> logConsumer) {
            this.source = source;
            this.target = target;
            this.useLog = useLog;
            this.logConsumer = logConsumer;
        }

        public void run() {
            final InputStream source = this.source;
            final String processName = ManagedProcess.this.processName;
            try {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(source), StandardCharsets.UTF_8));
                final OutputStreamWriter writer = new OutputStreamWriter(target, StandardCharsets.UTF_8);
                String s;
                String prevEscape = "";
                while ((s = reader.readLine()) != null) {

                    if (s.contains(ProcessController.STDIO_ABOUT_TO_INSTALL_MSG)) {
                        useLog = false;
                        continue;
                    }

                    // Has ANSI?
                    int i = s.lastIndexOf('\033');
                    int j = i != -1 ? s.indexOf('m', i) : 0;

                    if (useLog) {
                        StringBuilder sp = new StringBuilder();
                        sp.append("[");
                        sp.append(processName);
                        sp.append("] ");
                        sp.append(prevEscape);
                        sp.append(s);

                        // Reset if there was ANSI
                        if (j != 0 || !prevEscape.isEmpty()) {
                            sp.append("\033[0m");
                        }

                        logConsumer.accept(sp.toString());
                    } else {
                        synchronized (target) {
                            writer.write('[');
                            writer.write(processName);
                            writer.write("] ");
                            writer.write(prevEscape);
                            writer.write(s);

                            // Reset if there was ANSI
                            if (j != 0 || !prevEscape.isEmpty()) {
                                writer.write("\033[0m");
                            }
                            writer.write('\n');
                            writer.flush();
                        }
                    }

                    // Remember escape code for the next line
                    if (j != 0) {
                        String escape = s.substring(i, j + 1);
                        if (!"\033[0m".equals(escape)) {
                            prevEscape = escape;
                        } else {
                            prevEscape = "";
                        }
                    }
                }
                source.close();
            } catch (IOException e) {
                logStatus.streamProcessingFailed(processName, e);
            } finally {
                safeClose(source);
            }
        }
    }
}
