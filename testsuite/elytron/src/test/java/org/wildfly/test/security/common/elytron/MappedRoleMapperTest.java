/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
public class MappedRoleMapperTest {

    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
    }

    @After
    public void cleanup() throws Exception {
        cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:remove", true);
        cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm2:remove", true);
        cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm3:remove", true);
        cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm4:remove", true);
        cli.sendLine("/system-property=DEPARTMENT:remove", true);
        cli.sendLine("reload", true);
        cli.close();
    }

    @Test
    public void testAddUpdateRemoveMappedRoleMapper() {
        String LIST_REMOVE_ASSERTION_MESSAGE = "Expected to not find \"moderator\" in mapped-role-mapper 'mrm', but was found";

        boolean success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:add(keep-mapped=true, keep-non-mapped=false, role-map=[{from=admin, to=[management,moderator]},{from=user, to=[member]}])", true);
        Assert.assertTrue(success);

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:list-add(name=role-map[1].to,value=\"guest\")", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:read-attribute(name=role-map[1].to)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString( "\"guest\""));

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:list-remove(name=role-map[0].to,value=\"moderator\")", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:read-attribute(name=role-map[0].to)", true);
        Assert.assertTrue(success);
        try {
            MatcherAssert.assertThat(cli.readOutput(), containsString( "\"moderator\""));
            throw new AssertionError(LIST_REMOVE_ASSERTION_MESSAGE);
        } catch (AssertionError e) {
            // Rethrow error if "moderator" was found in list
            if (e.getMessage().equals(LIST_REMOVE_ASSERTION_MESSAGE)) {
                throw e;
            }
        }

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:write-attribute(name=keep-mapped,value=false)", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm:read-attribute(name=keep-mapped)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"result\" => false"));
    }

    @Test
    public void testLegacyParametersPassedToMappedRoleMapper() {
        boolean success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm2:add(role-map={admin=[management,moderator],user=[member]})", true);
        Assert.assertTrue(success);

        success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm3:add(role-map={admin=management})", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("Wrong type for 'to'. Expected [LIST] but was STRING"));
    }

    @Test
    public void testExpressionPassedToMappedRoleMapper() {
        boolean success =
                        cli.sendLine("/system-property=DEPARTMENT:add(value=IT) ", true);
        Assert.assertTrue(success);

        success =
                cli.sendLine(":resolve-expression(expression=\"department is ${DEPARTMENT}\"", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"department is IT\""));

        success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm4:add(role-map={admin=[management,${DEPARTMENT}]})", true);
        Assert.assertTrue(success);

        success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm4:read-resource(resolve-expressions=false)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("expression \"${DEPARTMENT}\""));

        success =
                cli.sendLine("/subsystem=elytron/mapped-role-mapper=mrm4:read-resource(resolve-expressions=true)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"IT\""));
    }
}
