/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.cli.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.alias.AliasCompletion;
import org.aesh.readline.alias.AliasManager;
import org.aesh.readline.alias.AliasPreProcessor;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.history.FileHistory;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.Attributes;
import org.aesh.utils.ANSI;
import org.aesh.utils.Config;
import org.aesh.readline.util.FileAccessPermission;
import org.aesh.readline.util.Parser;
import org.aesh.readline.completion.CompleteOperation;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.history.History;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.terminal.Key;
import org.aesh.readline.terminal.TerminalBuilder;
import org.aesh.readline.terminal.impl.WinSysTerminal;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.Util;
import org.jboss.logging.Logger;

/**
 * Integration point with aesh-readline. There are multiple paths when the CLI
 * exits.
 * <ul>
 * <li>quit command: Command ctx is terminated, console is closed, terminal
 * connection reading thread is interrupted, Main thread exit, jvm exit handlers
 * are called</li>
 * <li>Ctrl-C: can be received as an OS signal or parsed by aesh. In both cases
 * interruptHandler is called, the connection is closed, terminal connection
 * reading thread is interrupted, Main thread exit, jvm exit handlers are
 * called. If a prompt is in progress, the reading thread will be interrupted
 * too.</li>
 * <li>Ctrl-D (without characters typed): Only parsed by aesh, no native signal
 * raised. In both cases, the connection is closed by aesh, terminal connection
 * reading thread is interrupted, Main thread exit, jvm exit handlers are
 * called. If a prompt is in progress, the reading thread will be interrupted
 * too. Because Ctrl-D is not a native signal, it can't be handled during the
 * execution of a command. Only Ctrl-C can be used to interrupt the CLI.</li>
 * </ul>
 *
 * @author jdenise@redhat.com
 */
public class ReadlineConsole {

    private static final Logger LOG = Logger.getLogger(ReadlineConsole.class.getName());
    private static final boolean isTraceEnabled = LOG.isTraceEnabled();

    public interface Settings {

        /**
         * @return the inStream
         */
        InputStream getInStream();

        /**
         * @return the outStream
         */
        OutputStream getOutStream();

        /**
         * @return the disableHistory
         */
        boolean isDisableHistory();

        /**
         * @return the outputRedefined
         */
        boolean isOutputRedefined();

        /**
         * @return the historyFile
         */
        File getHistoryFile();

        /**
         * @return the historySize
         */
        int getHistorySize();

        /**
         * @return the permission
         */
        FileAccessPermission getPermission();

        /**
         * @return the interrupt
         */
        Runnable getInterrupt();
    }

    /**
     *
     * All chars printed by commands and prompts go through this class that log
     * any printed content. What is received by inputstream is echoed in the
     * outputstream.
     */
    private static class CLITerminalConnection extends TerminalConnection {

        private final Consumer<int[]> interceptor;
        private Thread connectionThread;

        CLITerminalConnection(Terminal terminal) {
            super(terminal);
            interceptor = (int[] ints) -> {
                if (isTraceEnabled) {
                    LOG.tracef("Writing %s",
                            Parser.stripAwayAnsiCodes(Parser.fromCodePoints(ints)));
                }
                CLITerminalConnection.super.stdoutHandler().accept(ints);
            };
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return interceptor;
        }

        /**
         * Required to close the connection reading on the terminal, otherwise
         * it can't be interrupted.
         *
         * @throws InterruptedException
         */
        public void openBlockingInterruptable()
                throws InterruptedException {
            // We need to thread this call in order to interrupt it (when Ctrl-C occurs).
            connectionThread = new Thread(() -> {
                // This thread can't be interrupted from another thread.
                // Will stay alive until System.exit is called.
                Thread thr = new Thread(() -> super.openBlocking(),
                        "CLI Terminal Connection (uninterruptable)");
                thr.start();
                try {
                    thr.join();
                } catch (InterruptedException ex) {
                    // XXX OK, interrupted, just leaving.
                }
            }, "CLI Terminal Connection (interruptable)");
            connectionThread.start();
            connectionThread.join();
        }

        @Override
        public void close() {
            super.close();
            if (connectionThread != null) {
                connectionThread.interrupt();
            }
        }
    }

    private static class SettingsImpl implements Settings {

        private final InputStream inStream;
        private final OutputStream outStream;
        private final boolean disableHistory;
        private final File historyFile;
        private final int historySize;
        private final FileAccessPermission permission;
        private final Runnable interrupt;
        private final boolean outputRedefined;

