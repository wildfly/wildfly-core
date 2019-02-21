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
import java.util.Map;
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
 * Represents a script. Note that this is not thread-safe.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptProcess extends Process implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ScriptProcess.class);

    private static final Function<String, String> WINDOWS_ARG_FORMATTER = s -> "\"" + s + "\"";
    private final Path script;
    private final Path stdoutLog;
    private final Path input;
    private final Function<ModelControllerClient, Boolean> check;
    private final Collection<String> prefixCmds;
    private Process delegate;
    private String lastExecutedCmd;

    ScriptProcess(final Path script, final Function<ModelControllerClient, Boolean> check, final String... prefixCmds) throws IOException {
        this.script = script;
        this.check = check;
        this.prefixCmds = new ArrayList<>(Arrays.asList(prefixCmds));
        final String baseFileName = script.getFileName().toString().replace('.', '-');
        stdoutLog = Environment.PROC_DIR.resolve(baseFileName + "-out.txt");
        input = Environment.PROC_DIR.resolve(baseFileName + "-in.txt");
        // Delete and create the input file
        Files.deleteIfExists(input);
        Files.createFile(input);
        lastExecutedCmd = "";
    }

    void start(final String... arguments) throws IOException, TimeoutException, InterruptedException {
        start(Arrays.asList(arguments));
    }

    @SuppressWarnings("WeakerAccess")
    void start(final Collection<String> arguments) throws IOException, TimeoutException, InterruptedException {
        start(Collections.emptyMap(), arguments);
    }

    @SuppressWarnings("SameParameterValue")
    void start(final Map<String, String> env, final String... arguments) throws IOException, TimeoutException, InterruptedException {
        start(env, Arrays.asList(arguments));
    }

    @SuppressWarnings("WeakerAccess")
    void start(final Map<String, String> env, final Collection<String> arguments) throws IOException, TimeoutException, InterruptedException {
        if (delegate != null) {
            throw new IllegalStateException("This process has already been started and has not exited.");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Attempting to start: %s", getCommandString(arguments));
        }
        lastExecutedCmd = getCommandString(arguments);
        final ProcessBuilder builder = new ProcessBuilder(getCommand(arguments))
                .directory(Environment.JBOSS_HOME.toFile())
                .redirectInput(input.toFile())
                .redirectErrorStream(true)
                .redirectOutput(stdoutLog.toFile());
        // The Windows scripts should not pause at the requiring user input
        if (Environment.isWindows()) {
            builder.environment().put("NOPAUSE", "true");
        }
        // Add any other environment variables
        if (env != null && !env.isEmpty()) {
            builder.environment().putAll(env);
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

    Path getStdout() {
        return stdoutLog;
    }

    @SuppressWarnings("unused")
    Path getInput() {
        return input;
    }

    String getErrorMessage(final String msg) {
        final StringBuilder errorMessage = new StringBuilder(msg)
                .append(System.lineSeparator())
                .append("Command Executed:")
                .append(System.lineSeparator())
                .append(lastExecutedCmd)
                .append(System.lineSeparator())
                .append("Environment:")
                .append(System.lineSeparator())
                .append("Output:")
                .append(System.lineSeparator());
        try {
            for (String line : Files.readAllLines(stdoutLog)) {
                errorMessage.append(line)
                            .append(System.lineSeparator());
            }
        } catch (IOException ignore) {
        }
        return errorMessage.toString();
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
        destroy(delegate);
        delegate = null;
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
        return getCommandString(Collections.emptyList());
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
        @SuppressWarnings("Convert2Lambda")
        final Callable<Boolean> callable = new Callable<Boolean>() {
            @Override
            public Boolean call() throws InterruptedException, IOException {
                long timeout = Environment.getTimeoutInMillis();
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
                destroy(process);
                throw new TimeoutException(getErrorMessage(String.format("The %s did not start within %d seconds.", script.getFileName(), Environment.getTimeout())));
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(getErrorMessage(String.format("Failed to determine if the %s server is running.", script.getFileName())), e);
        } finally {
            service.shutdownNow();
        }
    }

    private static void destroy(final Process process) {
        if (process != null && process.isAlive()) {
            final Process destroyed = process.destroyForcibly();
            try {
                if (destroyed.isAlive() && !destroyed.waitFor(Environment.getTimeout(), TimeUnit.SECONDS)) {
                    LOGGER.errorf("The process was not destroyed within %d seconds.", Environment.getTimeout());
                }
            } catch (InterruptedException e) {
                LOGGER.error("The process was interrupted while waiting to be destroyed.", e);
            }
        }
    }
}
