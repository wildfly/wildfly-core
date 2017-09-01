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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.ReadlineFlag;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.alias.AliasCompletion;
import org.aesh.readline.alias.AliasManager;
import org.aesh.readline.alias.AliasPreProcessor;
import org.aesh.readline.completion.CompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.history.FileHistory;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.Terminal;
import org.aesh.readline.terminal.TerminalBuilder;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.cursor.Line.CursorTransactionBuilder;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.terminal.tty.Signal;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Attributes;
import org.aesh.utils.ANSI;
import org.aesh.utils.Config;
import org.aesh.util.FileAccessPermission;
import org.aesh.util.Parser;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.logmanager.Logger;

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
public class ReadlineConsole implements Console {

    private static final class CharacterMatcher {

        private static final List<Character> endSeparators = new ArrayList<>();
        private static final Map<Character, Character> endStartSeparators = new HashMap<>();

        static {
            endSeparators.add('}');
            endSeparators.add(']');
            endSeparators.add(')');
            endStartSeparators.put('}', '{');
            endStartSeparators.put(']', '[');
            endStartSeparators.put(')', '(');
        }

        private TerminalConnection connection;

        private int lastIndex = -1;

        private Line lastLine;

        private CharacterMatcher(TerminalConnection connection) {
            this.connection = connection;
        }

        void clear() {
            if (lastLine != null) {
                if (lastIndex >= 0) {
                    CursorTransactionBuilder builder = lastLine.newCursorTransactionBuilder();
                    builder.colorize(lastIndex, Color.DEFAULT, Color.DEFAULT, false);
                    builder.build().run();
                }
            }
        }

        void match(Line line) {
            lastLine = line;
            // Clear last colorized character.
            clear();

            char endChar = (char) line.getCharacterAtCursor();
            if (endSeparators.contains(endChar)) {
                String l = line.getLineToCursor();
                char startChar = endStartSeparators.get(endChar);
                int index = findStart(l, startChar, endChar);
                if (index == -1) {
                    // This one is a mismatch, colorize in red.
                    lastIndex = colorizeWrongEnd(line);
                    return;
                }
                lastIndex = colorize(line, index);
            } else {
                lastIndex = -1;
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

        private int colorize(Line line, int startIndex) {
            CursorTransactionBuilder builder = line.newCursorTransactionBuilder();
            builder.colorize(startIndex, Color.DEFAULT, Color.GREEN, true);
            builder.build().run();
            return startIndex;
        }

        private int colorizeWrongEnd(Line line) {
            CursorTransactionBuilder builder = line.newCursorTransactionBuilder();
            builder.colorize(line.getCurrentCharacterIndex(), Color.RED, Color.DEFAULT, true);
            builder.build().run();
            return line.getCurrentCharacterIndex();
        }
    }

    private static final Logger LOG = Logger.getLogger(ReadlineConsole.class.getName());

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
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, "Writing {0}",
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
    private final CommandContext cmdCtx;
    private final List<Completion> completions = new ArrayList<>();
    private final Readline readline;
    private final CLITerminalConnection connection;
    private final CommandHistory history = new HistoryImpl();
    private final FileHistory readlineHistory;
    private String prompt;
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

    private final Consumer<Signal> interruptHandler;

    private final CharacterMatcher matcher;
    private final CursorListener cursorListener = new CursorListener() {
        @Override
        public void moved(Line line) {
            matcher.match(line);
        }
    };

    ReadlineConsole(CommandContext cmdCtx, Settings settings) throws IOException {
        this.cmdCtx = cmdCtx;
        this.settings = settings;
        readlineHistory = new FileHistory(settings.getHistoryFile(),
                settings.getHistorySize(), settings.getPermission(), false);
        if (settings.isDisableHistory()) {
            readlineHistory.disable();
        } else {
            readlineHistory.enable();
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "History is enabled? {0}", !settings.isDisableHistory());
        }
        connection = newConnection();
        interruptHandler = signal -> {
            if (signal == Signal.INT) {
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("Calling InterruptHandler");
                }
                connection.write(Config.getLineSeparator());
                connection.close();
            }
        };
        connection.setSignalHandler(interruptHandler);
        // Do not display ^C
        Attributes attr = connection.getAttributes();
        attr.setLocalFlag(Attributes.LocalFlag.ECHOCTL, false);
        connection.setAttributes(attr);

