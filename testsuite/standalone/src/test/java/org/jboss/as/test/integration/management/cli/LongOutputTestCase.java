package org.jboss.as.test.integration.management.cli;

import org.aesh.readline.terminal.Key;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
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
 * This test covers the minimal use-cases.
 * @author eduda@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class LongOutputTestCase {

    private static final Logger log = Logger.getLogger(WildflyTestRunner.class);

    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final Pattern morePattern = Pattern.compile(".*--More\\(\\d+%\\)--$");
    private static final AtomicBoolean readThreadActive = new AtomicBoolean(true);
    private static final List<Thread> threads = new ArrayList<>();
    private static final BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);

    private static CommandContext ctx;
    private static PipedInputStream consoleInput;
    private static PrintWriter consoleWriter;
    private static PipedOutputStream consoleOutput;
    private static Reader consoleReader;
    private static ReadlineConsole readlineConsole;

    @BeforeClass
    public static void setup() throws Exception {

        consoleInput = new PipedInputStream();
        consoleWriter = new PrintWriter(new PipedOutputStream(consoleInput));
        consoleOutput = new PipedOutputStream();
        InputStream consoleInputStream = new PipedInputStream(consoleOutput);
        consoleReader = new InputStreamReader(consoleInputStream);

        ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), consoleInput, consoleOutput);

        Class<?> ctxClass = Class.forName("org.jboss.as.cli.impl.CommandContextImpl");
        Method getConsoleMethod = ctxClass.getDeclaredMethod("getConsole");
        getConsoleMethod.setAccessible(true);
        readlineConsole = (ReadlineConsole) getConsoleMethod.invoke(ctx);
        readlineConsole.forcePagingOutput(true);

        ctx.connectController();

        final CommandContext c = ctx;
        Thread interactThread = new Thread(() -> c.interact());
        threads.add(interactThread);
        interactThread.start();

        final InputStream readThreadIs = consoleInputStream;
        final Reader readThreadReader = consoleReader;

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
    }

    @AfterClass
    public static void tearDown() throws Exception {
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

    @After
    public void afterTest() throws Exception {
        if (readlineConsole.isPagingOutputActive()) {
            consoleWriter.print(Key.Q.getAsChar());
            Assert.assertFalse(consoleWriter.checkError());
            String window = queue.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(window);
        }
    }

    /**
     * Check for More prompt, 1 line up, 1 line down, search then exits.
     */
    @Test
    public void testBasic() throws Exception {
        consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
        Assert.assertFalse(consoleWriter.checkError());

        String window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
        Assert.assertEquals(window, readlineConsole.getTerminalHeight() + 1, countLines(window));

        String lastLine = getBeforeLastLine(window);

        consoleWriter.print(Key.ENTER.getAsChar());
        Assert.assertFalse(consoleWriter.checkError());
        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
        Assert.assertEquals(window, 2, countLines(window));

        // it sends semicolon to the console what should move output one line up
        consoleWriter.print(Key.SEMI_COLON.getAsChar());
        Assert.assertFalse(consoleWriter.checkError());
        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
        Assert.assertEquals(lastLine, getBeforeLastLine(window));

        // Checks the search functionality.
        // tests /description
        final String pattern = "description";
        consoleWriter.print("/");
        consoleWriter.flush();
        Thread.sleep(100);
        consoleWriter.println(pattern);
        Assert.assertFalse(consoleWriter.checkError());
        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
        window = getLastNumberOfLines(window, readlineConsole.getTerminalHeight());
        Assert.assertEquals(readlineConsole.getTerminalHeight(), countLines(window));
        String firstMatch = getFirstLine(window);
        Assert.assertTrue(firstMatch, firstMatch.contains(pattern));

        consoleWriter.print(Key.Q.getAsChar());
        Assert.assertFalse(consoleWriter.checkError());
        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
    }

    private static String getBeforeLastLine(String window) {
        String[] lines = window.split(LINE_SEP);
        return (lines.length > 1) ? lines[lines.length-2] : "";
    }

    private static String getFirstLine(String window) {
        return window.substring(0, window.indexOf(System.getProperty("line.separator")));
    }

    private static String getLastNumberOfLines(String window, int number) {
        int numberOfLines = countLines(window);
        int firstWantedLine = numberOfLines - number;

        Assert.assertTrue(String.format("numberOfLines: %d, number: %d", numberOfLines, number), firstWantedLine > 0);

        int lineIndex = 1;
        int pos = 0;

        while ((pos = window.indexOf(LINE_SEP, pos) + 1) != 0 && lineIndex < firstWantedLine) {
            lineIndex++;
        }
        return window.substring(pos + 1);
    }

    private static int countLines(String str) {

        return StringUtils.countMatches(str, LINE_SEP) + 1;
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
