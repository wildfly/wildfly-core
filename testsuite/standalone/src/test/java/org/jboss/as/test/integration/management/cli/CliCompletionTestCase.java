/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.aesh.complete.AeshCompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.hamcrest.CoreMatchers;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class CliCompletionTestCase {

    private static CommandContext ctx;

    @ClassRule
    public static final TemporaryFolder temporaryDir = new TemporaryFolder();
    /**
     * Initialize CommandContext before all tests
     */
    @BeforeClass
    public static void init() throws Exception {
        ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
    }

    /**
     * Terminate CommandContext after all tests are executed
     */
    @AfterClass
    public static void close() {
        ctx.terminateSession();
    }

    @Test
    public void reloadWithSpaceTest() {
        for (List<String> candidates : getCandidatesLists("reload ", null)) {
            assertTrue(candidates.contains("--start-mode"));
            assertFalse(candidates.contains("--admin-only"));
        }
    }

    @Test
    public void reloadWithArgumentTest() {
        for (List<String> candidates : getCandidatesLists("reload --start-mode=", false)) {
            assertTrue(candidates.toString(), candidates.size() == 3);
            assertEquals(candidates.toString(), Arrays.asList("admin-only",
                    "normal", "suspend"), candidates);
        }
    }

    @Test
    public void operationWithObjectAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem=elytron/token-realm=JwtRealm:add(jwt={}", false)) {
            assertEquals(candidates.toString(), Arrays.asList(","), candidates);
        }
    }

    @Test
    public void complexOperationWithObjectAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem=logging/logger=cdsc:add(category=cdsc,"
                + "filter={accept=true,all={},change-level=ALL,not={},"
                + "level-range={min-level=ALL,max-level=ALL,"
                + "max-inclusive=true,min-inclusive=false}", false)) {
            assertEquals(candidates.toString(), Arrays.asList(","), candidates);
        }
    }
    /**
     * Checks CLI completion for "read-attribute " command
     */
    @Test
    public void readAttributeWithSpaceTest() {
        for (List<String> candidates : getCandidatesLists("read-attribute ", null)) {
            assertTrue(candidates.contains("--resolve-expressions"));
            assertTrue(candidates.contains("management-major-version"));
            assertFalse(candidates.contains("--admin-only"));
        }
    }
    /**
     * Checks CLI completion for "read-a" command
     */
    @Test
    public void readAttributeWithUnfinishedCmdTest() {
        for (List<String> candidates : getCandidatesLists("read-a", true)) {
            assertTrue(candidates.contains("read-attribute"));
        }
    }
    /**
     * Checks CLI completion for "read-attribute --" command
     */
    @Test
    public void readAttributeWithUnfinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("read-attribute --", false)) {
            assertTrue(candidates.contains("--resolve-expressions"));
            assertTrue(candidates.contains("--verbose"));
        }
    }

    @Test
    public void readAttributeAfterSystemPropertyTest() throws CommandLineException {
        CommandContext ctx = CLITestUtil
                .getCommandContext(TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(), System.in,
                        System.out);
        ctx.connectController();
        ctx.handle("cd /system-property");
        try {
            String cmd = "read-attribute ";
            List candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd, cmd.length(), candidates);
            candidates = complete(ctx, cmd, null);
            assertTrue(candidates.contains("--verbose"));
        } finally {
            ctx.terminateSession();
        }
    }
    /**
     * Checks CLI completion for "help" command
     */
    @Test
    public void helpTest() {
        for (List<String> candidates : getCandidatesLists("help", true)) {
            assertTrue(candidates.toString(), candidates.contains("help"));
        }
    }

    /**
     * Checks CLI completion for "help --" command
     */
    @Test
    public void helpWithUnifinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("help --", true)) {
            assertTrue(candidates.toString(), candidates.contains("--commands"));
        }
    }

    /**
     * Checks CLI completion for "help l" command
     */
    @Test
    public void helpWithLCharTest() {
        for (List<String> candidates : getCandidatesLists("help l", true)) {
            assertTrue(candidates.toString(), candidates.contains("ls"));
            assertTrue(candidates.toString(), candidates.contains("list-batch"));
        }
    }

    /**
     * Checks CLI completion for "help ls" command
     */
    @Test
    public void helpWithLsTest() {
        for (List<String> candidates : getCandidatesLists("help ls", true)) {
            assertTrue(candidates.toString(), candidates.contains("ls"));
        }
    }

    /**
     * Checks CLI completion for "help :" command
     */
    @Test
    public void helpWithColonTest() {
        for (List<String> candidates : getCandidatesLists("help :", true)) {
            assertTrue(candidates.toString(), candidates.contains("read-resource"));
        }
    }

    /**
     * Checks CLI completion for "help deployment " command
     */
    @Test
    public void helpWithDeploymentTest() {
        for (List<String> candidates : getCandidatesLists("help deployment ", true)) {
                assertTrue(candidates.toString(), candidates.contains("deploy-file"));
        }
    }

    /**
     * Checks CLI completion for "patch" command
     */
    @Test
    public void patchTest() {
        for (List<String> candidates : getCandidatesLists("patch", true)) {
            assertTrue(candidates.toString(), candidates.isEmpty());
        }
    }

    /**
     * Checks CLI completion for "pat" command
     */
    @Test
    public void patchDoNotCompleteTest() {
        for (List<String> candidates : getCandidatesLists("pat", true)) {
            assertTrue(candidates.toString(), candidates.isEmpty());
        }
    }


    /**
     * Checks CLI completion for "patch in" command
     */
    @Test
    public void patchInfoDoNotCompleteTest() {
        for (List<String> candidates : getCandidatesLists("patch in", true)) {
            assertTrue(candidates.toString(), candidates.isEmpty());
        }
    }

    @Test
    public void deployTest() {
        String cmd = "deploy";
        List<String> candidates = oldWayCompletion(cmd);
        assertTrue(candidates.toString(), candidates.size() == 4);
        assertEquals(candidates.toString(), Arrays.asList("deploy",
                "deployment", "deployment-info", "deployment-overlay"),
                candidates);
        candidates = complete(ctx, cmd, true);
        assertTrue(candidates.toString(),
                candidates.size() == 4);
        // Sorting of candidates is done in the aesh display layer
        assertTrue(candidates.toString(), candidates.contains("deploy"));
        assertTrue(candidates.toString(), candidates.contains("deployment"));
        assertTrue(candidates.toString(), candidates.contains("deployment-info"));
        assertTrue(candidates.toString(), candidates.contains("deployment-overlay"));
    }

    @Test
    public void deploymentTest() {
        String cmd = "deployment";
        List<String> candidates = oldWayCompletion(cmd);
        assertTrue(candidates.toString(), candidates.size() == 3);
        assertEquals(candidates.toString(), Arrays.asList(
                "deployment", "deployment-info", "deployment-overlay"),
                candidates);
        candidates = complete(ctx, cmd, true);
        assertTrue(candidates.toString(),
                candidates.size() == 3);
        // Sorting of candidates is done in the aesh display layer
        assertTrue(candidates.toString(), candidates.contains("deployment"));
        assertTrue(candidates.toString(), candidates.contains("deployment-info"));
        assertTrue(candidates.toString(), candidates.contains("deployment-overlay"));

        testDeployAction("deploy-file", true);
        testDeployAction("deploy-url", false);
        testDisableAction("disable-all");
        testDisableAction("disable foo");
        testEnableAction("enable-all");
        testEnableAction("enable foo");
        testDeploymentInfo();
        testUndeploy();
        testList();
    }

    private void testDeployAction(String action, boolean unmanaged) {
        String cmd = "deployment " + action + " foo ";
        List<String> candidates = complete(ctx, cmd, null);
        assertFalse(candidates.toString(), candidates.contains("--all-server-groups"));
        assertFalse(candidates.toString(), candidates.contains("--server-groups="));
        assertTrue(candidates.toString(), candidates.contains("--disabled"));
        assertTrue(candidates.toString(), candidates.contains("--enabled"));
        assertTrue(candidates.toString(), candidates.contains("--replace"));
        assertTrue(candidates.toString(), candidates.contains("--name="));
        assertTrue(candidates.toString(), candidates.contains("--runtime-name="));
        if (unmanaged) {
            assertTrue(candidates.toString(), candidates.contains("--unmanaged"));
        }
    }

    private void testDisableAction(String action) {
        String cmd = "deployment " + action + " ";
        List<String> candidates = complete(ctx, cmd, null);
        assertFalse(candidates.toString(), candidates.contains("--all-relevant-server-groups"));
        assertFalse(candidates.toString(), candidates.contains("--server-groups="));
    }

    private void testEnableAction(String action) {
        String cmd = "deployment " + action + " ";
        List<String> candidates = complete(ctx, cmd, null);
        assertFalse(candidates.toString(), candidates.contains("--all-server-groups"));
        assertFalse(candidates.toString(), candidates.contains("--server-groups="));
    }

    private void testDeploymentInfo() {
        String cmd = "deployment info ";
        List<String> candidates = complete(ctx, cmd, null);
        assertFalse(candidates.toString(), candidates.contains("--server-group="));
    }

    private void testUndeploy() {
        String cmd = "deployment undeploy foo ";
        List<String> candidates = complete(ctx, cmd, null);
        assertFalse(candidates.toString(), candidates.contains("--all-relevant-server-groups"));
        assertFalse(candidates.toString(), candidates.contains("--server-groups="));
    }

    private void testList() {
        String cmd = "deployment list ";
        List<String> candidates = complete(ctx, cmd, null);
        assertTrue(candidates.toString(), candidates.contains("--l"));
    }

    @Test
    public void subsystemTest() {
        for (List<String> candidates : getCandidatesLists("/subsystem", false)) {
                assertTrue(candidates.toString(), candidates.contains("="));
        }
    }

    @Test
    public void operationWithCharAsAttributeTest() {
        for (List<String> candidates : getCandidatesLists(":read-resource(p", false)) {
                assertTrue(candidates.toString(), candidates.contains("proxies"));
        }
    }

    @Test
    public void connectTest() {
        for (List<String> candidates : getCandidatesLists("connect ", true)) {
                assertTrue(candidates.toString(), candidates.contains("--bind="));
        }
    }

    @Test
    public void connectWithUnifinishedArgumentTest() {
        for (List<String> candidates : getCandidatesLists("connect --", true)) {
                assertTrue(candidates.toString(), candidates.contains("--bind="));
        }
    }

    @Test
    public void testDeploymentAdd() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        String op = "/deployment=toto:add(content=";
        try {
            {
                String cmd = op + "[{";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "bytes*", "empty*", "hash*",
                        "input-stream-index*", "path*", "relative-to", "url*"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive", "bytes*", "empty*", "hash*",
                        "input-stream-index*", "path*", "relative-to", "url*"), candidates);
            }

            {
                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                File parent = f.getParentFile();
                String cmd = op + "[{input-stream-index=" + escapePath(parent.getAbsolutePath() + File.separator + prefix);
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(f.getName()), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList(f.getName()), candidates);
            }

            {
                String prefix = System.currentTimeMillis() + "cliCompletionTest";
                File f = File.createTempFile(prefix, null);
                f.deleteOnExit();
                String cmd = op + "[{input-stream-index=" + escapePath(f.getAbsolutePath());
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                String cmd = op + "[{path=xxx,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive*", "relative-to"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{relative-to=xxx,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive", "path*"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive", "path*"), candidates);
            }

            {
                String cmd = op + "[{archive=true,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("empty*", "hash*", "path*", "relative-to"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("empty*", "hash*", "path*", "relative-to"), candidates);
            }

            {
                String cmd = op + "[{empty=true";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList(","), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList(","), candidates);
            }

            {
                String cmd = op + "[{empty=true,";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("archive"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("archive"), candidates);
            }

            {
                String cmd = op + "[{empty=true,archive=true";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("}"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("}"), candidates);
            }

            {
                // If the prefix of a property name is typed,
                // the property name (if it exists, hidden or not) is proposed.
                String cmd = op + "[{empty=true,archive=true,i";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(Arrays.asList("input-stream-index"), candidates);
                candidates = complete(ctx, cmd, false);
                assertEquals(Arrays.asList("input-stream-index"), candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void operatorAppendArgumentCompletion() throws Exception {
            {
                String cmd = "version >>";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "version >> ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }
    }

    @Test
    public void operatorPipeArgumentCompletion() throws Exception {
            {
                String cmd = "version |";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("grep"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("grep"));
            }

            {
                String cmd = "version | ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("grep"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("grep"));
            }
    }

    @Test
    public void operatorRedirectArgumentCompletion() throws Exception {
            {
                String cmd = "version >";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "version > ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }
    }

    @Test
    public void testAfterPipeCommandCompletion() throws Exception {
            String cmd = "echo /subsystem=elytron | l";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx,
                    cmd, cmd.length(), candidates);
            assertFalse(candidates.toString(), candidates.isEmpty());
            candidates = complete(ctx, cmd, null);
            assertFalse(candidates.toString(), candidates.isEmpty());
    }

    @Test
    public void testAfterPipeOperationCompletion() throws Exception {
            String cmd = "echo /subsystem=elytron | :";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx,
                    cmd, cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.isEmpty());
            candidates = complete(ctx, cmd, null);
            assertTrue(candidates.toString(), candidates.isEmpty());
    }

    @Test
    public void testAppendCustomFileRelativeDirCompletion() throws Exception {
        Path filePath = Files.createTempFile("tempFile", ".tmp");
        String tempFileStringPath = filePath.getFileName().toString();
        try {
            ctx.handle("version >" + filePath.toString());
            {
                String cmd = "version >> " + filePath.getParent() + File.separator;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(tempFileStringPath));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains(tempFileStringPath));
            }

            {
                String cmd = "version >> " + filePath.getParent() + File.separator;
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains(tempFileStringPath));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains(tempFileStringPath));
            }
        } finally {
            Files.delete(filePath);
        }
    }

    @Test
    public void testAppendCustomFileAbsoluteDirCompletion() throws Exception {
        Path tempFile = Files.createTempFile("tempFile", ".tmp");
        String tempFileStringPath = tempFile.toString();
        try {
            ctx.handle("version >" + tempFileStringPath);
            List<String> paths = new ArrayList<>();

            // Split the absolute path. First item must be the root directory (for X:\ on Windows)
            paths.add(tempFile.toAbsolutePath().getRoot().toString());
            for (int i=0; i < tempFile.toAbsolutePath().getNameCount(); i++) {
                paths.add(tempFile.toAbsolutePath().getName(i).toString());
            }

            String cmd = "version >>";
            String testPath = "";
            List<String> candidates;
            // Iterate through whole path except the last element which is the filename itself
            for (int i = 0; i < paths.size() - 1; i++) {
                String p = paths.get(i);
                if (i == 0 || (i == paths.size() - 1)) {
                    testPath += p;
                } else {
                    testPath += p + File.separator;
                }

                candidates = complete(ctx, cmd + testPath, null);
                // Candidate should be directory
                if (i < tempFile.toAbsolutePath().getNameCount() - 1) {
                    assertThat("No candidate for completion match with the expected content.",
                            candidates, CoreMatchers.hasItem(paths.get(i+1) + File.separator));
                // Candidate should be the final filename
                } else {
                    assertThat("No candidate for completion match with the expected content.",
                            candidates, CoreMatchers.hasItem((paths.get(paths.size()-1))));
                }
            }
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    public void testGrepParametersCompletion() throws Exception {
        Set<String> expectedParameters = new HashSet<>(Arrays.asList("--only-matching", "--help", "--ignore-case", "--count", "--line-number"));
        String cmd = "grep --";
        List<String> candidates = new ArrayList<>();
        ctx.getDefaultCommandCompleter().complete(ctx, cmd, cmd.length(), candidates);
        assertEquals(expectedParameters, candidates.stream().map(String::toString).collect(Collectors.toSet()));
        candidates = complete(ctx, cmd, null);
        assertEquals(expectedParameters, candidates.stream().map(String::toString).collect(Collectors.toSet()));
    }

    private static void cleanupSecurityCompletion(CommandContext ctx, boolean roleDecoder, boolean fsRealm, boolean ksRealm, boolean ks) throws Exception {
        Exception ex = null;
        if (roleDecoder) {
            try {
                ctx.handle("/subsystem=elytron/simple-role-decoder=from-roles-attribute:remove");
            } catch (Exception e) {
                ex = e;
            }
        }
        if (fsRealm) {
            try {
                ctx.handle("/subsystem=elytron/filesystem-realm=foo-fs-realm:remove");
            } catch (Exception e) {
                if (ex == null) {
                    ex = e;
                } else {
                    ex.addSuppressed(e);
                }
            }
        }
        if (ksRealm) {
            try {
                ctx.handle("/subsystem=elytron/key-store-realm=koko:remove");
            } catch (Exception e) {
                if (ex == null) {
                    ex = e;
                } else {
                    ex.addSuppressed(e);
                }
            }
        }
        if (ks) {
            try {
                ctx.handle("/subsystem=elytron/key-store=foo:remove");
            } catch (Exception e) {
                if (ex == null) {
                    ex = e;
                } else {
                    ex.addSuppressed(e);
                }
            }
        }
        if (ex != null) {
            throw ex;
        }
    }

    @Test
    public void securityCommandsCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        // add a key-store.
        ctx.handle("/subsystem=elytron/key-store=foo:add(type=JKS, credential-reference={clear-text=secret})");
        try {
            ctx.handle("/subsystem=elytron/key-store-realm=koko:add(key-store=foo)");
        } catch (Exception ex) {
            cleanupSecurityCompletion(ctx, false, false, false, true);
            throw ex;
        }
        // add a file system realm and decoder
        try {
            ctx.handle("/subsystem=elytron/filesystem-realm=foo-fs-realm:add(path="
                    + escapePath(temporaryDir.newFolder("identities").getAbsolutePath()));
        } catch (Exception ex) {
            cleanupSecurityCompletion(ctx, false, false, true, true);
            throw ex;
        }
        try {
            ctx.handle("/subsystem=elytron/simple-role-decoder=from-roles-attribute:add(attribute=Roles)");
        } catch (Exception ex) {
            cleanupSecurityCompletion(ctx, false, true, true, true);
            throw ex;
        }
        try {

            {
                String cmd = "security ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("disable-http-auth-management",
                        "disable-sasl-management",
                        "disable-ssl-management",
                        "enable-http-auth-management",
                        "enable-sasl-management",
                        "enable-ssl-management", "reorder-sasl-management");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--interactive", "--key-store-name=", "--key-store-path=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // --interactive completion
            {
                String cmd = "security enable-ssl-management --interactive ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--lets-encrypt", "--management-interface=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.contains(Util.HTTP_INTERFACE));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.contains(Util.HTTP_INTERFACE));
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(!candidates.contains("--http-secure-socket-binding="));
                candidates = complete(ctx, cmd, null);
                assertTrue(!candidates.contains("--http-secure-socket-binding="));
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=" + Util.HTTP_INTERFACE + " ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.contains("--http-secure-socket-binding="));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.contains("--http-secure-socket-binding="));
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--lets-encrypt", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=foo --no-reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--lets-encrypt");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=foo --no-reload --lets-encrypt ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--ca-account=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --interactive --management-interface=foo --no-reload --lets-encrypt --ca-account=foo";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList();
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // key-store-name completion
            {
                String cmd = "security enable-ssl-management --key-store-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo");
                assertTrue(candidates.toString(), candidates.contains("foo"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("foo"));
            }

            {
                String cmd = "security enable-ssl-management --key-store-name=ccc ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--management-interface=",
                        "--new-key-manager-name=", "--new-ssl-context-name=",
                        "--no-reload", "--trust-store-name=", "--trusted-certificate-path=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // key-store-path completion
            {
                String cmd = "security enable-ssl-management --key-store-path=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-ssl-management --key-store-path=ccc ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--key-store-password=",
                        "--key-store-path-relative-to=",
                        "--key-store-type=",
                        "--management-interface=",
                        "--new-key-manager-name=",
                        "--new-key-store-name=",
                        "--new-ssl-context-name=",
                        "--no-reload", "--trust-store-name=", "--trusted-certificate-path=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --key-store-path=ccc --key-store-type=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("JKS", "PKCS12");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // client certificate completion
            {
                String cmd = "security enable-ssl-management --key-store-name=ccc "
                        + "--management-interface=ccsd --new-key-manager-name=ccsd "
                        + "--new-ssl-context-name=cdscs --no-reload ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--trust-store-name=", "--trusted-certificate-path=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // client certificate completion
            {
                String cmd = "security enable-ssl-management --key-store-name=ccc "
                        + "--management-interface=ccsd --new-key-manager-name=ccsd "
                        + "--new-ssl-context-name=cdscs --no-reload --trust-store-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo");
                assertTrue(candidates.toString(), candidates.contains("foo"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("foo"));
            }

            {
                String cmd = "security enable-ssl-management --key-store-name=ccc "
                        + "--management-interface=ccsd --new-key-manager-name=ccsd "
                        + "--new-ssl-context-name=cdscs --no-reload --trust-store-name=cdcds ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--new-trust-manager-name=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-ssl-management --key-store-name=ccc "
                        + "--management-interface=ccsd --new-key-manager-name=ccsd --new-ssl-context-name=cdscs "
                        + "--no-reload --trusted-certificate-path=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-ssl-management --key-store-name=ccc "
                        + "--management-interface=ccsd --new-key-manager-name=ccsd --new-ssl-context-name=cdscs "
                        + "--no-reload --trusted-certificate-path=cdscsd ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--new-trust-manager-name=",
                        "--new-trust-store-name=", "--no-trusted-certificate-validation",
                        "--trust-store-file-name=", "--trust-store-file-password=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security disable-ssl-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--management-interface=",
                        "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            // Authentication
            {
                String cmd = "security enable-http-auth-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--mechanism=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("BASIC", "CLIENT_CERT",
                        "DIGEST", "DIGEST-SHA-256", "EXTERNAL", "FORM");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=CLIENT_CERT ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--key-store-name=",
                        "--key-store-realm-name=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=CLIENT_CERT "
                        + "--key-store-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo");
                assertTrue(candidates.toString(), candidates.contains("foo"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("foo"));
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=CLIENT_CERT "
                        + "--key-store-name=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--new-auth-factory-name=",
                        "--new-realm-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=CLIENT_CERT "
                        + "--key-store-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("koko");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=CLIENT_CERT "
                        + "--key-store-realm-name=koko ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--file-system-realm-name=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--properties-realm-name=",
                        "--user-properties-file=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--file-system-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo-fs-realm");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--file-system-realm-name=foo-fs-realm ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--user-role-decoder=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--file-system-realm-name=foo-fs-realm --user-role-decoder=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("from-roles-attribute", "groups-to-roles");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=DIGEST-MD5 "
                        + "--properties-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("ApplicationRealm", "ManagementRealm");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=DIGEST-MD5 "
                        + "--properties-realm-name=ApplicationRealm ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--user-properties-file=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--user-properties-file=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--group-properties-file=",
                        "--new-auth-factory-name=",
                        "--new-realm-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--plain-text",
                        "--relative-to=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-http-auth-management --mechanism=BASIC "
                        + "--user-properties-file=foo --group-properties-file=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security disable-http-auth-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--mechanism=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--mechanism=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-sasl-management --mechanism=EXTERNAL ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--key-store-name=",
                        "--key-store-realm-name=",
                        "--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=EXTERNAL "
                        + "--key-store-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo");
                assertTrue(candidates.toString(), candidates.contains("foo"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("foo"));
            }

            {
                String cmd = "security enable-sasl-management --mechanism=EXTERNAL "
                        + "--key-store-name=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-realm-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=EXTERNAL "
                        + "--key-store-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("koko");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=EXTERNAL "
                        + "--key-store-realm-name=koko ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload", "--roles=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=JBOSS-LOCAL-USER ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList(
                        "--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--super-user");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--file-system-realm-name=",
                        "--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--properties-realm-name=",
                        "--user-properties-file=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--file-system-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("foo-fs-realm");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--file-system-realm-name=foo-fs-realm ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--user-role-decoder=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--file-system-realm-name=foo-fs-realm --user-role-decoder=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("from-roles-attribute", "groups-to-roles");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--properties-realm-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("ApplicationRealm", "ManagementRealm");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--properties-realm-name=ApplicationRealm ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--management-interface=", "--new-auth-factory-name=",
                        "--new-security-domain-name=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--user-properties-file=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--user-properties-file=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--exposed-realm=",
                        "--group-properties-file=",
                        "--management-interface=",
                        "--new-auth-factory-name=",
                        "--new-realm-name=",
                        "--new-security-domain-name=",
                        "--no-reload",
                        "--plain-text",
                        "--relative-to=");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--user-properties-file=foo --group-properties-file=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security enable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--management-interface=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "security disable-sasl-management ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                List<String> res = Arrays.asList("--management-interface=", "--mechanism=", "--no-reload");
                assertEquals(candidates.toString(), res, candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), res, candidates);
            }

            {
                String cmd = "security disable-sasl-management --mechanism=DIGEST-MD5 "
                        + "--management-interface=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertFalse(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertFalse(candidates.toString(), candidates.isEmpty());
            }

        } finally {
            try {
                cleanupSecurityCompletion(ctx, true, true, true, true);
            } finally {
                try {
                    ctx.handle("reload");
                } finally {
                    ctx.terminateSession();
                }
            }
        }
    }

    @Test
    public void echoDMRCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        try {
            {
                String cmd = "echo-dmr ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.contains("--compact"));
                assertTrue(candidates.toString(), candidates.size() > 1);
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.contains("--compact"));
                assertTrue(candidates.toString(), candidates.size() > 1);
            }

            {
                String cmd = "echo-dmr --compact";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--compact "), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("--compact "), candidates);
            }

            {
                String cmd = "echo-dmr --compact ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertFalse(candidates.toString(), candidates.contains("--compact"));
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.size() > 1);
                assertFalse(candidates.toString(), candidates.contains("--compact"));
            }

            {
                String cmd = "echo-dmr version ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertTrue(candidates.toString(), candidates.isEmpty());
                candidates = complete(ctx, cmd, null);
                assertTrue(candidates.toString(), candidates.isEmpty());
            }

            {
                String cmd = "echo-dmr cl";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("clear"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("clear"), candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void genericCommandCompletion() throws Exception {
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        try {

            {
                String cmd = "command add ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--node-child", "--node-type"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("--node-child", "--node-type"), candidates);
            }

            {
                String cmd = "command add --node-child=/foo=bar ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--command-name"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("--command-name"), candidates);
            }

            {
                String cmd = "command add --node-child=/foo=bar --command-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("bar"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("bar"), candidates);
            }

            {
                String cmd = "command add --node-child=/foo=bar --command-name=bar ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Collections.emptyList(), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Collections.emptyList(), candidates);
            }

            {
                String cmd = "command add --node-type=/foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--command-name", "--property-id"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("--command-name", "--property-id"), candidates);
            }

            {
                String cmd = "command add --node-type=/foo --command-name=";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("foo"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("foo"), candidates);
            }

            {
                String cmd = "command add --node-type=/foo --command-name=foo ";
                List<String> candidates = new ArrayList<>();
                ctx.getDefaultCommandCompleter().complete(ctx,
                        cmd, cmd.length(), candidates);
                assertEquals(candidates.toString(), Arrays.asList("--property-id"), candidates);
                candidates = complete(ctx, cmd, null);
                assertEquals(candidates.toString(), Arrays.asList("--property-id"), candidates);
            }
        } finally {
            ctx.terminateSession();
        }
    }

    public static String escapePath(String filePath) {
        if (Util.isWindows()) {
            StringBuilder builder = new StringBuilder();
            for (char c : filePath.toCharArray()) {
                if (c == '\\') {
                    builder.append('\\');
                }
                builder.append(c);
            }
            return builder.toString();
        } else {
            return filePath;
        }
    }

    /**
     * Legacy way of CLI completion
     */
    private List<String> oldWayCompletion(String cmd) {
        List<String> candidates = new ArrayList<>();
        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                cmd.length(), candidates);
        return candidates;
    }

    /**
     * Return two lists with candidates for CLI completion. Each list of candidates is generated by different way.
     */
    private List<List<String>> getCandidatesLists(String cmd, Boolean separator) {
        List<List<String>> candidatesLists = new ArrayList<>();

        // old way completion
        List<String> candidates1 = oldWayCompletion(cmd);

        // aesh-readline completion
        List<String> candidates2 = complete(ctx, cmd, separator);

        candidatesLists.add(candidates1);
        candidatesLists.add(candidates2);
        return candidatesLists;
    }

    // This completion is what aesh-readline completion is calling, so more
    // similar to interactive CLI session
    private List<String> complete(CommandContext ctx, String cmd, Boolean separator) {
        Completion<AeshCompleteOperation> completer
                = (Completion<AeshCompleteOperation>) ctx.getDefaultCommandCompleter();
        AeshCompleteOperation op = new AeshCompleteOperation(cmd, cmd.length());
        completer.complete(op);
        if (separator != null) {
            assertEquals(op.hasAppendSeparator(), separator);
        }
        List<String> candidates = new ArrayList<>();
        for (TerminalString ts : op.getCompletionCandidates()) {
            candidates.add(ts.getCharacters());
        }
        // aesh-readline does sort the candidates prior to display.
        Collections.sort(candidates);
        return candidates;
    }
}
