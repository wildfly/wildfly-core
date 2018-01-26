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
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

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
@RunWith(WildflyTestRunner.class)
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
    public void testFileDefaultPermissions() throws Exception {
        // This is unix test only
        if (!Util.isWindows()) {
            CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                    TestSuiteEnvironment.getServerPort(), System.in, System.out);

            String tmpFile = "tmpFile";
            Path path = Paths.get(tmpFile);

            try {
                ctx.connectController();
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
                ctx.connectController();
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
