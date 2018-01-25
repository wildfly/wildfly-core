/*
 *
 *  * JBoss, Home of Professional Open Source.
 *  * Copyright 2011, Red Hat, Inc., and individual contributors
 *  * as indicated by the @author tags. See the copyright.txt file in the
 *  * distribution for a full listing of individual contributors.
 *  *
 *  * This is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU Lesser General Public License as
 *  * published by the Free Software Foundation; either version 2.1 of
 *  * the License, or (at your option) any later version.
 *  *
 *  * This software is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this software; if not, write to the Free
 *  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */

package org.jboss.as.domain.management.security.adduser;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.util.IllegalFormatException;
import org.aesh.readline.Prompt;
import org.aesh.readline.tty.terminal.TerminalConnection;

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
        ReadLineHandler readLineHandler = new ReadLineHandler(new Prompt(String.format(fmt, args)));
        return readInputLine(readLineHandler);
    }

    private String readInputLine(ReadLineHandler readLineHandler) {
        try {
            createTerminalConnection(readLineHandler);
        } catch (IOException e) {
            throw ROOT_LOGGER.noConsoleAvailable();
        }

        return readLineHandler.getLine();
    }

    protected void createTerminalConnection(ReadLineHandler readLineHandler) throws IOException {
        //Side effect - this starts the new connection to terminal and asks user for input.
        //This call will block this thread while it reads a line from user.
        new TerminalConnection(readLineHandler);
    }

    @Override
    public char[] readPassword(String fmt, Object... args) throws IllegalFormatException, IOError {
        ReadLineHandler readLineHandler = new ReadLineHandler(new Prompt(String.format(fmt, args), Character.MIN_VALUE));
        return readInputLine(readLineHandler).toCharArray();
    }

    @Override
    public boolean hasConsole() {
        return theConsole != null;
    }
}
