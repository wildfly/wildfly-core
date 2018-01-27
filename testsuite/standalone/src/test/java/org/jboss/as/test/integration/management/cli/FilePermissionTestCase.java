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
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    @Ignore("Uncomment when https://issues.jboss.org/browse/WFCORE-3553 is fixed")
    public void testWriteFileIntoDirWithoutWritePermission() throws Exception {

        // This is unix test only
        if (!Util.isWindows()) {

            Set<PosixFilePermission> permsBefore =
                    EnumSet.of(OWNER_READ, OWNER_EXECUTE);

            Path dir = Files.createTempDirectory("tmpDir", PosixFilePermissions.asFileAttribute(permsBefore));
            String dirFullStringPath = dir.toString();

            String cmd = "echo \"aaa\" >>" + dirFullStringPath + "/test";

            cliOut.reset();
            final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
            try {
                ctx.handle(cmd);
                String output = cliOut.toString(StandardCharsets.UTF_8.name());
                assertThat("Wrong results of the command - " + cmd, output,
                        CoreMatchers.containsString("You don't have permission to write into this location."));
            } finally {
                ctx.terminateSession();
                cliOut.reset();
                Files.delete(dir);
            }
        }
    }

    @Test
    @Ignore("Un-ignore when WFCORE-3559 is fixed")
    public void testFileDefaultPermissions() throws Exception {
        // This is unix test only
        if (!Util.isWindows()) {
            CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out);

            String tmpFile = "tmpFile";
            Path path = Paths.get(tmpFile);

            try {
                ctx.handle("echo \"aaa\" >> " + tmpFile);

                Set<PosixFilePermission> set = Files.getPosixFilePermissions(path);
                assertThat("The test file has unexpected permissions: " + PosixFilePermissions.toString(set),
                        PosixFilePermissions.toString(set), CoreMatchers.is(CoreMatchers.equalTo("rw-rw-r--")));
            } finally {
                ctx.terminateSession();
                Files.delete(path);
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
