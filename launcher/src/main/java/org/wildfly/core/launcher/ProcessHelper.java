/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

/**
 * A helper class to help with managing a process.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ProcessHelper {

    /**
     * Checks to see if the process has died.
     *
     * @param process the process to check
     *
     * @return {@code true} if the process has died, otherwise {@code false}
     * @see Process#isAlive()
     */
    @SuppressWarnings("unused")
    @Deprecated(forRemoval = true)
    public static boolean processHasDied(final Process process) {
        return !process.isAlive();
    }

    /**
     * Destroys the process if the process is not {@code null}.
     *
     * @param process the process to destroy, terminate
     *
     * @return 0 if the process was successfully destroyed
     */
    public static int destroyProcess(final Process process) throws InterruptedException {
        if (process == null)
            return 0;
        process.destroy();
        return process.waitFor();
    }

    /**
     * Adds a shutdown hook for the process.
     *
     * @param process the process to add a shutdown hook for
     *
     * @return the thread set as the shutdown hook
     *
     * @throws java.lang.SecurityException If a security manager is present and it denies {@link
     *                                     java.lang.RuntimePermission <code>RuntimePermission("shutdownHooks")</code>}
     */
    public static Thread addShutdownHook(final Process process) {
        final Thread thread = new Thread(() -> {
            if (process != null) {
                process.destroyForcibly();
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(thread);
        return thread;
    }
}
