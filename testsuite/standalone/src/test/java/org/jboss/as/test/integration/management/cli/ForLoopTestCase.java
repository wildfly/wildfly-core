/*
Copyright 2017 Red Hat, Inc.

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

import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class ForLoopTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testSimpleFor() throws Exception {
        addProperty("prop1", "prop1_a");
        addProperty("prop2", "prop2_a");
        try {
            cli.sendLine("set var=+");
            cli.sendLine("for propName in :read-children-names(child-type=system-property");
            cli.sendLine("set var=$var+$propName");
            cli.sendLine("done");
            cli.sendLine("echo $var");
            String line = cli.readOutput();
            assertTrue(line, line.equals("++prop1+prop2"));
        } finally {
            cli.sendLine("set var=");
            removeProperties();
        }
    }

    @Test
    public void testDiscard() throws Exception {
        addProperty("prop1", "prop1_a");
        addProperty("prop2", "prop2_a");
        try {
            cli.sendLine("set var=+");
            cli.sendLine("for propName in :read-children-names(child-type=system-property");
            cli.sendLine("set var=$var+$propName");
            cli.sendLine("done --discard");
            cli.sendLine("echo $var");
            String line = cli.readOutput();
            assertTrue(line, line.equals("+"));
        } finally {
            cli.sendLine("set var=");
            removeProperties();
        }
    }

    @Test
    public void testVarAlreadyExists() throws Exception {
        cli.sendLine("set var=+");
        try {
            assertFalse(cli.sendLine("for var in :read-children-names(child-type=system-property", true));
        } finally {
            cli.sendLine("set var=");
        }
    }

    @Test
    public void testInvalidNestedOp() throws Exception {
        cli.sendLine("for var in :read-children-names(child-type=system-property", true);
        try {
            assertFalse(cli.sendLine("for var in :read-children-names(child-type=system-property", true));
        } finally {
            cli.sendLine("done");
        }
    }

    @Test
    public void testInvalidOps() throws Exception {
        assertFalse(cli.sendLine("for var in :read-wrong", true));
        assertFalse(cli.sendLine("for var in", true));
        assertFalse(cli.sendLine("for var", true));
        assertFalse(cli.sendLine("for", true));
    }

    private void removeProperties() {
        cli.sendLine("for propName in :read-children-names(child-type=system-property");
        cli.sendLine("/system-property=$propName:remove");
        cli.sendLine("done");
        checkEmpty();
    }

    private void checkEmpty() {
        cli.sendLine("if (result==[]) of :read-children-names(child-type=system-property");
        cli.sendLine("echo EMPTY");
        cli.sendLine("else");
        cli.sendLine("echo FAILURE");
        cli.sendLine("end-if");
        String line = cli.readOutput();
        assertTrue(line, line.contains("EMPTY") && !line.contains("FAILURE"));
    }

    @Test
    public void testForNobatch() throws Exception {
        cli.sendLine("batch");
        try {
            assertFalse(cli.sendLine("for propName in :read-children-names(child-type=system-property", true));
        } finally {
            cli.sendLine("discard-batch");
        }

    }

    @Test
    public void testForBatch() throws Exception {
        addProperty("prop1", "prop1_a");
        addProperty("prop2", "prop2_a");
        try {
            cli.sendLine("for propName in :read-children-names(child-type=system-property");
            cli.sendLine("batch");
            cli.sendLine("/system-property=$propName:remove");
            cli.sendLine("run-batch");
            cli.sendLine("done");
            checkEmpty();
        } finally {
            removeProperties();
        }
    }

    @Test
    public void testForIf() throws Exception {
        addProperty("prop1", "prop1_a");
        addProperty("prop2", "prop1_a");
        addProperty("prop3", "prop1_a");
        addProperty("prop4", "prop1_a");
        addProperty("prop5", "prop1_a");
        try {
            cli.sendLine("for propName in :read-children-names(child-type=system-property");
            cli.sendLine("if (result!=[]) of :read-children-names(child-type=system-property");
            cli.sendLine("set foo=bar");
            cli.sendLine("echo $foo");
            cli.sendLine("/system-property=$propName:remove");
            cli.sendLine("end-if");
            cli.sendLine("done");
            checkEmpty();
        } finally {
            removeProperties();
        }
    }

    private void addProperty(String name, String value) {
        cli.sendLine("/system-property=" + name + ":add(value=" + value + ")");
    }
}
