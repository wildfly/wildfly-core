/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author jdenise@redhat.com
 */
public class FileCompletionTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    private File file1;
    private File file_ws;
    private File file_q;
    private File dir1;

    private File sub_file1;
    private File sub_file_ws;
    private File sub_file_q;
    private File sub_file_q_ws;
    private File sub_dir1;

    private File dot_sub_file1;

    private final List<String> allFileNames = new ArrayList<>();
    private final List<String> allSubFileNames = new ArrayList<>();
    private final String escapedWSFile = "spaces\\ file.txt";
    private final String escapedQuotedFile = "quotes\\\"file.txt";
    private final String escapedQuotedWSFile = "anotherquo tes\\\"file.txt";

    private final String escapedWSFile2 = "spaces\\ file2.txt";
    private final String escapedQuotedFile2 = "quotes\\\"file2.txt";

    private CommandContext ctx;
    private CommandLineCompleter completer;

    @Before
    public void setup() throws IOException, CliInitializationException {
        System.setProperty("user.home", temporaryUserHome.getRoot().getAbsolutePath());
        ctx = CommandContextFactory.getInstance().newCommandContext();
        completer = FilenameTabCompleter.newCompleter(ctx);
        file1 = temporaryUserHome.newFile();
        file_ws = temporaryUserHome.newFile("spaces file.txt");
        if (!Util.isWindows()) {
            file_q = temporaryUserHome.newFile("quotes\"file.txt");
            allFileNames.add(file_q.getName());
        }
        dir1 = temporaryUserHome.newFolder("adirectory");
        allFileNames.add(file1.getName());
        allFileNames.add(file_ws.getName());
        allFileNames.add(dir1.getName());

        ctx.setCurrentDir(dir1);

        dot_sub_file1 = new File(dir1, ".file2.txt");
        dot_sub_file1.createNewFile();

        sub_file1 = new File(dir1, "file2.txt");
        sub_file1.createNewFile();

        sub_file_ws = new File(dir1, "spaces file2.txt");
        sub_file_ws.createNewFile();

        if (!Util.isWindows()) {
            sub_file_q = new File(dir1, "quotes\"file2.txt");
            sub_file_q.createNewFile();
            allSubFileNames.add(sub_file_q.getName());
            sub_file_q_ws = new File(dir1, "anotherquo tes\"file.txt");
            sub_file_q_ws.createNewFile();
            allSubFileNames.add(sub_file_q_ws.getName());
        }

        sub_dir1 = new File(dir1, "adirectory2");
        sub_dir1.mkdir();

        allSubFileNames.add(dot_sub_file1.getName());
        allSubFileNames.add(sub_file1.getName());
        allSubFileNames.add(sub_file_ws.getName());
        allSubFileNames.add(sub_dir1.getName());
    }

    @Test
    public void testQuotedHomeDirectoryCompletion() throws Exception {

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "\"~", 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains(temporaryUserHome.getRoot().getName()));
        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "\"~"
                    + temporaryUserHome.getRoot().getName().substring(0, 2), 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains("\"~" + temporaryUserHome.getRoot().getName() + File.separator));
        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "\"~"
                    + temporaryUserHome.getRoot().getName(), 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains("\"~" + temporaryUserHome.getRoot().getName() + File.separator));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "\"~" + temporaryUserHome.getRoot().getName() + File.separator;
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(path.length(), i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allFileNames));
        }

        if (!Util.isWindows()) {
            {
                //Quoted path requires escaped quote.
                List<String> candidates = new ArrayList<>();
                String path = "\"~" + File.separator + file_q.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 0, candidates);
                Assert.assertEquals(3, i);
                Assert.assertTrue(candidates.toString(), candidates.contains(escapedQuotedFile));
            }
        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "\"~" + File.separator, 0, candidates);
            Assert.assertEquals(3, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allFileNames));
        }

        {
            //Quoted path doesn't require escaped spaces.
            List<String> candidates = new ArrayList<>();
            String path = "\"~" + File.separator + file_ws.getName().substring(0, 2);
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(3, i);
            Assert.assertTrue(candidates.toString(), candidates.contains(file_ws.getName()));
        }

    }

    @Test
    public void testHomeDirectoryCompletion() throws Exception {

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "~", 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains(temporaryUserHome.getRoot().getName()));
        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "~"
                    + temporaryUserHome.getRoot().getName().substring(0, 2), 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains("~" + temporaryUserHome.getRoot().getName() + File.separator));
        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "~"
                    + temporaryUserHome.getRoot().getName(), 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains("~" + temporaryUserHome.getRoot().getName() + File.separator));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "~" + temporaryUserHome.getRoot().getName() + File.separator;
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(path.length(), i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allFileNames));
        }

        if (!Util.isWindows()) {
            {
                //Not quoted path requires escaped spaces.
                List<String> candidates = new ArrayList<>();
                String path = "~" + File.separator + file_ws.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 0, candidates);
                Assert.assertEquals(2, i);
                Assert.assertTrue(candidates.toString(), candidates.contains(escapedWSFile));
            }

            {
                //Not quoted path requires escaped quote.
                List<String> candidates = new ArrayList<>();
                String path = "~" + File.separator + file_q.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 0, candidates);
                Assert.assertEquals(2, i);
                Assert.assertTrue(candidates.toString(), candidates.contains(escapedQuotedFile));
            }

        }

        {
            List<String> candidates = new ArrayList<>();
            int i = completer.complete(ctx, "~" + File.separator, 0, candidates);
            Assert.assertEquals(2, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allFileNames));
        }
    }

    @Test
    public void testWorkDirectoryCompletion() throws Exception {
        {
            List<String> candidates = new ArrayList<>();
            String path = "";
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allSubFileNames));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "." + File.separator;
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(2, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allSubFileNames));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = ".";
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.size() == 1);
            Assert.assertTrue(candidates.toString(), candidates.contains(dot_sub_file1.getName()));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = sub_dir1.getName();
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.size() == 1);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains(sub_dir1.getName() + File.separator));
        }

        if (!Util.isWindows()) {
            {
                //Not quoted path requires escaped spaces.
                List<String> candidates = new ArrayList<>();
                String path = sub_file_ws.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 0, candidates);
                Assert.assertEquals(0, i);
                Assert.assertTrue(candidates.toString(), candidates.contains(escapedWSFile2));
            }

            {
                //Not quoted path requires escaped quote.
                List<String> candidates = new ArrayList<>();
                String path = sub_file_q.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 0, candidates);
                Assert.assertEquals(0, i);
                Assert.assertTrue(candidates.toString(), candidates.contains(escapedQuotedFile2));
            }
        }
    }

    @Test
    public void testQuotedWorkDirectoryCompletion() throws Exception {
        {
            List<String> candidates = new ArrayList<>();
            String path = "\"";
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allSubFileNames));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "\"." + File.separator;
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(3, i);
            Assert.assertTrue(candidates.toString(), candidates.containsAll(allSubFileNames));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "\".";
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.size() == 1);
            Assert.assertTrue(candidates.toString(), candidates.contains("\"" + dot_sub_file1.getName()));
        }

        {
            List<String> candidates = new ArrayList<>();
            String path = "\"" + sub_dir1.getName();
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.size() == 1);
            Assert.assertTrue(candidates.toString(),
                    candidates.contains("\"" + sub_dir1.getName() + File.separator));
        }

        {
            //Quoted path requires escaped spaces.
            List<String> candidates = new ArrayList<>();
            String path = "\"" + sub_file_ws.getName().substring(0, 2);
            int i = completer.complete(ctx,
                    path, 0, candidates);
            Assert.assertEquals(0, i);
            Assert.assertTrue(candidates.toString(), candidates.contains("\"" + sub_file_ws.getName()));
        }

        if (!Util.isWindows()) {
            {
                //Quoted path requires escaped quote.
                List<String> candidates = new ArrayList<>();
                String path = "\"" + sub_file_q.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 1, candidates);
                Assert.assertEquals(0, i);
                Assert.assertTrue(candidates.toString(), candidates.contains("\"" + escapedQuotedFile2));
            }
            {
                //Quoted path requires escaped quote but not escaped whitespace.
                List<String> candidates = new ArrayList<>();
                String path = "\"" + sub_file_q_ws.getName().substring(0, 2);
                int i = completer.complete(ctx,
                        path, 1, candidates);
                Assert.assertEquals(0, i);
                Assert.assertTrue(candidates.toString(), candidates.contains("\"" + escapedQuotedWSFile));
            }
        }
    }
}
