/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import java.io.IOError;
import java.util.IllegalFormatException;

/**
 * Wrap the console commands
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public interface ConsoleWrapper {

    /**
     * Writes a formatted string to this console's output stream using
     * the specified format string and arguments.
     * see <a href="../util/Formatter.html#syntax">Format string syntax</a>
     * @param fmt
     * @param args
     */
    void format(String fmt, Object ...args) throws IllegalFormatException;

    /**
     * A convenience method to write a formatted string to this console's
     * output stream using the specified format string and arguments.
     *
     * @param format
     * @param args
     * @throws IllegalStateException
     */
    void printf(String format, Object ... args) throws IllegalFormatException;

    /**
     * Provides a formatted prompt, then reads a single line of text from the
     * console.
     *
     * @param fmt
     * @param args
     * @return A string containing the line read from the console, not
     *          including any line-termination characters, or <tt>null</tt>
     *          if an end of stream has been reached.
     * @throws IOError
     */
    String readLine(String fmt, Object ... args) throws IOError;

    /**
     * Provides a formatted prompt, then reads a password or passphrase from
     * the console with echoing disabled.
     *
     * @param fmt
     * @param args
     * @return  A character array containing the password or passphrase read
     *          from the console.
     * @throws IOError
     */
    char[] readPassword(String fmt, Object ... args) throws IllegalFormatException, IOError;

    /**
     * Check if the wrapper does have a console.
     *
     * @return true if the wrapper does have a console.
     */
    boolean hasConsole();
}