        private SettingsImpl(InputStream inStream,
                OutputStream outStream,
                boolean outputRedefined,
                boolean disableHistory,
                File historyFile,
                int historySize,
                FileAccessPermission permission,
                Runnable interrupt) {
            this.inStream = inStream;
            this.outStream = outStream;
            this.outputRedefined = outputRedefined;
            this.disableHistory = disableHistory;
            this.historyFile = historyFile;
            this.historySize = historySize;
            this.permission = permission;
            this.interrupt = interrupt;
        }

        /**
         * @return the inStream
         */
        @Override
        public InputStream getInStream() {
            return inStream;
        }

        /**
         * @return the outStream
         */
        @Override
        public OutputStream getOutStream() {
            return outStream;
        }

        /**
         * @return the disableHistory
         */
        @Override
        public boolean isDisableHistory() {
            return disableHistory;
        }

        /**
         * @return the outputRedefined
         */
        @Override
        public boolean isOutputRedefined() {
            return outputRedefined;
        }

        /**
         * @return the historyFile
         */
        @Override
        public File getHistoryFile() {
            return historyFile;
        }

        /**
         * @return the historySize
         */
        @Override
        public int getHistorySize() {
            return historySize;
        }

        /**
         * @return the permission
         */
        @Override
        public FileAccessPermission getPermission() {
            return permission;
        }

        /**
         * @return the interrupt
         */
        @Override
        public Runnable getInterrupt() {
            return interrupt;
        }
    }

    public static class SettingsBuilder {

        private InputStream inStream;
        private OutputStream outStream;
        private boolean disableHistory;
        private File historyFile;
        private int historySize;
        private FileAccessPermission permission;
        private Runnable interrupt;
        private boolean outputRedefined;

        public SettingsBuilder inputStream(InputStream inStream) {
            this.inStream = inStream;
            return this;
        }

        public SettingsBuilder outputStream(OutputStream outStream) {
            this.outStream = outStream;
            return this;
        }

        public SettingsBuilder disableHistory(boolean disableHistory) {
            this.disableHistory = disableHistory;
            return this;
        }

        public SettingsBuilder historyFile(File historyFile) {
            this.historyFile = historyFile;
            return this;
        }

        public SettingsBuilder historySize(int historySize) {
            this.historySize = historySize;
            return this;
        }

        public SettingsBuilder historyFilePermission(FileAccessPermission permission) {
            this.permission = permission;
            return this;
        }

        public SettingsBuilder interruptHook(Runnable interrupt) {
            this.interrupt = interrupt;
            return this;
        }

        public SettingsBuilder outputRedefined(boolean outputRedefined) {
            this.outputRedefined = outputRedefined;
            return this;
        }

        public Settings create() {
            return new SettingsImpl(inStream, outStream, outputRedefined,
                    disableHistory, historyFile, historySize, permission, interrupt);
        }
    }

    class HistoryImpl implements CommandHistory {

        @Override
        public List<String> asList() {
            List<String> lst = new ArrayList<>();
            for (int[] l : readlineHistory.getAll()) {
                lst.add(Parser.stripAwayAnsiCodes(Parser.fromCodePoints(l)));
            }
            return lst;
        }

        @Override
        public boolean isUseHistory() {
            return readlineHistory.isEnabled();
        }

        @Override
        public void setUseHistory(boolean useHistory) {
            if (useHistory) {
                readlineHistory.enable();
            } else {
                readlineHistory.disable();
            }

        }

        @Override
        public void clear() {
            readlineHistory.clear();
        }

        @Override
        public int getMaxSize() {
            return readlineHistory.size();
        }
    }

    private final List<Completion> completions = new ArrayList<>();
    private Readline readline;
    private CLITerminalConnection connection;
    private final CommandHistory history = new HistoryImpl();
    private final FileHistory readlineHistory;
    private Prompt prompt;
    private final Settings settings;
    private volatile boolean started;
    private volatile boolean closed;
    private Thread startThread;
    private Thread readingThread;
    private Consumer<String> callback;

    private final ExecutorService executor = Executors.newFixedThreadPool(1,
            (r) -> new Thread(r, "CLI command"));
    private StringBuilder outputCollector;

    private final AliasManager aliasManager;
    private final List<Function<String, Optional<String>>> preProcessors = new ArrayList<>();

    private static final EnumMap<ReadlineFlag, Integer> READLINE_FLAGS = new EnumMap<>(ReadlineFlag.class);

    static {
        READLINE_FLAGS.put(ReadlineFlag.NO_PROMPT_REDRAW_ON_INTR, Integer.MAX_VALUE);
    }

