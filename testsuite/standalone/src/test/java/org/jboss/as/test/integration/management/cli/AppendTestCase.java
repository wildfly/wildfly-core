/*
Copyright 2018 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jboss.as.cli.impl.CommandContextImpl;

/**
 * @author kanovotn@redhat.com
 */
public class AppendTestCase {

    @Test
    public void testAppendNonExistingFileBoot() throws Exception {
        CommandContext ctx = new CommandContextImpl(null);
        String fileName = "non-existing-file-boot";
        Path path = Paths.get(fileName);
        try {
            ctx.handle("echo foo >> " + fileName);
            Assert.assertTrue("The file" + fileName + "doesn't contain expected content.",
                    checkFileContains(path, "foo"));
        } finally {
            ctx.terminateSession();
            Files.delete(path);
        }

    }

    @Test
    public void testAppendNonExistingFile() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        String fileName= "non-existing-file";
        Path path = Paths.get(fileName);
        try {
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
        testAppend("tmpfile", "version", "Release:", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    @Test
    public void testAppendExistingFileBoot() throws Exception {
        testAppend("tmpfile", "echo foo", "foo", new CommandContextImpl(null));
    }

    @Test
    public void testAppendTwiceExistingFileBoot() throws Exception {
        doTestAppendTwiceExistingFile(new CommandContextImpl(null));
    }

    @Test
    public void testAppendTwiceExistingFile() throws Exception {
        doTestAppendTwiceExistingFile(CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    void doTestAppendTwiceExistingFile(CommandContext ctx) throws Exception {
        Path tempFile = Files.createTempFile("tempFile", ".tmp");
        String tempFileStringPath = tempFile.toString();

        writeTestFile(tempFile, "some content");

        try {
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
    public void testAppendFileRelativePathBoot() throws Exception {
        doTestAppendFileRelativePath(new CommandContextImpl(null));
    }

    @Test
    public void testAppendFileRelativePath() throws Exception {
        doTestAppendFileRelativePath(CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    void doTestAppendFileRelativePath(CommandContext ctx) throws Exception {
        Path tempDir = Files.createTempDirectory("tempdir");
        Path tempFile = Files.createTempFile(tempDir, "tempFile", ".tmp");
        Path tempFilePath = tempDir.resolve(tempFile);
        String tempFileStringPath = tempFilePath.toString();

        writeTestFile(tempFilePath, "some content");

        try {
            ctx.handle("echo foo >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(Paths.get(tempFileStringPath), "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(Paths.get(tempFileStringPath), "foo"));
        } finally {
            ctx.terminateSession();
            tempFile.toFile().delete();
            tempDir.toFile().delete();
        }
    }

    @Test
    public void testAppendFileAbsolutePathBoot() throws Exception {
        doTestAppendFileAbsolutePath(new CommandContextImpl(null));
    }

    @Test
    public void testAppendFileAbsolutePath() throws Exception {
        doTestAppendFileAbsolutePath(CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    void doTestAppendFileAbsolutePath(CommandContext ctx) throws Exception {
        Path tempDir = Files.createTempDirectory("tempdir");
        Path tempFile = Files.createTempFile(tempDir, "tempFile", ".tmp");
        Path tempFilePath = tempDir.resolve(tempFile);
        String tempFileStringPath = tempFilePath.toAbsolutePath().toString();

        writeTestFile(tempFilePath, "some content");

        try {
            ctx.handle("echo foo >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have the initial content.",
                    checkFileContains(Paths.get(tempFileStringPath), "some content"));
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(Paths.get(tempFileStringPath), "foo"));
        } finally {
            ctx.terminateSession();
            tempFile.toFile().delete();
            tempDir.toFile().delete();
        }
    }

    @Test
    public void testAppendSpecialCharsInFileBoot() throws Exception {
        testAppend("tmpfile", "echo üúö", "üúö", new CommandContextImpl(null));
    }

    @Test
    public void testAppendSpecialCharsInFile() throws Exception {
        testAppend("tmpfile", "echo üúö", "üúö", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    @Test
    public void testAppendFileNameSpecialCharsBoot() throws Exception {
        testAppend("tmpüúö", "echo foo", "foo", new CommandContextImpl(null));
    }

    @Test
    public void testAppendFileNameSpecialChars() throws Exception {
        testAppend("tmpüúö", "version", "Release:", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out));
    }

    @Test
    public void testAppendFileNameQuestionMarkBoot() throws Exception {
        // '?' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp?file", "echo foo", "foo", new CommandContextImpl(null));
        }
    }

    @Test
    public void testAppendFileNameQuestionMark() throws Exception {
        // '?' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp?file", "version", "Release:", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out));
        }
    }

    @Test
    public void testAppendFileNamePipeBoot() throws Exception {
        // '|' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp\\|file", "echo foo", "foo", new CommandContextImpl(null));
        }
    }

    @Test
    public void testAppendFileNamePipe() throws Exception {
        // '|' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp\\|file", "version", "Release:", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out));
        }
    }

    @Test
    public void testAppendFileNameColonBoot() throws Exception {
        // ':' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp:file", "echo foo", "foo", new CommandContextImpl(null));
        }
    }

    @Test
    public void testAppendFileNameColon() throws Exception {
        // ':' character is not supported in filename in windows
        if (!Util.isWindows()) {
            testAppend("tmp:file", "version", "Release:", CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out));
        }
    }

    private void testAppend(String filename, String commandToAppend, String expectedContent, CommandContext ctx) throws Exception {
        // Do not create a file with escape character in its name.
        String filtered = filename.replaceAll("\\\\", "");
        Path tempFile = Files.createTempFile(filtered, ".tmp");
        // Create an escaped path to be used from CLI.
        Path escapedPath = tempFile.getParent().resolve(tempFile.getFileName().toString().replace(filtered, filename));
        String tempFileStringPath = tempFile.toString();

        writeTestFile(tempFile, "some content");

        try {
            ctx.handle(commandToAppend + " >> " + escapedPath.toString());
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
