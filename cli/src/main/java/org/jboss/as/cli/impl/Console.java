/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.aesh.complete.CompleteOperation;
import org.jboss.aesh.complete.Completion;
import org.jboss.aesh.console.AeshConsoleBufferBuilder;
import org.jboss.aesh.console.AeshInputProcessorBuilder;
import org.jboss.aesh.console.ConsoleBuffer;
import org.jboss.aesh.console.ConsoleCallback;
import org.jboss.aesh.console.InputProcessor;
import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.settings.Settings;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;

/**
 *
 * @author Alexey Loubyansky
 */
public interface Console {

    void addCompleter(CommandLineCompleter completer);

    boolean isUseHistory();

    CommandHistory getHistory();

    void clearScreen();

    void printColumns(Collection<String> list);

    void print(String line);

    void printNewLine();

    String readLine(String prompt);

    String readLine(String prompt, Character mask);

    int getTerminalWidth();

    int getTerminalHeight();

    void setCallback(ConsoleCallback consoleCallback);

    void start();

    void stop();

    boolean running();

    void setPrompt(String prompt);

    static final class Factory {

        public static Console getConsole(CommandContext ctx, Settings settings) throws CliInitializationException {
            return getConsole(ctx, settings, null, null);
        }

        public static Console getConsole(final CommandContext ctx, final Settings set, InputStream is, OutputStream os) throws CliInitializationException {

            org.jboss.aesh.console.Console aeshConsole;
            final Settings settings = set;
            aeshConsole = new org.jboss.aesh.console.Console(settings);

            final org.jboss.aesh.console.Console finalAeshConsole = aeshConsole;
            return new Console() {

                private CommandContext cmdCtx = ctx;
                private org.jboss.aesh.console.Console console = finalAeshConsole;
                private CommandHistory history = new HistoryImpl();

                @Override
                public void addCompleter(final CommandLineCompleter completer) {
                    console.addCompletion(new Completion() {
                        @Override
                        public void complete(CompleteOperation co) {
                            List<String> candidates = new ArrayList<>();
                            int offset =  completer.complete(cmdCtx,
                                    co.getBuffer(), co.getCursor(), candidates);
                            co.setOffset(offset);
                            co.addCompletionCandidates(candidates);
                            if(candidates.size() == 1 && candidates.get(0).startsWith(co.getBuffer()))
                                co.doAppendSeparator(true);
                            else
                                co.doAppendSeparator(false);
                        }
                    });
                }

                @Override
                public boolean isUseHistory() {
                    return settings.isHistoryDisabled();
                }

                @Override
                public CommandHistory getHistory() {
                    return history;
                }

                @Override
                public void clearScreen() {
                    try {
                        console.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void printColumns(Collection<String> list) {
                    String[] newList = new String[list.size()];
                    list.toArray(newList);
                    console.getShell().out().println(
                            org.jboss.aesh.parser.Parser.formatDisplayList(newList,
                                    console.getTerminalSize().getHeight(),
                                    console.getTerminalSize().getWidth()));
                }

                @Override
                public void print(String line) {
                    console.getShell().out().print(line);
                }

                @Override
                public void printNewLine() {
                    console.getShell().out().println();
                }

                @Override
                public String readLine(String prompt) {
                    return read(prompt, null);
                }

                @Override
                public String readLine(String prompt, Character mask) {
                    return read(prompt, mask);
                }

                private String read(String prompt, Character mask) {

                    ConsoleBuffer consoleBuffer = new AeshConsoleBufferBuilder()
                            .shell(console.getShell())
                            .prompt(new Prompt(prompt, mask))
                            .create();
                    InputProcessor inputProcessor = new AeshInputProcessorBuilder()
                            .consoleBuffer(consoleBuffer)
                            .create();

                    consoleBuffer.displayPrompt();
                    String result = null;
                    try {
                        do {
                            result = inputProcessor.parseOperation(console.getConsoleCallback().getInput());
                        }

                        while(result == null );
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return result;
                }

                @Override
                public int getTerminalWidth() {
                    return console.getTerminalSize().getWidth();
                }

                @Override
                public int getTerminalHeight() {
                    return console.getTerminalSize().getHeight();
                }

                @Override

                public void setCallback(ConsoleCallback consoleCallback) {
                    if(console != null)
                        console.setConsoleCallback(consoleCallback);
                }

                @Override
                public void start() {
                    if(console != null)
                        console.start();
                }

                @Override
                public void stop() {
                    if(console != null)
                        console.stop();
                }

                @Override
                public boolean running() {
                    return console != null && console.isRunning();
                }

                @Override
                public void setPrompt(String prompt) {
                    console.setPrompt(new Prompt(prompt));
                }

                class HistoryImpl implements CommandHistory {

                @SuppressWarnings("unchecked")
                @Override
                public List<String> asList() {
                    return console.getHistory().getAll();
                }

                @Override
                public boolean isUseHistory() {
                    return !settings.isHistoryDisabled();
                }

                @Override
                public void setUseHistory(boolean useHistory) {
                    //not implemented
                }

                @Override
                public void clear() {
                    console.getHistory().clear();
                }

                @Override
                public void setMaxSize(int maxSize) {
                    //not implemented
                }

                @Override
                public int getMaxSize() {
                    return settings.getHistorySize();
                }
            }};
        }
    }
}