    private Consumer<Signal> interruptHandler;

    private boolean isSystemTerminal;

    private boolean forcePaging;

    private History searchHistory = new InMemoryHistory();
    private Paging paging;

    ReadlineConsole(Settings settings) throws IOException {
        this.settings = settings;
        readlineHistory = new FileHistory(settings.getHistoryFile(),
                settings.getHistorySize(), settings.getPermission(), false);
        if (settings.isDisableHistory()) {
            readlineHistory.disable();
        } else {
            readlineHistory.enable();
        }
        if (isTraceEnabled) {
            LOG.tracef("History is enabled? %s", !settings.isDisableHistory());
        }
        aliasManager = new AliasManager(new File(Config.getHomeDir()
                + Config.getPathSeparator() + ".aesh_aliases"), true);
        AliasPreProcessor aliasPreProcessor = new AliasPreProcessor(aliasManager);
        preProcessors.add(aliasPreProcessor);
        completions.add(new AliasCompletion(aliasManager));
        readline = new Readline();
    }

    private void initializeConnection() throws IOException {
        if (connection == null) {
            connection = newConnection();
            connection.setSizeHandler(new Consumer<Size>() {
                @Override
                public void accept(Size t) {
                    if (paging != null) {
                        paging.redraw(connection.getTerminal().getSize());
                    }
                }
            });
            interruptHandler = signal -> {
                if (signal == Signal.INT) {
                    LOG.trace("Calling InterruptHandler");
                    connection.write(Config.getLineSeparator());
                    connection.close();
                }
            };
            connection.setSignalHandler(interruptHandler);
            // Do not display ^C
            Attributes attr = connection.getAttributes();
            attr.setLocalFlag(Attributes.LocalFlag.ECHOCTL, false);
            connection.setAttributes(attr);
            /**
             * On some terminal (Mac terminal), when the terminal switches to
             * the original mode (the mode in place prior readline is called
             * with echo ON) when executing a command, then, if there are still
             * some characters to read in the buffer (eg: large copy/paste of
             * commands) these characters are displayed by the terminal. It has
             * been observed on some platforms (eg: Mac OS). By entering the raw
             * mode we are not impacted by this behavior. That is tracked by
             * AESH-463.
             */
            connection.enterRawMode();
        }
    }

    public void setActionCallback(Consumer<String> callback) {
        this.callback = callback;
    }

    private CLITerminalConnection newConnection() throws IOException {
        LOG.trace("Creating terminal connection");

        // The choice of the outputstream to use is ruled by the following rules:
        // - If the output is redefined in CLIPrintStream, the terminal use the CLIPrintStream
        // - If some redirection have been establised ( <, or remote process), CLIPrintStream is to be used.
        //   That is required to protect the CLI against embed-server I/O handling.
        // - Otherwise, a system terminal is used. system terminals don't use System.out
        //   so are protected against embed-server I/O handling.
        Terminal terminal = TerminalBuilder.builder()
                .input(settings.getInStream() == null
                        ? System.in : settings.getInStream())
                // Use CLI stream if not a system terminal, it protects against
                // embed-server I/O redefinition
                .output(settings.getOutStream())
                .nativeSignals(true)
                .name("CLI Terminal")
                // We ask for a system terminal only if the Output has not been redefined.
                // If the IO context is redirected ( <, or remote process usage),
                // then, whatever the output being redefined or not, the terminal
                // will be NOT a system terminal, that is the TerminalBuilder behavior.
                .system(!settings.isOutputRedefined())
                .build();
        if (isTraceEnabled) {
            LOG.tracef("New Terminal %s", terminal.getClass());
        }
        CLITerminalConnection c = new CLITerminalConnection(terminal);
        isSystemTerminal = c.supportsAnsi();

        return c;
    }

    /**
     * This has the side effect to create the internal readline instance.
     *
     * @param ch The Completion Handler.
     */
    public void setCompletionHandler(CompletionHandler<? extends CompleteOperation> ch) {
        readline = new Readline(EditModeBuilder.builder().create(), null, ch);
    }

    private Readline getReadLine() {
        if (readline == null) {
            readline = new Readline();
        }
        return readline;
    }

    public void addCompleter(Completion<? extends CompleteOperation> completer) {
        completions.add(completer);
    }

    public CommandHistory getHistory() {
        return history;
    }

    public void clearScreen() {
        if (connection != null) {
            connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
        }
    }

