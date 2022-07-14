/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import java.io.File;
import java.nio.file.Files;
import org.jboss.as.test.integration.management.util.CLIWrapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class AttachmentTestCase {

    @Test
    public void test() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            assertTrue(f.exists() && f.length() != 0);

            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            assertTrue(f2.exists() && f2.length() != 0);
        } finally {
            f.delete();
            f2.delete();
            cli.quit();
        }
    }

    @Test
    public void testOtherDirectory() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File dir = new File(System.currentTimeMillis() + "attachment");
        dir.mkdir();
        File f = new File(dir, System.currentTimeMillis() + "attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            assertTrue(f.exists() && f.length() != 0);

            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            assertTrue(f2.exists() && f2.length() != 0);
        } finally {
            f.delete();
            f2.delete();
            dir.delete();
            cli.quit();
        }
    }

    @Test
    public void testOverwrite() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath()
                    + " --overwrite");
            assertTrue(f.exists() && f.length() != 0);

            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath()
                    + " --overwrite");
            assertTrue(f.exists() && f.length() != 0);
            assertFalse(f2.exists());
        } finally {
            f.delete();
            cli.quit();
        }
    }

    @Test
    public void testCreateDirectories() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        Path dir = new File(System.currentTimeMillis() + "attachment").toPath();
        Path f = dir.resolve(System.currentTimeMillis() + "attachment.log");
        assertFalse(Files.exists(dir));
        assertFalse(Files.exists(f));
        try {
            assertFalse(cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.toAbsolutePath(), true));
            assertFalse(Files.exists(dir));
            assertFalse(Files.exists(f));
            cli.sendLine("attachment save --createDirs --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.toAbsolutePath());
            assertTrue(Files.exists(dir));
            assertTrue(Files.exists(f));
            assertTrue(Files.size(f) > 0L);
        } finally {
            Files.deleteIfExists(f);
            Files.deleteIfExists(dir);
            cli.quit();
        }
    }

    @Test
    public void testBatch() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "batch_attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("batch");
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            cli.sendLine("run-batch");
            assertTrue(f.exists() && f.length() != 0);
            assertTrue(f2.exists() && f2.length() != 0);
        } finally {
            f.delete();
            f2.delete();
            cli.quit();
        }
    }

    @Test
    public void testBatchFile() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "batch_attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        File batchFile = new File(System.currentTimeMillis() + "batch.cli");
        String cmd = "attachment save --operation=/subsystem=logging/log-file=server.log:"
                + "read-attribute(name=stream) --file=" + f.getAbsolutePath() + "\n"
                + "attachment save --operation=/subsystem=logging/log-file=server.log:"
                + "read-attribute(name=stream) --file=" + f.getAbsolutePath() + "\n";
        Files.write(batchFile.toPath(), cmd.getBytes());
        try {
            cli.sendLine("run-batch --file=" + batchFile + " -v");
            assertTrue(f.exists() && f.length() != 0);
            assertTrue(f2.exists() && f2.length() != 0);
        } finally {
            batchFile.delete();
            f.delete();
            f2.delete();
            cli.quit();
        }
    }

    @Test
    public void testIf() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "batch_if.log");
        assertFalse(f.exists());
        try {
            cli.sendLine("if (outcome==success) of :read-resource");
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            cli.sendLine("end-if");
            assertTrue(f.exists() && f.length() != 0);
        } finally {
            f.delete();
            cli.quit();
        }
    }

    @Test
    public void testTry() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "try_attachment.log");
        File f2 = new File(f.getAbsolutePath() + "(1)");
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("try");
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            cli.sendLine("finally");
            cli.sendLine("attachment save --operation=/subsystem=logging/log-file=server.log:"
                    + "read-attribute(name=stream) --file=" + f.getAbsolutePath());
            cli.sendLine("end-try");
            assertTrue(f.exists() && f.length() != 0);
            assertTrue(f2.exists() && f2.length() != 0);
        } finally {
            f.delete();
            f2.delete();
            cli.quit();
        }
    }

    @Test
    public void testNoFile() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "nofile_attachment.log");
        assertFalse(f.exists());
        try {
            cli.sendLine("attachment save --operation=:read-resource() --file=" + f.getAbsolutePath());
            assertFalse(f.exists());
        } finally {
            cli.quit();
        }
    }

    @Test
    public void testDisplay() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        try {
            cli.sendLine("attachment display --operation=/subsystem=logging/"
                    + "log-file=server.log:read-attribute(name=stream)");
            String output = cli.readOutput();
            assertTrue(output.contains("jboss.home.dir"));
        } finally {
            cli.quit();
        }
    }

    @Test
    public void testDisplayWrongOptions() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        boolean failed = false;

        try {
            cli.sendLine("attachment display --operation=/subsystem=logging/"
                    + "log-file=server.log:read-attribute(name=stream) --overwrite");
        } catch (Throwable ex) {
            // XXX OK.
            failed = true;
        }

        try {
            cli.sendLine("attachment display --operation=/subsystem=logging/"
                    + "log-file=server.log:read-attribute(name=stream) --file=toto");
        } catch (Throwable ex) {
            // XXX OK.
            failed = true;
        } finally {
            cli.quit();
        }
        if (!failed) {
            throw new Exception("Should have failed");
        }
    }

    @Test
    public void testBatchIncrementalDeployment() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        File f = new File(System.currentTimeMillis() + "batch_attachment.log");
        File f2 = new File(System.currentTimeMillis() + "batch_attachment2.log");
        File source = new File(System.currentTimeMillis() + "todeploy.log");
        String expected = "HelloWorld";
        Files.write(source.toPath(), expected.getBytes());
        assertFalse(f.exists());
        assertFalse(f2.exists());
        try {
            cli.sendLine("/deployment=AttachedFileTestCase.war:add(content=[{empty=true}])");
            cli.sendLine("batch");
            cli.sendLine("/deployment=AttachedFileTestCase.war:add-content(content=[{input-stream-index="
                    + source.getName() + ", target-path=batch.text.file1}])");
            cli.sendLine("/deployment=AttachedFileTestCase.war:add-content(content=[{input-stream-index="
                    + source.getName() + ", target-path=batch.text.file2}])");
            cli.sendLine("attachment save --operation=/deployment=AttachedFileTestCase.war:"
                    + "read-content(path=batch.text.file1) --file=" + f.getAbsolutePath());
            cli.sendLine("attachment save --operation=/deployment=AttachedFileTestCase.war:"
                    + "read-content(path=batch.text.file2) --file=" + f2.getAbsolutePath());
            cli.sendLine("run-batch");
            assertTrue(f.exists() && f.length() != 0);
            assertTrue(Files.readAllLines(f.toPath()).get(0).equals(expected));
            assertTrue(f2.exists() && f2.length() != 0);
            assertTrue(Files.readAllLines(f2.toPath()).get(0).equals(expected));
        } finally {
            f.delete();
            f2.delete();
            source.delete();
            cli.sendLine("undeploy AttachedFileTestCase.war");
            cli.quit();
        }
    }
}
