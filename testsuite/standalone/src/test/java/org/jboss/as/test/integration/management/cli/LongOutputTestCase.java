package org.jboss.as.test.integration.management.cli;

import org.aesh.readline.terminal.Key;
import org.aesh.terminal.utils.Config;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.IOUtil;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.cli.impl.ReadlineConsole;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
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
    private static final Pattern promptPattern = Pattern.compile(".*\\[.*@.* /\\]\\s*$");
    private static final int bufferSize = 1024*64;

    private AtomicBoolean readThreadActive;
    private List<Thread> threads;
    private BlockingQueue<String> queue;
    private CommandContext ctx;
    private PipedInputStream consoleInput;
    private PrintWriter consoleWriter;
    private PipedOutputStream consoleOutput;
    private Reader consoleReader;
    private ReadlineConsole readlineConsole;
    private InputStream consoleInputStream;
    private StringBuilder sb;
    private String originalCliConfig;

    private enum OutputPaging   {
        ENABLE_PAGING_OUTPUT_VIA_ARGUMENT,
        ENABLE_PAGING_OUTPUT_VIA_XML,
        DISABLE_PAGING_OUTPUT_VIA_XML,
        DISABLE_PAGING_OUTPUT_VIA_ARGUMENT,
        DEFAULT
    }

    @Before
    public void setup() throws Exception {
        readThreadActive = new AtomicBoolean(true);
        threads = new ArrayList<>();
        queue = new ArrayBlockingQueue<>(1);
        consoleInput = new PipedInputStream(bufferSize);
        consoleWriter = new PrintWriter(new PipedOutputStream(consoleInput));
        consoleOutput = new PipedOutputStream();
        consoleInputStream = new PipedInputStream(consoleOutput, bufferSize);
        consoleReader = new InputStreamReader(consoleInputStream);
        sb = new StringBuilder();
        // tests can  manipulate with jboss.cli.config system property thus we need  keep have original value so
        // it can be restored in @After phase
        originalCliConfig = WildFlySecurityManager.getPropertyPrivileged("jboss.cli.config", "");
    }

    private void setupConsole(OutputPaging outputPaging) throws Exception {
        CommandContextConfiguration.Builder builder =  CLITestUtil.getCommandContextBuilder(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(),
                consoleInput,
                consoleOutput);

        File configFile;
        switch (outputPaging)   {
            case ENABLE_PAGING_OUTPUT_VIA_XML:
                // create jboss-cli.xml and set to and set path to jboss.cli.config property
                configFile = CliConfigUtils.createConfigFile(false, 0, true, false, false, true);
                WildFlySecurityManager.setPropertyPrivileged("jboss.cli.config", configFile.getAbsolutePath());
                break;
            case DISABLE_PAGING_OUTPUT_VIA_XML:
                // create jboss-cli.xml and set to and set path to jboss.cli.config property
                configFile = CliConfigUtils.createConfigFile(false, 0, true, false, false, false);
                WildFlySecurityManager.setPropertyPrivileged("jboss.cli.config", configFile.getAbsolutePath());
                break ;
            case DISABLE_PAGING_OUTPUT_VIA_ARGUMENT:
                builder.setOutputPaging(false);
                break;
            case ENABLE_PAGING_OUTPUT_VIA_ARGUMENT:
                builder.setOutputPaging(true);
                break;
            case DEFAULT:
                // do nothing - just keep default (which is paging output enabled)
                break;
            default:
                // throw exception here
                throw new IllegalArgumentException("Invalid paging output enum passed: " + outputPaging
                        + " - see LongOutputTestCase.OutputPaging enum for valid values.");
        }

        ctx = CommandContextFactory.getInstance().newCommandContext(builder.build());

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
        final Reader readThreadReader = new BufferedReader(consoleReader);



        /**
         * The thread reads output from the CLI. There is implemented logic, which
         * captures an output into the StringBuilder. If no output is available
         * for 3 seconds, it suspects it is everything what should be read in this
         * round and it puts the captured output into the blocking queue.
         */
        Thread readThread = new Thread(() -> {

            char[] buffer = new char[bufferSize];
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
                            Thread.sleep(5);
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

    @After
    public void tearDown() throws Exception {
        afterTest();
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
        threads.removeAll(threads);
        IOUtil.close(consoleInput);
        IOUtil.close(consoleWriter);
        IOUtil.close(consoleOutput);
        IOUtil.close(consoleReader);

        // return back original value for jboss.cli.config property
        WildFlySecurityManager.setPropertyPrivileged("jboss.cli.config", originalCliConfig);
    }

    private void afterTest() throws Exception {
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
        setupConsole(OutputPaging.DEFAULT);
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

        emulateAlternateBuffer();

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

        consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
        Assert.assertFalse(consoleWriter.checkError());

        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
        // +1 is for command string which was sent to the CLI
        Assert.assertEquals(window, readlineConsole.getTerminalHeight() + 1, countLines(window));

        emulateAlternateBuffer();

        consoleWriter.print(Key.CTRL_C.getAsChar());
        Assert.assertFalse(consoleWriter.checkError());

        window = queue.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(window);

        final Pattern promptPattern = Pattern.compile(".*\\[.*@.* /\\]\\s*$");
        checkWithRegex(window, promptPattern);
        Assert.assertEquals(window, readlineConsole.getTerminalHeight(), countLines(window));
    }

    private void emulateAlternateBuffer() throws Exception {
        // We need to emulate alternateBuffer support
        // to be able to go up and search
        if (Config.isWindows()) {
            Field pagingSupportField = readlineConsole.getClass().getDeclaredField("pagingSupport");
            pagingSupportField.setAccessible(true);
            Object pagingSupport = pagingSupportField.get(readlineConsole);
            Field f = pagingSupport.getClass().getDeclaredField("paging");
            f.setAccessible(true);
            Object paging = f.get(pagingSupport);
            Field alt = paging.getClass().getDeclaredField("alternateSupported");
            alt.setAccessible(true);
            alt.set(paging, true);
        }
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

    /**
     * Check that the whole output was written at once when the output paging is disabled
     */
    @Test
    public void testDisabledOutputPagingViaArgument() throws Exception {
        setupConsole(OutputPaging.DISABLE_PAGING_OUTPUT_VIA_ARGUMENT);
        testDisabledOutputPaging();
    }

    @Test
    public void testEnableOutputPagingViaArgument() throws Exception {
        setupConsole(OutputPaging.ENABLE_PAGING_OUTPUT_VIA_ARGUMENT);
        testEnabledOutputPaging();
    }

    @Test
    public void testDisabledOutputPagingViaXml() throws Exception {
        setupConsole(OutputPaging.DISABLE_PAGING_OUTPUT_VIA_XML);
        testDisabledOutputPaging();
    }

    @Test
    public void testEnableOutputPagingViaXml() throws Exception {
        setupConsole(OutputPaging.ENABLE_PAGING_OUTPUT_VIA_XML);
        testEnabledOutputPaging();
    }

    private void testDisabledOutputPaging() throws Exception {
        consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
        Assert.assertFalse(consoleWriter.checkError());
        String window = queue.poll(10, TimeUnit.SECONDS);

        Assert.assertNotNull(window);
        checkWithRegex(window, promptPattern);

        //Check if the whole output was written at once - e.g. there is the starting "{" and ending "}"
        Assert.assertTrue(Pattern.compile("^\\{.*^\\}", Pattern.MULTILINE | Pattern.DOTALL)
                .matcher(window).find());
    }

    private void testEnabledOutputPaging() throws Exception {
        consoleWriter.println("/subsystem=elytron:read-resource-description(recursive=true)");
        Assert.assertFalse(consoleWriter.checkError());
        String window = queue.poll(10, TimeUnit.SECONDS);

        Assert.assertNotNull(window);
        checkWithRegex(window, morePattern);
    }
}
