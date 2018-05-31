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

import org.hamcrest.CoreMatchers;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.hamcrest.MatcherAssert.assertThat;
/**
 * @author kanovotn@redhat.com
 */
public class FilePermissionTestCase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static ByteArrayOutputStream cliOut;

    @BeforeClass
    public static void setup() throws Exception {
        cliOut = new ByteArrayOutputStream();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        cliOut = null;
    }

    @Test
    public void testWriteFileIntoDirWithoutWritePermission() throws Exception {

        // This is unix test only
        if (!Util.isWindows()) {

            Set<PosixFilePermission> permsBefore =
                    EnumSet.of(OWNER_READ, OWNER_EXECUTE);

            Path dir = Files.createTempDirectory("tmpDir", PosixFilePermissions.asFileAttribute(permsBefore));
            String dirFullStringPath = dir.toString();
            String filePath = dirFullStringPath + "/test";

            String cmd = "echo \"aaa\" >>" + filePath;

            expectedException.expect(CommandLineException.class);
            expectedException.expectMessage(filePath + " (Access denied)");

            cliOut.reset();
            final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
            try {
                ctx.handle(cmd);
            } finally {
                ctx.terminateSession();
                cliOut.reset();
                Files.delete(dir);
            }
        }
    }

    @Test
    public void testDefaultOwnership() throws Exception {
        // This is unix test only
        if (!Util.isWindows()) {
            CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out);

            String tmpFile = "tmpFile";
            Path path = Paths.get(tmpFile);

            try {
                ctx.handle("echo \"aaa\" >> " + tmpFile);

                UserPrincipal userPrincipal = Files.getOwner(path);
                assertThat("The test file has unexpected ownership: " + userPrincipal.toString(),
                        userPrincipal.toString(), CoreMatchers.is(CoreMatchers.equalTo(System.getProperty("user.name"))));
            } finally {
                ctx.terminateSession();
                Files.delete(path);
            }
        }
    }
}
