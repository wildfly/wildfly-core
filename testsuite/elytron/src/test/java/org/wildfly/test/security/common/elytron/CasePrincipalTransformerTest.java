/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.elytron;

import static org.hamcrest.core.StringContains.containsString;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;


@RunWith(WildFlyRunner.class)
public class CasePrincipalTransformerTest {

    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
    }

    @After
    public void cleanup() throws Exception {
        cli.sendLine("reload", true);
        cli.close();
    }

    @Test
    public void testAddUpdateRemoveCasePrincipalTransformer() {
        boolean success =
                cli.sendLine("/subsystem=elytron/case-principal-transformer=my-case-transformer:add()", true);
        Assert.assertTrue(success);

        success = cli.sendLine("/subsystem=elytron/case-principal-transformer=my-case-transformer:write-attribute(name=upper-case,value=false)", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/case-principal-transformer=my-case-transformer:read-attribute(name=upper-case)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"result\" => false"));

        success = cli.sendLine("/subsystem=elytron/case-principal-transformer=my-case-transformer:remove", true);
        Assert.assertTrue(success);
    }

    @Test
    public void testInvalidParametersPassedToCasePrincipalTransformer() {
        boolean success =
                cli.sendLine("/subsystem=elytron/case-principal-transformer=my-case-principal2:add(lower-case=\"\", replacement=\"any\", keep-non-mapped=\"false\", replace-all=\"true\")", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("is not found among the supported properties: [upper-case]"));
    }
}
