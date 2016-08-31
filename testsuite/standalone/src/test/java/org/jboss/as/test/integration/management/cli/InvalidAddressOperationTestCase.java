/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * The test makes sure the outcome of an operation with a non-existing address
 * is a server response with a failure description and not a CLI error.
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class InvalidAddressOperationTestCase {

    private static Path jbossCliOriginalCopy = Paths.get(TestSuiteEnvironment.getTmpDir(), "tmp-jboss-cli.xml");

    private Path jbossCliXml;
    private boolean replaced;

    @Test
    public void testNoParamsValidationEnabled() throws Exception {
        // validation concerns only the parameter list, not the address, so the validation passes in this case
        assertFailedServerResponseAddress("/subsystem=datasources/bogus=*:read-resource-description", true);
    }

    @Test
    public void testNoParamsValidationDisabled() throws Exception {
        assertFailedServerResponseAddress("/subsystem=datasources/bogus=*:read-resource-description", false);
    }

    @Test
    public void testValidationEnabledAddress() throws Exception {
        assertCliValidationError("/subsystem=datasources/bogus=*:read-resource-description(param=value)", true);
    }

    @Test
    public void testValidationDisabled() throws Exception {
        assertFailedServerResponseAddress("/subsystem=datasources/bogus=*:read-resource-description(param=value)", false);
    }

    @Test
    public void testInvalidOperationNoParamsValidationEnabled() throws Exception {
        // validation concerns only the parameter list, not the address, so the validation passes in this case
        assertFailedServerResponseOperation(":has-to-fail", true);
    }

    @Test
    public void testInvalidOperationNoParamsValidationDisabled() throws Exception {
        assertFailedServerResponseOperation(":has-to-fail", false);
    }

    @Test
    public void testInvalidOperationWithParamsValidationEnabled() throws Exception {
        assertCliValidationError(":has-to-fail(param=value)", true);
    }

    @Test
    public void testInvalidOperationWithParamValidationDisabled() throws Exception {
        assertFailedServerResponseOperation(":has-to-fail(param=value)", false);
    }

    protected void assertCliValidationError(String line, boolean cliRequestValidation) throws IOException {
        try {
            execute(line, cliRequestValidation);
        } catch(CommandLineException e) {
            try {
                ModelNode.fromString(e.getLocalizedMessage());
                fail("received well-formed DMR response");
            } catch(IllegalArgumentException e2) {
                // expected
                assertTrue(e.getLocalizedMessage().startsWith("Failed to get the list of the operation properties"));
            }
        }
    }

    protected void assertFailedServerResponseAddress(String line, boolean cliRequestValidation) throws IOException {
        assertFailedServerResponse(line, cliRequestValidation, "WFLYCTL0030");
    }

    protected void assertFailedServerResponseOperation(String line, boolean cliRequestValidation) throws IOException {
        assertFailedServerResponse(line, cliRequestValidation, "WFLYCTL0031");
    }

    protected void assertFailedServerResponse(String line, boolean cliRequestValidation, String errorCode) throws IOException {
        try {
            execute(line, cliRequestValidation);
        } catch(CommandLineException e) {
            final ModelNode response = ModelNode.fromString(e.getLocalizedMessage());
            assertEquals("failed", response.get("outcome").asString());
            assertTrue(response.get("failure-description").toString().contains(errorCode));
        }
    }

    protected void execute(String line, boolean cliRequestValidation) throws CommandLineException, IOException {
        if(!cliRequestValidation) {
            disableValidation();
        }
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle(line);
            Assert.fail("invalid operation succeeded");
        } finally {
            ctx.terminateSession();
            if(!cliRequestValidation) {
                restoreConfig();
            }
        }
    }

    protected void restoreConfig() throws IOException {
        // restore the original config
        if(replaced && Files.exists(jbossCliOriginalCopy)) {
            Files.copy(jbossCliOriginalCopy, jbossCliXml, StandardCopyOption.REPLACE_EXISTING);
            replaced = false;
        }
        Files.deleteIfExists(jbossCliOriginalCopy);
    }

    protected void disableValidation() throws IOException {
        // make sure validation for operation requests is disabled
        final String jbossDist = TestSuiteEnvironment.getSystemProperty("jboss.dist");
        if(jbossDist == null) {
            fail("jboss.dist system property is not set");
        }
        jbossCliXml = Paths.get(jbossDist, "bin", "jboss-cli.xml");
        if(!Files.exists(jbossCliXml)) {
            fail(jbossCliXml + " doesn't exist.");
        }

        final StringWriter buf = new StringWriter();

        try (final BufferedReader reader = Files.newBufferedReader(jbossCliXml);
                final BufferedWriter writer = new BufferedWriter(buf)) {
            String line = reader.readLine();
            while(line != null) {
                if(!replaced) {
                    final int i = line.indexOf("<validate-operation-requests>true</validate-operation-requests>");
                    if(i >= 0) {
                        line = line.substring(0, i) + "<validate-operation-requests>false</validate-operation-requests>" +
                               line.substring(i + "<validate-operation-requests>true</validate-operation-requests>".length());
                        replaced = true;
                    }
                }
                writer.write(line);
                writer.newLine();
                line = reader.readLine();
            }
        }

        if(!replaced) {
            fail("expected to change the value of validate-operation-requests element");
        }

        Files.copy(jbossCliXml, jbossCliOriginalCopy);
        try {
            writeFile(jbossCliXml, buf.getBuffer().toString());
        } catch(IOException e) {
            Files.copy(jbossCliOriginalCopy, jbossCliXml);
            Files.deleteIfExists(jbossCliOriginalCopy);
        }
    }

    private static void writeFile(Path file, String content) throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(file, Charset.forName("UTF-8"), StandardOpenOption.CREATE)) {
            writer.write(content);
        }
    }
}