    public String formatColumns(Collection<String> list) {
        String[] newList = new String[list.size()];
        list.toArray(newList);
        return Parser.formatDisplayList(newList,
                getHeight(),
                getWidth());
    }

    public void print(String line, boolean collect) {
        LOG.tracef("Print %s", line);
        if (collect && outputCollector != null) {
            outputCollector.append(line);
        } else if (connection == null) {
            OutputStream out = settings.getOutStream() == null ? System.out : settings.getOutStream();
            try {
                out.write(line.getBytes());
            } catch (IOException ex) {
                LOG.tracef("Print exception %s", ex);
            }
        } else {
            connection.write(line);
        }
    }

    public Key readKey() throws InterruptedException, IOException {
        return Key.findStartKey(read());
    }

    private class Paging {

        private boolean notFound;
        private boolean searchingMode;
        private int currentLine;
        private int allLines;
        private int lastScrolledLines;
        private List<String> lines;
        private final String[] splitLines;
        private int jumpIndex = -1;
        private String pattern;
        private int max;
        private boolean paging;

        // Starting Windows 10, alternate buffer is supported.
        private final boolean alternateSupported;

        Paging(String output, Size termSize) {
            // '\R' will match any line break.
            // -1 to keep empty lines at the end of content.
            splitLines = output.split("\\R", -1);
            lines = buildLines(termSize);
            lastScrolledLines = lines.size();
            max = termSize.getHeight() - 1;
            if (Util.isWindows()) {
                // forcePaging is used by tests.
                alternateSupported = WinSysTerminal.isVTSupported() || forcePaging;
            } else {
                alternateSupported = true;
            }
            if (lines.size() > max) {
                if (alternateSupported) {
                    connection.write(ANSI.ALTERNATE_BUFFER);
                    clearScreen();
                }
                paging = true;
            }
        }

        private List<String> buildLines(Size size) {
            List<String> lst = new ArrayList<>();
            int width = Util.isWindows() ? size.getWidth() - 1 : size.getWidth();
            for (String l : splitLines) {
                String remaining = l;
                do {
                    String st = remaining.substring(0, Math.min(remaining.length(), width));
                    lst.add(st);
                    remaining = remaining.substring(Math.min(remaining.length(), width));
                } while (!remaining.isEmpty());
            }
            return lst;
        }

        int getMax() {
            return max;
        }

        void pagingDone() {
            if (paging && alternateSupported) {
                //Print the output to main buffer (from start until the last scrolled position)
                connection.write(ANSI.MAIN_BUFFER);
                printScrolledLines();
            }
        }

        boolean needPrompt() {
            return (currentLine > getMax() - 1 && jumpIndex == -1) || (endBuffer() && searchingMode);
        }

        boolean inWorkflow() {
            return allLines < lines.size() || searchingMode;
        }

        void exit() {
            lastScrolledLines = allLines;
            allLines = lines.size();
            searchingMode = false;
        }

        void pageDown() {
            notFound = false;
            currentLine = 0;
            // Exit the workflow.
            if (endBuffer()) {
                exit();
            }
        }

        void pageUp() {
            if (!alternateSupported) {
                return;
            }
            clearScreen();
            notFound = false;
            currentLine = 0;
            if (allLines > 2 * getMax()) {
                //Move one screen up
                allLines -= 2 * getMax();
            } else {
                //Move to the start of input
                allLines = 0;
            }
        }

        void previousMatch() {
            if (!alternateSupported) {
                return;
            }
            if (!searchingMode && searchHistory.size() != 0) {
                int[] p = searchHistory.get(searchHistory.size() - 1);
                pattern = Parser.fromCodePoints(p);
                searchingMode = true;
            }
            if (searchingMode) {
                if (allLines <= getMax()) {
                    notFound = true;
                }
                int previous = previousMatch(pattern, lines, allLines - getMax() - 1);
                if (previous >= 0) {
                    jumpIndex = allLines - previous - 1;
                    notFound = false;
                    resetScreen();
                } else {
                    notFound = true;
                }
            }
        }

        private int previousMatch(String pattern, List<String> lines, int currentLine) {
            int previous = 0;
            for (int i = currentLine; i >= 0; i--) {
                String l = lines.get(i);
                if (l.contains(pattern)) {
                    return previous;
                }
                previous += 1;
            }
            return -1;
        }

