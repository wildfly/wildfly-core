/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.function.Consumer;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.impl.ReadlineConsole.Settings;

/**
 *
 * @author Alexey Loubyansky
 */
public interface Console {

    void addCompleter(CommandLineCompleter completer);

    CommandHistory getHistory();

    void clearScreen();

    void printColumns(Collection<String> list);

    void print(String line);

    void printNewLine();

    String readLine(String prompt) throws IOException;

    String readLine(String prompt, Character mask) throws IOException;

    int getTerminalWidth();

    int getTerminalHeight();

    void setActionCallback(Consumer<String> cons);

    void start() throws IOException;

    void stop();

    boolean running();

    void setPrompt(String prompt);

    static final class Factory {

        public static Console getConsole(CommandContext ctx, Settings settings) throws CliInitializationException {
            return getConsole(ctx, null, null, settings);
        }

        public static Console getConsole(final CommandContext ctx, InputStream is, OutputStream os, Settings settings) throws CliInitializationException {
            ReadlineConsole console;
            try {
                console = new ReadlineConsole(ctx, settings);
            } catch (IOException ex) {
                throw new CliInitializationException(ex);
            }
            return console;
        }
    }

}