        aliasManager = new AliasManager(new File(Config.getHomeDir()
                + Config.getPathSeparator() + ".aesh_aliases"), true);
        AliasPreProcessor aliasPreProcessor = new AliasPreProcessor(aliasManager);
        preProcessors.add(aliasPreProcessor);
        completions.add(new AliasCompletion(aliasManager));
        readline = new Readline();
        matcher = new CharacterMatcher(connection);
    }

    @Override
    public void setActionCallback(Consumer<String> callback) {
        this.callback = callback;
    }

    private CLITerminalConnection newConnection() throws IOException {
        CLIPrintStream stream = (CLIPrintStream) settings.getOutStream();
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Creating terminal connection ");
        }

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
                .output(stream)
                .nativeSignals(true)
                .name("CLI Terminal")
                // We ask for a system terminal only if the Output has not been redefined.
                // If the IO context is redirected ( <, or remote process usage),
                // then, whatever the output being redefined or not, the terminal
                // will be NOT a system terminal, that is the TerminalBuilder behavior.
                .system(!settings.isOutputRedefined())
                .build();
        CLITerminalConnection c = new CLITerminalConnection(terminal);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "New Terminal {0}", terminal.getClass());
        }
        return c;
    }

    @Override
    public void addCompleter(CommandLineCompleter completer) {
        completions.add((Completion) (CompleteOperation co) -> {
            List<String> candidates = new ArrayList<>();
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Completing {0}", co.getBuffer());
            }
            int offset = completer.complete(cmdCtx,
                    co.getBuffer(), co.getCursor(), candidates);
            co.setOffset(offset);
            co.addCompletionCandidates(candidates);
            String buffer = cmdCtx.getArgumentsString() == null
                    ? co.getBuffer() : cmdCtx.getArgumentsString() + co.getBuffer();
            if (co.getCompletionCandidates().size() == 1
                    && co.getCompletionCandidates().get(0).
                    getCharacters().startsWith(buffer)) {
                co.doAppendSeparator(true);
            } else {
                co.doAppendSeparator(false);
            }
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Completion candidates {0}",
                        co.getCompletionCandidates());
            }
        });
    }

    @Override
    public CommandHistory getHistory() {
        return history;
    }

    @Override
    public void clearScreen() {
        connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
    }

    @Override
    public void printColumns(Collection<String> list) {
        String[] newList = new String[list.size()];
        list.toArray(newList);
        String line = Parser.formatDisplayList(newList,
                getHeight(),
                getWidth());
        if (outputCollector == null) {
            connection.write(line);
        } else {
            outputCollector.append(line);
        }
    }

    @Override
    public void print(String line) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Print {0}", line);
        }
        if (outputCollector == null) {
            connection.write(line);
        } else {
            outputCollector.append(line);
        }
    }

    // handle "a la" 'more' scrolling
    // Doesn't take into account wrapped lines (lines that are longer than the
    // terminal width. This could make a page to skip some lines.
    private void printCollectedOutput() {
        if (outputCollector == null) {
            return;
        }
        try {
            String line = outputCollector.toString();
            if (line.isEmpty()) {
                return;
            }
            // '\R' will match any line break.
            // -1 to keep empty lines at the end of content.
            String[] lines = line.split("\\R", -1);
            int max = connection.getTerminal().getSize().getHeight();
            int currentLines = 0;
            int allLines = 0;
            while (allLines < lines.length) {
                if (currentLines > max - 2) {
                    try {
                        connection.write(ANSI.CURSOR_SAVE);
                        int percentage = (allLines * 100) / lines.length;
                        connection.write("--More(" + percentage + "%)--");
                        Key k = read();
                        connection.write(ANSI.CURSOR_RESTORE);
                        connection.stdoutHandler().accept(ANSI.ERASE_LINE_FROM_CURSOR);
                        switch (k) {
                            case SPACE: {
                                currentLines = 0;
                                break;
                            }
                            case ENTER:
                            case CTRL_M: { // On Mac, CTRL_M...
                                currentLines -= 1;
                                break;
                            }
                            case q: {
                                allLines = lines.length;
                                break;
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                } else {
                    String l = lines[allLines];
                    currentLines += 1;
                    allLines += 1;
                    // Do not add an extra \n
                    // The \n has been added by the previous line.
                    if (allLines == lines.length) {
                        if (l.isEmpty()) {
                            continue;
                        }
                    }
                    connection.write(l + Config.getLineSeparator());
                }
            }
        } finally {
            outputCollector = null;
        }
    }

    private Key read() throws InterruptedException {
        ActionDecoder decoder = new ActionDecoder();
        final Key[] key = {null};
        CountDownLatch latch = new CountDownLatch(1);
        Attributes attributes = connection.enterRawMode();
        connection.setStdinHandler(keys -> {
            decoder.add(keys);
            if (decoder.hasNext()) {
                key[0] = Key.findStartKey(decoder.next().buffer().array());
                latch.countDown();
                connection.suspend();
            }
        });
        connection.awake();
        try {
            // Wait until interrupted
            latch.await();
        } finally {
            connection.setStdinHandler(null);
        }
        return key[0];
    }

    @Override
    public void printNewLine() {
        print(outputCollector == null ? Config.getLineSeparator() : "\n");
    }

    @Override
    public String readLine(String prompt) throws IOException {
        return readLine(prompt, null);
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
    @Override
    public String readLine(String prompt, Character mask) throws IOException {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Prompt {0} mask {1}", new Object[]{prompt, mask});
        }

        // If there are some output collected, flush it.
        printCollectedOutput();
        // New collector
        outputCollector = new StringBuilder();

        // Keep a reference on the caller thread in case Ctrl-C is pressed
        // and thread needs to be interrupted.
        readingThread = Thread.currentThread();
        try {
            if (!started) {
                // That is the case of the CLI connecting prior to start the terminal.
                // No Terminal waiting in Main thread yet.
                // We are opening the connection to the terminal until we have read
                // something from prompt.
                return promptFromNonStartedConsole(prompt, mask);
            } else {
                return promptFromStartedConsole(prompt, mask);
            }
        } finally {
            readingThread = null;
        }
    }

    private String promptFromNonStartedConsole(String prompt, Character mask) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Not started");
        }
        String[] out = new String[1];
        if (connection.suspended()) {
            connection.awake();
        }
        readline.readline(connection, new Prompt(prompt, mask), newLine -> {
            out[0] = newLine;
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("Got some input");
            }
            // We must call stopReading to stop reading from terminal
            // and release this thread.
            connection.stopReading();
        }, null, null, null, null, READLINE_FLAGS);
        try {
            connection.openBlockingInterruptable();
        } catch (InterruptedException ex) {
            interrupted(ex);
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Done for prompt");
        }
        return out[0];
    }

    private String promptFromStartedConsole(String prompt, Character mask) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Prompt {0} mask {1}", new Object[]{prompt, mask});
        }
        String[] out = new String[1];
        // We must be called from another Thread. connection is reading in Main thread.
        // calling readline will wakeup the Main thread that will execute
        // the Prompt handling.
        // We can safely wait.
        if (readingThread == startThread) {
            throw new RuntimeException("Can't prompt from the Thread that is "
                    + "reading terminal input");
        }
        CountDownLatch latch = new CountDownLatch(1);

        // We need to set the interrupt SignalHandler to interrupt the CLI.
        Consumer<Signal> prevHandler = connection.getSignalHandler();
        connection.setSignalHandler(interruptHandler);
        readline.readline(connection, new Prompt(prompt, mask), newLine -> {
            out[0] = newLine;
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("Got some input");
            }
            latch.countDown();
        }, null, null, null, null, READLINE_FLAGS);
        try {
            latch.await();
        } catch (InterruptedException ex) {
            interrupted(ex);
        } finally {
            connection.setSignalHandler(prevHandler);
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Done for prompt");
        }
        return out[0];
    }

    private void interrupted(InterruptedException ex) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Thread interrupted");
        }
        // Ctrl-C, interrupt and throw exception to cancel prompting.
        Thread.currentThread().interrupt();
        throw new RuntimeException(ex);
    }

    @Override
    public int getTerminalWidth() {
        return getWidth();
    }

    @Override
    public int getTerminalHeight() {
        return getHeight();
    }

    private int getHeight() {
        if (connection instanceof TerminalConnection) {
            return ((TerminalConnection) connection).getTerminal().getSize().getHeight();
        }
        return connection.size().getHeight();
    }

    private int getWidth() {
        if (connection instanceof TerminalConnection) {
            return ((TerminalConnection) connection).getTerminal().getSize().getWidth();
        }
        return connection.size().getWidth();
    }

    @Override
    public void start() throws IOException {
        if (closed) {
            throw new IllegalStateException("Console has already been closed");
        }
        if (!started) {
            startThread = Thread.currentThread();
            started = true;
            loop();
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Started in thread {0}. Waiting...",
                        startThread.getName());
            }
            try {
                connection.openBlockingInterruptable();
            } catch (InterruptedException ex) {
                // OK leaving
            }
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("Leaving console");
            }
        } else if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Already started");
        }
    }

    private void loop() {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Set a readline callback with prompt {0}", prompt);
        }
        // Console could have been closed during a command execution.
        if (!closed) {
            readline.readline(connection, new Prompt(prompt), line -> {
                // All commands can lead to prompting the user. So require
                // to be executed in a dedicated thread.
                // This can happen if a security configuration occurs
                // on the remote server.
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER,
                            "Executing command {0} in a new thread.", line);
                }
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
                                if (LOG.isLoggable(Level.FINER)) {
                                    LOG.log(Level.FINER, "Interrupting command: " + line,
                                            line);
                                }
                                callingThread.interrupt();
                            }
                        }
                    });
                    try {
                        outputCollector = new StringBuilder();
                        callback.accept(line);
                    } finally {
                        printCollectedOutput();
                        // The current thread could have been interrupted.
                        // Clear the flag to safely interact with aesh-readline
                        Thread.interrupted();
                        connection.setSignalHandler(handler);
                        if (LOG.isLoggable(Level.FINER)) {
                            LOG.log(Level.FINER, "Done Executing command {0}",
                                    line);
                        }
                        loop();
                    }
                });
            }, completions, preProcessors, readlineHistory, cursorListener, READLINE_FLAGS);
        }
    }

    private boolean handleAlias(String line) {
        if (line.startsWith("alias ") || line.equals("alias")) {
            String out = aliasManager.parseAlias(line.trim());
            if (out != null) {
                connection.write(out);
            }
            return true;
        } else if (line.startsWith("unalias ") || line.equals("unalias")) {
            String out = aliasManager.removeAlias(line.trim());
            if (out != null) {
                connection.write(out);
            }
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        if (!closed) {
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("Stopping.");
            }
            closed = true;
            if (readingThread != null) {
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("Interrupting reading thread");
                }
                readingThread.interrupt();
            }
            if (started) {
                readlineHistory.stop();
                aliasManager.persist();
            }
            executor.shutdown();
            connection.close();
        }
    }

    @Override
    public boolean running() {
        return started;
    }

    @Override
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
