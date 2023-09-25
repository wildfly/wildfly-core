/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.io.Console;
import java.io.IOError;
import java.util.IllegalFormatException;

/**
 * Describe the purpose
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class JavaConsole implements ConsoleWrapper {

    private Console theConsole = System.console();

    @Override
    public void format(String fmt, Object... args) throws IllegalFormatException {
        if (hasConsole()) {
            theConsole.format(fmt, args);
        } else {
            System.out.format(fmt, args);
        }
    }

    @Override
    public void printf(String format, Object... args) throws IllegalFormatException {
        if (hasConsole()) {
            theConsole.printf(format, args);
        } else {
            System.out.format(format, args);
        }
    }

    @Override
    public String readLine(String fmt, Object... args) throws IOError {
        if (hasConsole()) {
            return theConsole.readLine(fmt, args);
        } else {
            throw ROOT_LOGGER.noConsoleAvailable();
        }
    }

    @Override
    public char[] readPassword(String fmt, Object... args) throws IllegalFormatException, IOError {
        if (hasConsole()) {
            return theConsole.readPassword(fmt, args);
        } else {
            throw ROOT_LOGGER.noConsoleAvailable();
        }
    }

    @Override
    public boolean hasConsole() {
        return theConsole != null;
    }


}
