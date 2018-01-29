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
package org.jboss.as.test.integration.domain.management.cli;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Test for enhanced help for CLI
 *
 * https://issues.jboss.org/browse/WFCORE-3220
 *
 * @author Marek Kopecky <mkopecky@redhat.com>
 */
public class HelpTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        CLITestSuite.createSupport(HelpTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AbstractCliTestBase.closeCLI();
        CLITestSuite.stopSupport();
    }

    /**
     * help command works correctly with operation name
     */
    @Test
    public void helpForOperationTest() {
        String cmd = "help :read-resource";
        cli.sendLine(cmd);
        String help = cli.readOutput();

        assertTrue(getErrMsg(cmd, "synopsis"), help.contains("SYNOPSIS"));
        assertTrue(getErrMsg(cmd, "description"), help.contains("DESCRIPTION"));
        assertTrue(getErrMsg(cmd, "options"), help.contains("OPTIONS"));
        assertTrue(getErrMsg(cmd, "return value"), help.contains("RETURN VALUE"));
    }

    /**
     * Generate assert error message. This method is used if some section is missing in help message.
     */
    private String getErrMsg(String cmd, String sectionName) {
        return String.format("Command %s does not have %s section.", cmd, sectionName);
    }
}