        private void nextMatch() {
            if (!alternateSupported) {
                return;
            }
            if (searchingMode) {
                if (endBuffer()) {
                    notFound = true;
                } else {
                    int start = allLines - getMax() < 0 ? 0 : allLines - getMax();
                    int next = nextMatch(pattern, lines, start + 1);
                    if (next >= 0) {
                        // We need to redraw everything from start in case
                        // some matches are already displayed and need highlighting
                        jumpIndex = Math.min(allLines + next + 1, lines.size());
                        notFound = false;
                        resetScreen();
                    } else {
                        notFound = true;
                    }
                }
            } else if (searchHistory.size() != 0) {
                int[] p = searchHistory.get(searchHistory.size() - 1);
                doSearch(Parser.fromCodePoints(p));
            }
        }

        private int nextMatch(String pattern, List<String> lines, int currentLine) {
            int next = 0;
            for (int i = currentLine; i < lines.size(); i++) {
                String l = lines.get(i);
                if (l.contains(pattern)) {
                    return next;
                }
                next += 1;
            }
            return -1;
        }

        private void search() throws InterruptedException, IOException {
            if (!alternateSupported) {
                return;
            }
            doSearch(readPattern());
        }

        private void doSearch(String pattern) {
            // The complete buffer needs to be redrawn to clear the pattern prompt
            // and to highlight possible matches already displayed.
            if (pattern == null || pattern.isEmpty()) {
                // needed to redraw in order to clear pattern prompt.
                jumpIndex = allLines;
            } else {
                this.pattern = pattern;
                int start = allLines - getMax() < 0 ? 0 : allLines - getMax();
                int next = nextMatch(pattern, lines, start);
                if (next >= 0) {
                    jumpIndex = Math.min(allLines + next, lines.size());
                    searchingMode = true;
                    notFound = false;
                } else {
                    notFound = true;
                    // do we have something from the beginning
                    int n = nextMatch(pattern, lines, 0);
                    if (n >= 0) {
                        searchingMode = true;
                    }
                    // needed to redraw in order to clear pattern prompt.
                    jumpIndex = allLines;
                }
            }
            resetScreen();
        }

        private void lineUp() {
            if (!alternateSupported) {
                return;
            }
            notFound = false;
            if (allLines > getMax()) {
                currentLine = 0;
                //Move one line up
                allLines -= getMax() + 1;
                clearScreen();
            }
        }

        private void lineDown() {
            notFound = false;
            // Exit the workflow
            if (endBuffer()) {
                exit();
            }
            currentLine -= 1;
        }

        private int getPercentage() {
            return (allLines * 100) / lines.size();
        }

        private String nextCurrentLine() {
            String line = lines.get(allLines);
            currentLine += 1;
            allLines += 1;
            if (jumpIndex == allLines) {
                currentLine = getMax();
                jumpIndex = -1;
            }
            return line;
        }

        private boolean endBuffer() {
            return allLines == lines.size();
        }

        private void redraw(Size size) {
            if (!alternateSupported) {
                return;
            }
            int oldMax = max;
            max = size.getHeight() - 1;
            lines = buildLines(size);
            if (lines.size() > max) {
                jumpIndex = allLines + (max - oldMax);
            } else {
                jumpIndex = -1;
            }
            resetScreen();
            while (inWorkflow() && !needPrompt()) {
                printCurrentLine();
            }
            // Redraw the prompt in all cases, even if resizing expose everything.
            drawPrompt();
        }

        private void printCurrentLine() {
            String l = nextCurrentLine();
            if (searchingMode) {
                displayHightlighted(pattern, l);
            } else {
                connection.write(l + Config.getLineSeparator());
            }
        }

        private void printScrolledLines() {
            //Print the output to main buffer (from start until the last scrolled position)
            for (int i = 0; i < lastScrolledLines; i++) {
                String l = lines.get(i);
                connection.write(l + Config.getLineSeparator());
            }
        }

        private void drawPrompt() {
            if (notFound) {
                connection.write(ANSI.INVERT_BACKGROUND);
                connection.write("Pattern not found");
                connection.write(ANSI.RESET);
            } else {
                connection.write("--More(" + getPercentage() + "%)--");
            }
        }

        private void goHome() {
            if (!alternateSupported) {
                return;
            }
            notFound = false;
            resetScreen();
        }

        private void goEnd() {
            notFound = false;
            // Jump to the size - 1 line to not exit
            // the paging.
            if (allLines < lines.size() - 1) {
                jumpIndex = lines.size() - 1;
            }
        }

        private void resetScreen() {
            currentLine = 0;
            allLines = 0;
            clearScreen();
        }
    }

