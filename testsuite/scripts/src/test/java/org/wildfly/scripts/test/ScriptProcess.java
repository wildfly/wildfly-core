/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptProcess extends Process implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ScriptProcess.class);

    private static final Function<String, String> WINDOWS_ARG_FORMATTER = s -> "\"" + s + "\"";
    private final Path script;
    private final Path stdoutLog;
    private final Function<ModelControllerClient, Boolean> check;
    private final Collection<String> prefixCmds;
    private volatile Process delegate;
    private volatile String lastExecutedCmd;

    ScriptProcess(final Path script, final String... prefixCmds) {
        this(script, null, prefixCmds);
    }

    ScriptProcess(final Path script, final Function<ModelControllerClient, Boolean> check, final String... prefixCmds) {
        this.script = script;
        this.check = check;
        this.prefixCmds = new ArrayList<>(Arrays.asList(prefixCmds));
        stdoutLog = Environment.LOG_DIR.resolve(script.getFileName().toString().replace('.', '-') + ".log");
        lastExecutedCmd = "";
    }

    void start(final String... arguments) throws IOException, TimeoutException, InterruptedException {
        start(Arrays.asList(arguments));
    }

    @SuppressWarnings("WeakerAccess")
    void start(final Collection<String> arguments) throws IOException, TimeoutException, InterruptedException {
        // TODO (jrp) not thread-safe
        if (delegate != null) {
            throw new IllegalStateException("This process has already been started and has not exited.");
        }
        // TODO (jrp) needs to be removed
        System.out.printf("Attempting to start: %s%n", getCommandString(arguments));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Attempting to start: %s", getCommandString(arguments));
        }
        lastExecutedCmd = getCommandString(arguments);
        final ProcessBuilder builder = new ProcessBuilder(getCommand(arguments))
                .directory(Environment.JBOSS_HOME.toFile())
                .redirectErrorStream(true)
                .redirectOutput(stdoutLog.toFile());
        // The Windows scripts should not pause at the requiring user input
        if (Environment.isWindows()) {
            builder.environment().put("NOPAUSE", "true");
        }
        final Process process = builder.start();
        if (check != null) {
            waitFor(process, check);
        }
        this.delegate = process;
    }

    @SuppressWarnings("unused")
    Path getScript() {
        return script;
    }

    @SuppressWarnings("unused")
    Path getStdout() {
        return stdoutLog;
    }

    String getLastExecutedCmd() {
        return lastExecutedCmd;
    }

    private List<String> getCommand(final Collection<String> arguments) {
        final List<String> cmd = new ArrayList<>(prefixCmds);
        cmd.add(script.toString());
        if (Environment.isWindows()) {
            for (String arg : arguments) {
                cmd.add(WINDOWS_ARG_FORMATTER.apply(arg));
            }
        } else {
            cmd.addAll(arguments);
        }
        return cmd;
    }

    @Override
    public void close() {
        // TODO (jrp) this is not thread safe
        if (delegate != null) {
            delegate.destroyForcibly();
            delegate = null;
        }
    }

    @Override
    public OutputStream getOutputStream() {
        checkStatus();
        return delegate.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        checkStatus();
        return delegate.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        checkStatus();
        return delegate.getErrorStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
        checkStatus();
        return delegate.waitFor();
    }

    @Override
    public boolean waitFor(final long timeout, final TimeUnit unit) throws InterruptedException {
        checkStatus();
        return delegate.waitFor(timeout, unit);
    }

    @Override
    public int exitValue() {
        checkStatus();
        return delegate.exitValue();
    }

    @Override
    public void destroy() {
        checkStatus();
        delegate.destroy();
    }

    @Override
    public Process destroyForcibly() {
        checkStatus();
        return delegate.destroyForcibly();
    }

    @Override
    public boolean isAlive() {
        checkStatus();
        return delegate.isAlive();
    }

    @Override
    public String toString() {
        return getCommandString(Collections.singleton(script.toString()));
    }

    private String getCommandString(final Collection<String> arguments) {
        final List<String> cmd = getCommand(arguments);
        final StringBuilder result = new StringBuilder();
        final Iterator<String> iter = cmd.iterator();
        while (iter.hasNext()) {
            result.append(iter.next());
            if (iter.hasNext()) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    private void checkStatus() {
        if (delegate == null) {
            throw new IllegalStateException("The script has not yet been started.");
        }
    }

    private void waitFor(final Process process, final Function<ModelControllerClient, Boolean> check) throws TimeoutException, InterruptedException {
        final Callable<Boolean> callable = new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException, IOException {
                long timeout = Environment.TIMEOUT * 1000;
                final long sleep = 100L;
                try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                    while (timeout > 0) {
                        if (!process.isAlive()) {
                            return false;
                        }
                        long before = System.currentTimeMillis();
                        if (check.apply(client)) {
                            return true;
                        }
                        timeout -= (System.currentTimeMillis() - before);
                        TimeUnit.MILLISECONDS.sleep(sleep);
                        timeout -= sleep;
                    }
                }
                return false;
            }
        };
        final ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            final Future<Boolean> future = service.submit(callable);
            if (!future.get()) {
                if (process != null) {
                    process.destroyForcibly();
                }
                final StringBuilder errorMessage = new StringBuilder()
                        .append("The ")
                        .append(script.getFileName())
                        .append(" did not start within ")
                        .append(Environment.TIMEOUT)
                        .append(" seconds.")
                        .append(System.lineSeparator())
                        .append("Command:")
                        .append(System.lineSeparator())
                        .append(this)
                        .append(System.lineSeparator())
                        .append("Failure:")
                        .append(System.lineSeparator());
                try {
                    for (String line : Files.readAllLines(stdoutLog)) {
                        errorMessage.append(line).append(System.lineSeparator());
                    }
                } catch (IOException ignore) {
                }
                throw new TimeoutException(errorMessage.toString());
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(String.format("Failed to determine if the %s server is running.", script.getFileName()), e);
        } finally {
            service.shutdownNow();
        }
    }
}
