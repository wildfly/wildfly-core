package org.jboss.as.test.integration.management.cli;

import org.aesh.readline.terminal.Key;
import org.codehaus.plexus.util.IOUtil;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author eduda@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class MoreTestCase {

    private static final Logger log = Logger.getLogger(WildflyTestRunner.class);

    @Test
    public void testPaging() throws Exception {
        final Pattern morePattern = Pattern.compile(".*--More\\(\\d+%\\)--$");
        final Pattern promptPattern = Pattern.compile(".*\\[.*@.* /\\]\\s*$");
        final AtomicBoolean readThreadActive = new AtomicBoolean(true);

        PipedInputStream consoleInput = null;
        PrintWriter consoleWriter = null;
        PipedOutputStream consoleOutput = null;
        InputStream consoleInputStream;
        Reader consoleReader = null;
        CommandContext ctx = null;
        List<Thread> threads = null;

        try {
            threads = new ArrayList<>();
            consoleInput = new PipedInputStream();
            consoleWriter = new PrintWriter(new PipedOutputStream(consoleInput));

            consoleOutput = new PipedOutputStream();
            consoleInputStream = new PipedInputStream(consoleOutput);
            consoleReader = new InputStreamReader(consoleInputStream);

            ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), consoleInput, consoleOutput);

            Class<?> ctxClass = Class.forName("org.jboss.as.cli.impl.CommandContextImpl");
            Method getConsoleMethod = ctxClass.getDeclaredMethod("getConsole");
            getConsoleMethod.setAccessible(true);
            ReadlineConsole readlineConsole = (ReadlineConsole) getConsoleMethod.invoke(ctx);

            readlineConsole.forcePagingOutput(true);

            ctx.connectController();

            final CommandContext c = ctx;
            Thread interactThread = new Thread(() -> c.interact());
            threads.add(interactThread);
            interactThread.start();

            final InputStream readThreadIs = consoleInputStream;
            final Reader readThreadReader = consoleReader;
            final BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);

            /**
             * The thread reads output from the CLI. There is implemented logic, which
             * captures an output into the StringBuilder. If no output is available
             * for 3 seconds, it suspects it is everything what should be read in this
             * round and it puts the captured output into the blocking queue.
             */
            Thread readThread = new Thread(() -> {

                char[] buffer = new char[1024];
                StringBuilder sb = new StringBuilder();
                int noAvailable = 0;

                while (readThreadActive.get()) {
                    try {
                        if (readThreadIs.available() > 0) {
                            noAvailable = 0;
                            int read = readThreadReader.read(buffer);
                            sb.append(buffer, 0, read);
                        } else {
                            if (noAvailable < 300) {
                                noAvailable++;
                                Thread.sleep(10);
                            } else {
                                queue.put(sb.toString());
                                sb = new StringBuilder();
                                noAvailable = 0;
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }

            });
            threads.add(readThread);
            readThread.start();

            // this just reads the lines which are printed when CLI is started
            String window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);

            Assert.assertTrue(readlineConsole.getTerminalHeight() > 0);

            consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            // +1 is for command string which was sent to the CLI
            Assert.assertEquals(window, readlineConsole.getTerminalHeight() + 1, countLines(window));

            //last output line should be the same after move 1 line down and move 1 line back up
            String lastOutputLine = getBeforeLastLine(window);
            // it sends enter to the console what should return only one new line
            consoleWriter.print(Key.ENTER.getAsChar());
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            // we expect 2 because on the second line there is --More(%)-- string
            Assert.assertEquals(window,2, countLines(window));

            // it sends semicolon to the console what should move output one line up
            consoleWriter.print(Key.SEMI_COLON.getAsChar());
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            // The line above the --More(%)-- should be equal to the one before inserting ENTER
            Assert.assertEquals(window,lastOutputLine, getBeforeLastLine(window));
            Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));

            lastOutputLine = getBeforeLastLine(window);
            consoleWriter.print(" ");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));

            consoleWriter.print(Key.BACKSLASH.getAsChar());
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));
            Assert.assertEquals(lastOutputLine, getBeforeLastLine(window));

            lastOutputLine = getBeforeLastLine(window);
            consoleWriter.print("q");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, promptPattern);
            Assert.assertEquals(window, lastOutputLine, getBeforeLastLine(window));

            consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);

            lastOutputLine = getBeforeLastLine(window);
            consoleWriter.print(Key.ESC.getAsChar());
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, promptPattern);
            Assert.assertEquals(window, lastOutputLine, getBeforeLastLine(window));
            // the output should be printed into the main buffer as well after exiting
            Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));

            consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            // +1 is for command string which was sent to the CLI
            Assert.assertEquals(window, readlineConsole.getTerminalHeight() + 1, countLines(window));

            consoleWriter.print("Q");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, promptPattern);
            // Check that the content displayed before 'Q' was printed is printed to
            // the main buffer.
            Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));

            consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
            Assert.assertFalse(consoleWriter.checkError());
            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);

            consoleWriter.print(Key.DOWN.getKeyValuesAsString());
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, morePattern);
            // we expect 2 because on the second line there is --More(%)-- string
            Assert.assertEquals(window, 2, countLines(window));

            consoleWriter.print("Q");
            Assert.assertFalse(consoleWriter.checkError());

            window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
            checkWithRegex(window, promptPattern);
        } finally {
            readThreadActive.set(false);
            if (ctx != null) {
                ctx.terminateSession();
            }
            for (Thread thread : threads) {
                thread.join(5000);
                if (thread.isAlive()) {
                    thread.interrupt();
                }
                waitFor(() -> !thread.isAlive(), 10000);
            }
            IOUtil.close(consoleInput);
            IOUtil.close(consoleWriter);
            IOUtil.close(consoleOutput);
            IOUtil.close(consoleReader);
        }

    }

    private String getBeforeLastLine(String window) {
        String[] lines = window.split(System.getProperty("line.separator"));
        return (lines.length > 1) ? lines[lines.length-2] : "";
    }

    private static int countLines(String str) {
        String lineSep = System.getProperty("line.separator");
        if (str == null || str.isEmpty()) {
            return 0;
        }
        int lines = 1;
        int pos = 0;
        while ((pos = str.indexOf(lineSep, pos) + 1) != 0) {
            lines++;
        }
        return lines;
    }

    private static void checkWithRegex(String window, Pattern pattern) {
        Matcher m = pattern.matcher(window.replaceAll("\\R", " "));
        Assert.assertTrue(window, m.matches());
    }

    private static void waitFor(Callable<Boolean> condition, long timeout) throws Exception {
        long timeToWait = System.currentTimeMillis() + timeout;

        while (System.currentTimeMillis() < timeToWait) {
            Boolean state = condition.call();
            if (state) {
                return;
            }
            Thread.sleep(100);
        }
        Assert.fail("waitFor timed out");
    }

}