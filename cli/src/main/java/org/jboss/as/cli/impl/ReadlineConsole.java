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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import org.aesh.readline.PagingSupport;
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.alias.AliasCompletion;
import org.aesh.readline.alias.AliasManager;
import org.aesh.readline.alias.AliasPreProcessor;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.history.FileHistory;
import org.aesh.readline.terminal.formatting.Color;
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
import org.aesh.readline.terminal.Key;
import org.aesh.readline.terminal.TerminalBuilder;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Signal;
import org.jboss.as.cli.CommandHistory;
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

    private static final class CharacterMatcher {

        private static final List<Character> endSeparators = Arrays.asList('}', ']', ')');
        private static final List<Character> startSeparators = Arrays.asList('{', '[', '(');
        private static final Map<Character, Character> endStartSeparators = new HashMap<>();
        private static final Map<Character, Character> startEndSeparators = new HashMap<>();
        private final Settings settings;

        static {
            endStartSeparators.put('}', '{');
            endStartSeparators.put(']', '[');
            endStartSeparators.put(')', '(');
            startEndSeparators.put('{', '}');
            startEndSeparators.put('[', ']');
            startEndSeparators.put('(', ')');
        }

        private int lastIndex = -1;
        private final List<Integer> openPositions = new ArrayList<>();
        private Line lastLine;

        private CharacterMatcher(Settings settings) {
            this.settings = settings;
        }

        void clear() {
            if (lastLine != null) {
                if (lastIndex >= 0) {
                    Line.CursorTransactionBuilder builder = lastLine.newCursorTransactionBuilder();
                    builder.colorize(lastIndex, Color.DEFAULT, Color.DEFAULT, false);
                    builder.build().run();
                }

                if (openPositions.size() > 0) {
                    for (int position : openPositions) {
                        Line.CursorTransactionBuilder builder = lastLine.newCursorTransactionBuilder();
                        builder.colorize(position, Color.DEFAULT, Color.DEFAULT, false);
                        builder.build().run();
                    }
                    openPositions.clear();
                }
            }

        }

        void clear(int position) {
            if (lastLine != null) {
                Line.CursorTransactionBuilder builder = lastLine.newCursorTransactionBuilder();
                builder.colorize(position, Color.DEFAULT, Color.DEFAULT, false);
                builder.build().run();
            }
        }

        void match(Line line) {
            lastLine = line;
            clear();

            char separator = (char) line.getCharacterAtCursor();

            if (endSeparators.contains(separator)) {
                String l = line.getLineToCursor();
                char startChar = endStartSeparators.get(separator);
                int index = findStart(l, startChar, separator);
                if (index == -1) {
                    return;
                }

                scan(line, index, line.getCurrentCharacterIndex());
                lastIndex = colorize(line, index);
            } else if (startSeparators.contains(separator)) {
                String l = line.getLineFromCursor();
                char endChar = startEndSeparators.get(separator);
                int index = findEnd(l, line.toString().length(), separator, endChar);
                if (index == -1) {
                    return;
                }

                scan(line, line.getCurrentCharacterIndex(), index);
                lastIndex = colorize(line, index);
            } else {
                lastIndex = -1;
            }
        }

        void scan(Line line, int start, int end) {
            List<AbstractMap.SimpleEntry<Character,Integer>> brackets = new ArrayList<>();

            // Collect the position of all brackets in the line
            for (int i = start; i <= end; i++) {
                char currentChar = line.getCharacterAtPosition(i);
                if (endSeparators.contains(currentChar) || startSeparators.contains(currentChar)) {
                    brackets.add(new AbstractMap.SimpleEntry<>(currentChar, i));
                }
            }

            // If there's only one bracket in the line, mark it and return
            if (brackets.size() == 1) {
                AbstractMap.SimpleEntry<Character, Integer> solo = brackets.get(0);
                if (endStartSeparators.containsKey(solo.getKey()) || startEndSeparators.containsKey(solo.getKey())) {
                    colorizeOpenPair(line, solo.getValue());
                    return;
                }
            }

            Set<AbstractMap.SimpleEntry<Character, Integer>> closedBrackets = new LinkedHashSet<>();
            Set<AbstractMap.SimpleEntry<Character, Integer>> openBrackets = new LinkedHashSet<>();


            for (int i = 0; i < brackets.size(); i++) {
                AbstractMap.SimpleEntry<Character, Integer> current = brackets.get(i);


                // Go through all the brackets and figure out if they are close or open
                for (int j = i + 1; j < brackets.size(); j++) {
                    AbstractMap.SimpleEntry<Character, Integer> next = brackets.get(j);
                    if (endStartSeparators.containsKey(current.getKey())) {
                        if (next.getKey() != endStartSeparators.get(current.getKey())) {
                            if (j == brackets.size() - 1) openBrackets.add(current);
                            openBrackets.add(next);
                        } else {
                            closedBrackets.add(current);
                            closedBrackets.add(next);
                        }
                    } else if (startEndSeparators.containsKey(current.getKey())) {
                        if (next.getKey() != startEndSeparators.get(current.getKey())) {
                            if (j == brackets.size() - 1) openBrackets.add(current);
                            openBrackets.add(next);
                        } else {
                            closedBrackets.add(current);
                            closedBrackets.add(next);
                        }
                    }
                }
            }

            // Remove brackets that are closed so we can clear them if needed
            openBrackets.removeAll(closedBrackets);
            for (AbstractMap.SimpleEntry<Character, Integer> closed : closedBrackets) {
                clear(closed.getValue());
            }

            // Mark open brackets
            for (AbstractMap.SimpleEntry<Character, Integer> open : openBrackets) {
                colorizeOpenPair(line, open.getValue());
                openPositions.add(open.getValue());
            }
        }

        private int findStart(String l, char start, char end) {
            int endCount = 0;
            char[] chars = l.toCharArray();

            for (int i = chars.length - 1; i >= 0; i--) {
                char c = chars[i];
                if (c == start) {
                    if (endCount == 0) {
                        return i;
                    } else {
                        endCount--;
                    }
                }

                if (c == end) {
                    endCount += 1;
                }
            }

            return -1;
        }

        private int findEnd(String l, int length, char start, char end) {
            if (l.length() <= 1) {
                return -1;
            }

            int endCount = 0;
            char[] chars = l.toCharArray();
            int pos = length - chars.length;

            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (c == end) {
                    if (endCount == 1) {
                        return pos + i;
                    } else {
                        endCount--;
                    }
                }

                if (c == start) {
                    endCount += 1;
                }
            }

            return -1;
        }

        private int colorize(Line line, int index) {
            Line.CursorTransactionBuilder builder = line.newCursorTransactionBuilder();
            if (settings.isColorOutput()) {
                builder.colorize(index, Color.DEFAULT, Color.GREEN, true);
            } else {
                builder.colorize(index, Color.BLACK, Color.WHITE, true);
            }
            builder.build().run();
            return index;
        }

        private void colorizeOpenPair(Line line, int index) {
            Line.CursorTransactionBuilder builder = line.newCursorTransactionBuilder();
            if (settings.isColorOutput()) {
                builder.colorize(index, Color.DEFAULT, Color.RED, true);
            } else {
                builder.colorize(index, Color.BLACK, Color.WHITE, true);
            }
            builder.build().run();
        }
    }

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

        /**
         * @return if console is configured for colour output
         */
        boolean isColorOutput();

        /**
         * @return the outputPaging
         */
        boolean isOutputPaging();

        /**
         * @return if user wants to disable character highlight
         */
        boolean ischaracterHighlight();
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
        private final ReadlineConsole console;

        CLITerminalConnection(Terminal terminal, ReadlineConsole console) {
            super(terminal);
            interceptor = (int[] ints) -> {
                if (isTraceEnabled) {
                    LOG.tracef("Writing %s",
                            Parser.stripAwayAnsiCodes(Parser.fromCodePoints(ints)));
                }
                CLITerminalConnection.super.stdoutHandler().accept(ints);
            };
            this.console = console;
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return interceptor;
        }

        @Override
        public void setAttributes(Attributes attr) {
            // Console already closed with Ctrl-c,
            // aesh AeshInputProcessor.finish would reset the attributes
            // to original ones, in our case we are in raw (AESH-463) so
            // ignore these attributes. During connection.close the original
            // attributes have been set back by the connection.
            if (!console.closed) {
                super.setAttributes(attr);
            }
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
        private final boolean colorOutput;
        private final boolean outputPaging;
        private final boolean characterHighlight;

        private SettingsImpl(InputStream inStream,
                OutputStream outStream,
                boolean outputRedefined,
                boolean disableHistory,
                File historyFile,
                int historySize,
                FileAccessPermission permission,
                Runnable interrupt,
                boolean colorOutput,
                boolean outputPaging,
                boolean characterHighlight) {
            this.inStream = inStream;
            this.outStream = outStream;
            this.outputRedefined = outputRedefined;
            this.disableHistory = disableHistory;
            this.historyFile = historyFile;
            this.historySize = historySize;
            this.permission = permission;
            this.interrupt = interrupt;
            this.colorOutput = colorOutput;
            this.outputPaging = outputPaging;
            this.characterHighlight = characterHighlight;
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

        /**
         * @return if console is configured for colour output
         */
        @Override
        public boolean isColorOutput() {
            return colorOutput;
        }


        /**
         * @return the outputPaging
         */
        @Override
        public boolean isOutputPaging() {
            return outputPaging;
        }
        /**
         * @return if user wants to disable character highlight
         */
        public boolean ischaracterHighlight() {
            return characterHighlight;
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
        private boolean colourOutput;
        private boolean outputPaging;
        private boolean characterHighlight;

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

        public SettingsBuilder colorOutput(boolean colourOutput) {
            this.colourOutput = colourOutput;
            return this;
        }

        public SettingsBuilder outputPaging(boolean outputPaging) {
            this.outputPaging = outputPaging;
            return this;
        }

        public SettingsBuilder characterHighlight(boolean characterHighlight) {
            this.characterHighlight = characterHighlight;
            return this;
        }

        public Settings create() {
            return new SettingsImpl(inStream, outStream, outputRedefined,
                    disableHistory, historyFile, historySize, permission, interrupt, colourOutput, outputPaging, characterHighlight);
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

    private final AliasManager aliasManager;
    private final List<Function<String, Optional<String>>> preProcessors = new ArrayList<>();

    private static final EnumMap<ReadlineFlag, Integer> READLINE_FLAGS = new EnumMap<>(ReadlineFlag.class);

    static {
        READLINE_FLAGS.put(ReadlineFlag.NO_PROMPT_REDRAW_ON_INTR, Integer.MAX_VALUE);
    }

    private Consumer<Signal> interruptHandler;

    private boolean isSystemTerminal;

    private boolean forcePaging;
    private PagingSupport pagingSupport;

    private final CharacterMatcher matcher;
    private CursorListener cursorListener = new CursorListener() {
        @Override
        public void moved(Line line) {
            matcher.match(line);
        }
    };

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
        matcher = new CharacterMatcher(settings);
        if (!settings.ischaracterHighlight()) {
            cursorListener = null;
        }
    }

    private void initializeConnection() throws IOException {
        if (connection == null) {
            connection = newConnection();
            pagingSupport = new PagingSupport(connection, readline, true);
            interruptHandler = signal -> {
                if (signal == Signal.INT) {
                    LOG.trace("Calling InterruptHandler");
                    matcher.clear();
                    connection.write(Config.getLineSeparator());
                    // Put the console in closed state. No more readline
                    // input handler can be set when closed.
                    stop();
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
        CLITerminalConnection c = new CLITerminalConnection(terminal, this);
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
        if (collect && isPagingOutputEnabled() && pagingSupport != null) {
            pagingSupport.addContent(line);
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

    // handle "a la" 'more' scrolling
    private void printCollectedOutput() {
        if (!isPagingOutputEnabled()) {
            return;
        }
        pagingSupport.printCollectedOutput();
    }

    public int[] read() throws InterruptedException, IOException {
        initializeConnection();
        ActionDecoder decoder = new ActionDecoder();
        final int[][] key = {null};
        // Keep a reference on the caller thread in case Ctrl-C is pressed
        // and thread needs to be interrupted.
        readingThread = Thread.currentThread();
        // We need to set the interrupt SignalHandler to interrupt the reading.
        Consumer<Signal> prevHandler = connection.getSignalHandler();
        connection.setSignalHandler((signal) -> {
            // Interrupting the current reading thread.
            switch (signal) {
                case INT: {
                    LOG.tracef("Interrupting reading thread.");
                    readingThread.interrupt();
                }
            }
        });
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
            pagingSupport.reset();
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
                            pagingSupport.reset();
                            callback.accept(line);
                        } catch (Throwable thr) {
                            connection.write("Unexpected exception");
                            thr.printStackTrace();
                        } finally {
                            try{
                                //This can throw an Exception and that would
                                //cause the cli to hang, therefore it is enclosed in try+finally
                                printCollectedOutput();
                            } finally {
                                // The current thread could have been interrupted.
                                // Clear the flag to safely interact with aesh-readline
                                Thread.interrupted();
                                connection.setSignalHandler(handler);
                                LOG.tracef("Done Executing command %s", line);
                                loop();
                            }
                        }
                    });
                }, completions, preProcessors, readlineHistory, cursorListener, READLINE_FLAGS);
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
        if (forcePaging && settings.isOutputPaging()) {
            return true;
        }
        return settings.isOutputPaging() && isSystemTerminal;
    }

    public boolean isPagingOutputActive() {
        return pagingSupport != null && pagingSupport.isPagingOutputActive();
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
