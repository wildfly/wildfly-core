/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.core.launcher;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a {@link java.lang.Process process} to launch a standalone or domain server based on the {@link
 * org.wildfly.core.launcher.CommandBuilder command builder}.
 * <p/>
 * The process is only created by the launcher and not managed. It's the responsibility of the consumer to manage the
 * process.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Launcher {

    private final CommandBuilder builder;
    private boolean redirectErrorStream;
    private Redirect outputDestination;
    private Redirect errorDestination;
    private File workingDirectory;
    private final Map<String, String> env;

    /**
     * Creates a new launcher.
     *
     * @param builder the builder to build the list of commands
     */
    public Launcher(final CommandBuilder builder) {
        this.builder = builder;
        redirectErrorStream = false;
        outputDestination = null;
        errorDestination = null;
        env = new HashMap<>();
    }

    /**
     * Creates a new launcher to create a {@link java.lang.Process process} based on the command builder.
     *
     * @param builder the builder used to launch the process
     *
     * @return the newly created launcher
     */
    public static Launcher of(final CommandBuilder builder) {
        return new Launcher(builder);
    }

    /**
     * Sets the output and error streams to inherit the output and error streams from it's parent process.
     *
     * @return the launcher
     */
    public Launcher inherit() {
        outputDestination = Redirect.INHERIT;
        errorDestination = Redirect.INHERIT;
        return this;
    }

    /**
     * Set to {@code true} if the error stream should be redirected to the output stream.
     *
     * @param redirectErrorStream {@code true} to merge the error stream into the output stream, otherwise {@code
     *                            false}
     *                            to keep the streams separate
     *
     * @return the launcher
     */
    public Launcher setRedirectErrorStream(final boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    /**
     * Redirects the output of the process to a file.
     *
     * @param file the file to redirect the output to
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder.Redirect#to(java.io.File)
     */
    public Launcher redirectOutput(final File file) {
        outputDestination = Redirect.to(file);
        return this;
    }

    /**
     * Redirects the output of the process to a file.
     *
     * @param path the path to redirect the output to
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder.Redirect#to(java.io.File)
     */
    public Launcher redirectOutput(final Path path) {
        return redirectOutput(path.toFile());
    }

    /**
     * Redirects the output of the process to the destination provided.
     *
     * @param destination the output destination
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder#redirectOutput(java.lang.ProcessBuilder.Redirect)
     */
    public Launcher redirectOutput(final Redirect destination) {
        outputDestination = destination;
        return this;
    }

    /**
     * Redirects the error stream of the process to a file.
     *
     * @param file the file to redirect the error stream to
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder.Redirect#to(java.io.File)
     */
    public Launcher redirectError(final File file) {
        errorDestination = Redirect.to(file);
        return this;
    }


    /**
     * Redirects the error stream of the process to the destination provided.
     *
     * @param destination the error stream destination
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder#redirectError(java.lang.ProcessBuilder.Redirect)
     */
    public Launcher redirectError(final Redirect destination) {
        errorDestination = destination;
        return this;
    }

    /**
     * Sets the working directory for the process created.
     *
     * @param path the path to the working directory
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder#directory(java.io.File)
     */
    public Launcher setDirectory(final Path path) {
        workingDirectory = path.toFile();
        return this;
    }

    /**
     * Sets the working directory for the process created.
     *
     * @param dir the working directory
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder#directory(java.io.File)
     */
    public Launcher setDirectory(final File dir) {
        workingDirectory = dir;
        return this;
    }

    /**
     * Sets the working directory for the process created.
     *
     * @param dir the working directory
     *
     * @return the launcher
     *
     * @see java.lang.ProcessBuilder#directory(java.io.File)
     */
    public Launcher setDirectory(final String dir) {
        return setDirectory(AbstractCommandBuilder.validateAndNormalizeDir(dir, true));
    }

    /**
     * Adds an environment variable to the process being created.
     *
     * @param key   they key for the variable
     * @param value the value for the variable
     *
     * @return the launcher
     */
    public Launcher addEnvironmentVariable(final String key, final String value) {
        env.put(key, value);
        return this;
    }

    /**
     * Adds the environment variables to the process being created.
     *
     * @param env the environment variables to add
     *
     * @return the launcher
     */
    public Launcher addEnvironmentVariables(final Map<String, String> env) {
        this.env.putAll(env);
        return this;
    }

    /**
     * Launches a new process based on the commands from the {@link org.wildfly.core.launcher.CommandBuilder builder}.
     *
     * @return the newly created process
     *
     * @throws IOException if an error occurs launching the process
     */
    public Process launch() throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(builder.build());
        if (outputDestination != null) {
            processBuilder.redirectOutput(outputDestination);
        }
        if (errorDestination != null) {
            processBuilder.redirectError(errorDestination);
        }
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory);
        }
        if (!env.isEmpty()) {
            processBuilder.environment().putAll(env);
        }
        processBuilder.redirectErrorStream(redirectErrorStream);
        return processBuilder.start();
    }
}
