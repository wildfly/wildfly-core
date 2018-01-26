package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author kanovotn@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class AppendTestCase {
    @Test
    public void testAppendNonExistingFile() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        String fileName= "non-existing-file";
        Path path = Paths.get(fileName);
        try {
            ctx.connectController();
            ctx.handle("version >> " + fileName);
            Assert.assertTrue("The file" + fileName + "doesn't contain expected content.",
                    checkFileContains(path, "Release: "));
        } finally {
            ctx.terminateSession();
            Files.delete(path);
        }

    }

    @Test
    public void testAppendExistingFile() throws Exception {
        testAppend("tmpfile", "version", "Release:");
    }

    @Test
    public void testAppendTwiceExistingFile() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);

        Path tempFile = Files.createTempFile("tempFile", ".tmp");
        String tempFileStringPath = tempFile.toString();

        writeTestFile(tempFile, "some content");

        try {
            ctx.connectController();
            ctx.handle("echo First append >> " + tempFileStringPath);
            ctx.handle("echo Second append >> " + tempFileStringPath);

            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(tempFile, "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content. Expected string: First append",
                    checkFileContains(tempFile, "First append"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content. Expected string: Second append",
                    checkFileContains(tempFile, "Second append"));
        } finally {
            ctx.terminateSession();
            Files.delete(tempFile);
        }
    }

    @Test
    public void testAppendFileRelativePath() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        Path tempDir = Files.createTempDirectory("tempdir");
        Path tempFile = Files.createTempFile(tempDir, "tempFile", ".tmp");
        Path tempFilePath = tempDir.resolve(tempFile);
        String tempFileStringPath = tempFilePath.toString();

        writeTestFile(tempFilePath, "some content");

        try {
            ctx.connectController();
            ctx.handle("version >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(Paths.get(tempFileStringPath), "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(Paths.get(tempFileStringPath), "Release: "));
        } finally {
            ctx.terminateSession();
            tempFile.toFile().delete();
            tempDir.toFile().delete();
        }
    }

    @Test
    public void testAppendFileAbsolutePath() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        Path tempDir = Files.createTempDirectory("tempdir");
        Path tempFile = Files.createTempFile(tempDir, "tempFile", ".tmp");
        Path tempFilePath = tempDir.resolve(tempFile);
        String tempFileStringPath = tempFilePath.toAbsolutePath().toString();

        writeTestFile(tempFilePath, "some content");

        try {
            ctx.connectController();
            ctx.handle("version >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(Paths.get(tempFileStringPath), "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(Paths.get(tempFileStringPath), "Release: "));
        } finally {
            ctx.terminateSession();
            tempFile.toFile().delete();
            tempDir.toFile().delete();
        }
    }

    @Test
    public void testAppendSpecialCharsInFile() throws Exception {
        testAppend("tmpfile", "echo üúö", "üúö");
    }

    @Test
    public void testAppendFileNameSpecialChars() throws Exception {
        testAppend("tmpüúö", "version", "Release:");
    }

    @Test
    public void testAppendFileNameQuestionMark() throws Exception {
        // '?' character is not supported in filename in windows
        if (!Util.isWindows()) {
           testAppend("tmp?file", "version", "Release:");
        }
    }

    @Test
    @Ignore("Uncomment when https://issues.jboss.org/browse/WFCORE-3554 is fixed")
    public void testAppendFileNamePipe() throws Exception {
        // '|' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp|file", "version", "Release:");
        }
    }

    @Test
    public void testAppendFileNameColon() throws Exception {
        // ':' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp:file", "version", "Release:");
        }
    }

    private void testAppend(String filename, String commandToAppend, String expectedContent) throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        Path tempFile = Files.createTempFile(filename, ".tmp");
        String tempFileStringPath = tempFile.toString();

        writeTestFile(tempFile, "some content");

        try {
            ctx.connectController();
            ctx.handle(commandToAppend + " >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(tempFile, "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(tempFile, expectedContent));
        } finally {
            ctx.terminateSession();
            Files.delete(tempFile);
        }
    }

    private void writeTestFile(Path path, String content) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, Charset.forName("UTF-8"))) {
            bw.write(content);
        } catch (IOException ex) {
            Assert.fail("Failed to prepare test file.");
        }
    }

    private boolean checkFileContains(Path path, String expectedString) {

        String currentLine = null;
        boolean found = false;

        try (BufferedReader br = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            while ((currentLine = br.readLine()) != null) {
                if (currentLine.contains(expectedString)) {
                    found = true;
                    break;
                }
            }
        } catch (IOException e) {
            Assert.fail("Failed to read content of the test file.");
        }
        return found;
    }
}
