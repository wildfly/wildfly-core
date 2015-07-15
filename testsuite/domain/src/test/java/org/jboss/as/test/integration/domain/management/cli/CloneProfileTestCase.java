/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.test.integration.domain.management.cli;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;


/**
 * Test for providing profile cloning ability (runtime (CLI)) to create
 * new profiles based on existing JBoss profiles.
 *
 * Test clone "default" profile to "clone-profile-test-case-default" profile.
 *
 * https://issues.jboss.org/browse/WFCORE-797
 *
 * @author Marek Kopecky <mkopecky@redhat.com>
 */
public class CloneProfileTestCase extends AbstractCliTestBase {

    private static Logger log = Logger.getLogger(CloneProfileTestCase.class);

    private static final String ORIGINAL_PROFILE = "default";

    private static final String NEW_PROFILE = "clone-profile-test-case-default";

    /**
     * Domain configuration
     */
    private static File domainCfg;

    @BeforeClass
    public static void beforeClass() throws Exception {
        DomainTestSupport domainSupport = CLITestSuite.createSupport(CloneProfileTestCase.class.getSimpleName());
        AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);

        File masterDir = new File(domainSupport.getDomainMasterConfiguration().getDomainDirectory());
        domainCfg = new File(masterDir, "configuration"
                + File.separator + "testing-domain-standard.xml");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AbstractCliTestBase.closeCLI();
        CLITestSuite.stopSupport();
    }

    /**
     * Sends command line to CLI, validate and return output.
     *
     * @param line command line
     * @return CLI output
     */
    private String cliRequest(String line, boolean successRequired) {
        log.info(line);
        cli.sendLine(line);
        String output = cli.readOutput();
        if (successRequired) {
            assertTrue("CLI command \"" + line + " doesn't contain \"success\"", output.contains("success"));
        }
        return output;
    }

    private String readFileToString(File file) throws FileNotFoundException {
        return new Scanner(file).useDelimiter("\\Z").next();
    }

    @Test
    public void testProfile() throws FileNotFoundException {
        // get domain configuration
        String domainCfgContent = readFileToString(domainCfg);
        assertFalse("Domain configuration is not initialized correctly.", domainCfgContent.contains(NEW_PROFILE));

        // clone profile
        cliRequest("/profile=" + ORIGINAL_PROFILE + ":clone(to-profile=" + NEW_PROFILE + ")", true);

        // get and check submodules
        String originSubmodules = cliRequest("ls /profile=" + ORIGINAL_PROFILE + "/subsystem", false);
        String newSubmodules = cliRequest("ls /profile=" + NEW_PROFILE + "/subsystem", false);
        assertEquals("New profile has different submodules than origin profile.", originSubmodules, newSubmodules);

        // check domain configuration
        domainCfgContent = readFileToString(domainCfg);
        assertTrue("Domain configuration doesn't contain " + NEW_PROFILE + " profile.", domainCfgContent.contains(NEW_PROFILE));

        // Remove the new profile (WFCORE-808 test)
        cliRequest("/profile=" + NEW_PROFILE + ":remove", true);

        // check domain configuration
        domainCfgContent = readFileToString(domainCfg);
        assertFalse("Domain configuration still contains " + NEW_PROFILE + " profile.", domainCfgContent.contains(NEW_PROFILE));

    }

}
