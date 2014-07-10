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
     */
    public static boolean processHasDied(final Process process) {
        try {
            // The process is still running
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException ignore) {
        }
        return false;
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
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (process != null) {
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        thread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(thread);
        return thread;
    }
}