    // handle "a la" 'more' scrolling
    private void printCollectedOutput() {
        if (outputCollector == null || outputCollector.length() == 0) {
            return;
        }
        try {
            String line = outputCollector.toString();
            if (line.isEmpty()) {
                return;
            }
            paging = new Paging(line, connection.size());
            while (paging.inWorkflow()) {
                if (paging.needPrompt()) {
                    try {
                        connection.write(ANSI.CURSOR_SAVE);
                        paging.drawPrompt();
                        Key k = readKey();
                        connection.write(ANSI.CURSOR_RESTORE);
                        connection.stdoutHandler().accept(ANSI.ERASE_LINE_FROM_CURSOR);
                        if (k == null) { // interrupted, exit.
                            paging.exit();
                        } else {
                            switch (k) {
                                case SPACE:
                                case PGDOWN_2:
                                case PGDOWN: {
                                    paging.pageDown();
                                    break;
                                }
                                case BACKSLASH:
                                case PGUP_2:
                                case PGUP: {
                                    paging.pageUp();
                                    break;
                                }
                                case N: {
                                    paging.previousMatch();
                                    break;
                                }
                                case n: {
                                    paging.nextMatch();
                                    break;
                                }
                                case SLASH: {
                                    paging.search();
                                    break;
                                }
                                case SEMI_COLON:
                                case UP_2:
                                case UP: {
                                    paging.lineUp();
                                    break;
                                }
                                case DOWN:
                                case DOWN_2:
                                case ENTER:
                                case CTRL_M: { // On Mac, CTRL_M...
                                    paging.lineDown();
                                    break;
                                }
                                case HOME:
                                case HOME_2:
                                case g: {
                                    paging.goHome();
                                    break;
                                }
                                case END:
                                case END_2:
                                case END_3:
                                case G: {
                                    paging.goEnd();
                                    break;
                                }
                                case Q:
                                case ESC:
                                case q: {
                                    paging.exit();
                                    break;
                                }
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    paging.printCurrentLine();
                }
            }
        } finally {
            paging.pagingDone();
            paging = null;
            outputCollector = null;
        }
    }

    private void displayHightlighted(String pattern, String l) {
        int index = l.indexOf(pattern);
        while (index >= 0) {
            connection.write(l.substring(0, index));
            connection.write(ANSI.INVERT_BACKGROUND);
            connection.write(pattern);
            connection.write(ANSI.RESET);
            l = l.substring(index + pattern.length());
            index = l.indexOf(pattern);
        }
        connection.write(l + Config.getLineSeparator());
    }

    public int[] read() throws InterruptedException, IOException {
        initializeConnection();
        ActionDecoder decoder = new ActionDecoder();
        final int[][] key = {null};
        // Keep a reference on the caller thread in case Ctrl-C is pressed
        // and thread needs to be interrupted.
        readingThread = Thread.currentThread();
        // We need to set the interrupt SignalHandler to interrupt the CLI.
        Consumer<Signal> prevHandler = connection.getSignalHandler();
        connection.setSignalHandler(interruptHandler);
        CountDownLatch latch = new CountDownLatch(1);
        Attributes attributes = connection.enterRawMode();
        connection.setStdinHandler(keys -> {
            decoder.add(keys);
            if (decoder.hasNext()) {
                key[0] = decoder.next().buffer().array();
                /*
                    Synchronously put the TerminalConnection thread in wait (side effect of setting a null handler).
                    This does guarantee that when this thread unstack it will put itself
                    in wait mode. That is safe, wait is the state expected once this handler is done.
                    When the latch.await returns, or the current command
                    terminates and a new readline call will position a new stdinhandler
                    or read() is called again and a new stdinhandler will be set. In
                    both cases the terminalConnection will wake up and read on the terminal.
                    If we were not doing this call here but after latch.await we could
                    have this thread to be already reading on the terminal when
                    attempted to be put in wait mode (by the latch.await thread).
                    Theoreticaly the console could read a key although no inputHandler has been already set.
                    This has never been observed. But this can cause an issue on Cygwin (WFCORE-3647),
                    if the connection is reading although Terminal attributes are
                    get/set (by forking a sub process) by the next call to read(),
                    the get/set call is stuck when reading sub
                    process output and is unblocked when a key is typed in the terminal.
                    So one key is swallowed and the key must be typed twice to operate.
                */
                connection.setStdinHandler(null);
                latch.countDown();
            }
        });
        try {
            // Wait until interrupted
            latch.await();
        } finally {
            connection.setSignalHandler(prevHandler);
            connection.setAttributes(attributes);
            readingThread = null;
        }
        return key[0];
    }

    public void printNewLine(boolean collect) {
        print(Config.getLineSeparator(), collect);
    }

    public String readLine(String prompt) throws IOException, InterruptedException {
        return readLine(prompt, (Character) null);
    }

    /**
     * Prompt a user. The complexity of this method is implied by the Ctrl-C
     * handling. When Ctrl-C occurs, the Exit hook will call this.close that
     * interrupts the thread calling this operation.<p>
     * We have 2 cases.
     * <p>
     * 1) prompting prior to have started the console: - Must start a new
     * connection. - Make it non blocking. - Wait on latch to resync and to
     * catch Thread.interrupt.
     * <p>
     * 2) prompting after to have started the console: - No need to open the
     * connection. - Wait on latch to resync and to catch Thread.interrupt.
     *
     * @param prompt
     * @param mask
     * @return
     * @throws IOException
     */
    public String readLine(String prompt, Character mask) throws InterruptedException, IOException {
        logPromptMask(prompt, mask);
        return readLine(new Prompt(prompt, mask));
    }

    public String readLine(Prompt prompt) throws InterruptedException, IOException {
        return readLine(prompt, null);
    }

    public String readLine(Prompt prompt, Completion completer) throws InterruptedException, IOException {
        if (started) {
            // If there are some output collected, flush it.
            printCollectedOutput();
            // New collector
            outputCollector = createCollector();
        }

        // Keep a reference on the caller thread in case Ctrl-C is pressed
        // and thread needs to be interrupted.
        readingThread = Thread.currentThread();
        try {
            if (!started) {
                // That is the case of the CLI connecting prior to start the terminal.
                // No Terminal waiting in Main thread yet.
                // We are opening the connection to the terminal until we have read
                // something from prompt.
                return promptFromNonStartedConsole(prompt, completer);
            } else {
                return promptFromStartedConsole(prompt, completer, null);
            }
        } finally {
            readingThread = null;
        }
    }

    private String readPattern() throws InterruptedException, IOException {
        // Keep a reference on the caller thread in case Ctrl-C is pressed
        // and thread needs to be interrupted.
        readingThread = Thread.currentThread();
        try {
            return promptFromStartedConsole(new Prompt("/", (Character) null), null, searchHistory);
        } finally {
            readingThread = null;
        }
    }

    private StringBuilder createCollector() {
        if (!isPagingOutputEnabled()) {
            return null;
        }
        return new StringBuilder();
    }

    private String promptFromNonStartedConsole(Prompt prompt, Completion completer) throws InterruptedException, IOException {
        initializeConnection();
        LOG.trace("Not started");
        String[] out = new String[1];
        if (connection.suspended()) {
            connection.awake();
        }
        List<Completion> lst = null;
        if (completer != null) {
            lst = new ArrayList<>();
            lst.add(completer);
        }
        getReadLine().readline(connection, prompt, newLine -> {
            out[0] = newLine;
            LOG.trace("Got some input");

            // We must call stopReading to stop reading from terminal
            // and release this thread.
            connection.stopReading();
        }, lst, null, null, null, READLINE_FLAGS);
        connection.openBlockingInterruptable();
        LOG.trace("Done for prompt");
        return out[0];
    }

    private String promptFromStartedConsole(Prompt prompt, Completion completer, History history) throws InterruptedException, IOException {
        initializeConnection();
        String[] out = new String[1];
        // We must be called from another Thread. connection is reading in Main thread.
        // calling readline will wakeup the Main thread that will execute
        // the Prompt handling.
        // We can safely wait.
        if (readingThread == startThread) {
            throw new RuntimeException("Can't prompt from the Thread that is "
                    + "reading terminal input");
        }
        List<Completion> lst = null;
        if (completer != null) {
            lst = new ArrayList<>();
            lst.add(completer);
        }
        CountDownLatch latch = new CountDownLatch(1);
        // We need to set the interrupt SignalHandler to interrupt the current thread.
        Consumer<Signal> prevHandler = connection.getSignalHandler();
        connection.setSignalHandler(interruptHandler);
        readline.readline(connection, prompt, newLine -> {
            out[0] = newLine;
            LOG.trace("Got some input");
            latch.countDown();
        }, lst, null, history, null, READLINE_FLAGS);
        try {
            latch.await();
        } finally {
            connection.setSignalHandler(prevHandler);
        }
        LOG.trace("Done for prompt");
        return out[0];
    }

    private void logPromptMask(String prompt, Character mask) {
        LOG.tracef("Prompt %s mask %s", prompt, mask);
    }

    public int getTerminalWidth() {
        return getWidth();
    }

    public int getTerminalHeight() {
        return getHeight();
    }

    private int getHeight() {
        if (connection == null) {
            return 40;
        }
        return connection.size().getHeight();
    }

    private int getWidth() {
        if (connection == null) {
            return 80;
        }
        return connection.size().getWidth();
    }

    public void start() throws IOException {
        if (closed) {
            throw new IllegalStateException("Console has already been closed");
        }
        if (!started) {
            initializeConnection();
            startThread = Thread.currentThread();
            started = true;
            loop();
            LOG.tracef("Started in thread %s. Waiting...",
                    startThread.getName());

            try {
                connection.openBlockingInterruptable();
            } catch (InterruptedException ex) {
                // OK leaving
            }
            LOG.trace("Leaving console");
        } else {
            LOG.trace("Already started");
        }
    }

    private void loop() {
        try {
            if (isTraceEnabled) {
                LOG.tracef("Set a readline callback with prompt %s", prompt);
            }
            // Console could have been closed during a command execution.
            if (!closed) {
                getReadLine().readline(connection, prompt, line -> {
                    // All commands can lead to prompting the user. So require
                    // to be executed in a dedicated thread.
                    // This can happen if a security configuration occurs
                    // on the remote server.
                    LOG.tracef("Executing command %s in a new thread.", line);
                    if (line == null || line.trim().length() == 0 || handleAlias(line)) {
                        loop();
                        return;
                    }
                    executor.submit(() -> {
                        Consumer<Signal> handler = connection.getSignalHandler();
                        Thread callingThread = Thread.currentThread();
                        connection.setSignalHandler((signal) -> {
                            // Interrupting the current command thread.
                            switch (signal) {
                                case INT: {
                                    LOG.tracef("Interrupting command: %s", line);
                                    callingThread.interrupt();
                                }
                            }
                        });
                        try {
                            outputCollector = createCollector();
                            callback.accept(line);
                        } catch (Throwable thr) {
                            connection.write("Unexpected exception");
                            thr.printStackTrace();
                        } finally {
                            printCollectedOutput();
                            // The current thread could have been interrupted.
                            // Clear the flag to safely interact with aesh-readline
                            Thread.interrupted();
                            connection.setSignalHandler(handler);
                            LOG.tracef("Done Executing command %s", line);
                            loop();
                        }
                    });
                }, completions, preProcessors, readlineHistory, null, READLINE_FLAGS);
            }
        } catch (Exception ex) {
            connection.write("Unexpected exception");
            ex.printStackTrace();
        }
    }

    private boolean handleAlias(String line) {
        if (line.startsWith("alias ") || line.equals("alias")) {
            String out = aliasManager.parseAlias(line.trim());
            if (out != null) {
                print(out, false);
            }
            return true;
        } else if (line.startsWith("unalias ") || line.equals("unalias")) {
            String out = aliasManager.removeAlias(line.trim());
            if (out != null) {
                print(out, false);
            }
            return true;
        }
        return false;
    }

    public void stop() {
        if (!closed) {
            LOG.trace("Stopping.");

            closed = true;
            if (readingThread != null) {
                LOG.trace("Interrupting reading thread");
                readingThread.interrupt();
            }
            if (started) {
                readlineHistory.stop();
                aliasManager.persist();
            }
            executor.shutdown();
            if (connection != null) {
                connection.close();
            }
        }
    }

    public boolean running() {
        return started;
    }

    public void setPrompt(String prompt) {
        if (prompt.contains("\u001B[")) {
            this.prompt = new Prompt(Parser.stripAwayAnsiCodes(prompt), prompt);
        } else {
            this.prompt = new Prompt(prompt);
        }
    }

    public void setPrompt(Prompt prompt) {
        this.prompt = prompt;
    }

    /**
     * Public for testing purpose only.
     *
     * @return
     */
    public boolean isPagingOutputEnabled() {
        if (forcePaging) {
            return true;
        }
        return isSystemTerminal;
    }

    public void forcePagingOutput(boolean forcePaging) {
        this.forcePaging = forcePaging;
    }

    public Prompt getPrompt() {
        return prompt;
    }

    public Connection getConnection() {
        return connection;
    }

    public String handleBuiltins(String line) {
        if (handleAlias(line)) {
            return null;
        }
        return parse(line);
    }

    private String parse(String line) {
        Optional<String> out = aliasManager.getAliasName(line);
        if (out.isPresent()) {
            line = out.get();
        }
        return line;
    }
}
