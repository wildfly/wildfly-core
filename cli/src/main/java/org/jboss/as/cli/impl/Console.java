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
import org.jboss.aesh.console.ConsoleCallback;
import org.jboss.aesh.console.Prompt;
import org.jboss.aesh.console.settings.Settings;
import org.jboss.aesh.parser.Parser;
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

    void setCompletion(boolean complete);

    void clearScreen();

    void printColumns(Collection<String> list);

    void print(String line);

    void printNewLine();

    String readLine(String prompt);

    String readLine(String prompt, Character mask);

    int getTerminalWidth();

    int getTerminalHeight();

    /**
     * Checks whether the tab-completion is enabled.
     *
     * @return  true if tab-completion is enabled, false - otherwise
     */
    boolean isCompletionEnabled();

    /**
     * Enables or disables the tab-completion.
     *
     * @param completionEnabled  true will enable the tab-completion, false will disable it
     */
    // void setCompletionEnabled(boolean completionEnabled);

    /**
     * Interrupts blocking readLine method.
     *
     * Added as solution to BZ-1149099.
     */
    void interrupt();

    void controlled();

    boolean isControlled();

    void continuous();

    void setCallback(ConsoleCallback consoleCallback);

    void start();

    void stop();

    boolean running();

    void setPrompt(String prompt);

    void setPrompt(String prompt, Character mask);

    void redrawPrompt();

    static final class Factory {

        public static Console getConsole(CommandContext ctx, Settings settings) throws CliInitializationException {
            return getConsole(ctx, null, null, settings);
        }

        public static Console getConsole(final CommandContext ctx, InputStream is, OutputStream os, final Settings settings) throws CliInitializationException {

            org.jboss.aesh.console.Console aeshConsole = null;
            try {
                aeshConsole = new org.jboss.aesh.console.Console(settings);
            } catch (Throwable e) {
                throw new CliInitializationException("Failed to initialize Aesh console", e);
            }

            final org.jboss.aesh.console.Console finalAeshConsole = aeshConsole;

            Console console =  new Console() {

                private CommandContext cmdCtx = ctx;
                private org.jboss.aesh.console.Console console = finalAeshConsole;
                private CommandHistory history = new HistoryImpl();
                private boolean controlled;

                @Override
                public void addCompleter(final CommandLineCompleter completer) {
                    console.addCompletion(new Completion() {
                        @Override
                        public void complete(CompleteOperation co) {
                            List<String> candidates = new ArrayList<>();
                            int offset = completer.complete(cmdCtx,
                                    co.getBuffer(), co.getCursor(), candidates);
                            co.setOffset(offset);
                            co.setCompletionCandidates(candidates);
                            String buffer = cmdCtx.getArgumentsString() == null ? co.getBuffer() : ctx.getArgumentsString() + co.getBuffer();
                            if (co.getCompletionCandidates().size() == 1
                                    && co.getCompletionCandidates().get(0).getCharacters().startsWith(buffer))
                                co.doAppendSeparator(true);
                            else
                                co.doAppendSeparator(false);
                        }
                    });
                }

                @Override
                public boolean isUseHistory() {
                    return !settings.isHistoryDisabled();
                }

                @Override
                public CommandHistory getHistory() {
                    return history;
                }

                @Override
                public void setCompletion(boolean complete){
                    console.setCompletionEnabled(complete);
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
                            Parser.formatDisplayList(newList,
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
                    int PID = -1;

                    try {
                        ConsoleCallback callback = console.getConsoleCallback();
                        if (callback instanceof CommandContextImpl.CLIAeshConsoleCallback) {
                            CommandContextImpl.CLIAeshConsoleCallback cliCallback = ((CommandContextImpl.CLIAeshConsoleCallback) callback);

                            if (cliCallback.hasActiveProcess()) {
                                PID = cliCallback.getProcessPID();
                                console.putProcessInBackground(PID);
                            }

                        }
                        Prompt origPrompt = null;
                        if(!console.getPrompt().getPromptAsString().equals(prompt)) {
                            origPrompt = console.getPrompt();
                            console.setPrompt(new Prompt(prompt, mask));
                            redrawPrompt();
                        }
                        try {
                            return console.getInputLine();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            if(origPrompt != null) {
                                console.setPrompt(origPrompt);
                            }
                        }
                    } finally {
                        if( PID != -1) {
                            console.putProcessInForeground(PID);
                        }
                    }

                    // Something is wrong.
                    return null;
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
                public boolean isCompletionEnabled(){
                    return settings.isCompletionDisabled();
                }

                @Override
                public void interrupt() {
                }

                @Override
                public void controlled() {
                    console.controlled();
                    controlled = true;
                }

                @Override
                public void continuous() {
                    console.continuous();
                    controlled = false;
                }

                @Override
                public boolean isControlled() {
                    return controlled;
                }

                @Override
                public void setCallback(ConsoleCallback consoleCallback) {
                    if(console != null)
                        console.setConsoleCallback(consoleCallback);
                }

                @Override
                public void start(){
                    if(console != null)
                        console.start();
                }

                @Override
                public void stop() {
                    if(console != null) {
                        console.stop();
                    }
                }

                @Override
                public boolean running() {
                    return console != null && (console.isRunning() || console.hasRunningProcesses());
                }

                @Override
                public void setPrompt(String prompt){
                    setPrompt(prompt, null);
                }

                @Override
                public void setPrompt(String prompt, Character mask) {
                    if(!prompt.equals(console.getPrompt().getPromptAsString())) {
                        console.setPrompt(new Prompt(prompt, mask));
                    }
                }

                @Override
                public void redrawPrompt() {
                    console.clearBufferAndDisplayPrompt();
                }

                class HistoryImpl implements CommandHistory {

                    @Override
                    public List<String> asList() {
                        return console.getHistory().getAll();
                    }

                    @Override
                    public boolean isUseHistory() {
                        return console.getHistory().isEnabled();
                    }

                    @Override
                    public void setUseHistory(boolean useHistory) {
                        if(useHistory){
                            console.getHistory().enable();
                        }else{
                            console.getHistory().disable();
                        }
                    }

                    @Override
                    public void clear() {
                        console.getHistory().clear();
                    }

                    @Override
                    public int getMaxSize() {
                        return settings.getHistorySize();
                    }
                }
            };

            return console;
        }
    }

}
