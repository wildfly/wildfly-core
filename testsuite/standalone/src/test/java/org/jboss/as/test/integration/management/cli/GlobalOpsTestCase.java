/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class GlobalOpsTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        cli.sendLine("reload");
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testReadResource() throws Exception {
        testReadResource(false);
    }

    @Test
    public void testReadResourceRecursive() throws Exception {
        testReadResource(true);
    }

    private void testReadResource(boolean recursive) throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-resource(recursive="+ String.valueOf(recursive) +",include-runtime=true)");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?,?> map = (Map<?,?>) result.getResult();

        assertEquals("org.jboss.as.logging", map.get("module").toString());

        assertTrue(map.containsKey("subsystem"));
        Map<?,?> subsystem = (Map<?, ?>) map.get("subsystem");
        assertTrue(subsystem.containsKey("logging"));

        if (recursive) {
            assertTrue(subsystem.get("logging") instanceof Map);
            Map<?,?> logging = (Map<?,?>) subsystem.get("logging");
            assertTrue(logging.containsKey("management-major-version"));
            assertTrue(logging.containsKey("management-minor-version"));
            assertTrue(logging.containsKey("management-micro-version"));
            assertTrue(logging.containsKey("xml-namespaces"));
        } else {
            assertTrue(subsystem.get("logging").equals("undefined"));
        }
    }

    @Test
    public void testReadAttribute() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-attribute(name=module)");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult().equals("org.jboss.as.logging"));

    }

    @Test
    public void testReadResourceDescription() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-resource-description");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?,?> map = (Map<?,?>) result.getResult();

        assertTrue(map.containsKey("description"));
        assertTrue(map.containsKey("attributes"));
        assertTrue(map.containsKey("operations"));
    }

    @Test
    public void testReadOperationNames() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-operation-names");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof List);
        List<?> names = (List<?>) result.getResult();

        assertTrue(names.contains("add"));
        assertTrue(names.contains("read-attribute"));
        assertTrue(names.contains("read-children-names"));
        assertTrue(names.contains("read-children-resources"));
        assertTrue(names.contains("read-children-types"));
        assertTrue(names.contains("read-operation-description"));
        assertTrue(names.contains("read-operation-names"));
        assertTrue(names.contains("read-resource"));
        assertTrue(names.contains("read-resource-description"));
        assertTrue(names.contains("remove"));
        assertTrue(names.contains("undefine-attribute"));
        assertTrue(names.contains("write-attribute"));

    }

    @Test
    public void testReadOperationDescription() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-operation-description(name=add)");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?,?> map = (Map<?,?>) result.getResult();

        assertTrue(map.containsKey("operation-name"));
        assertTrue(map.containsKey("description"));
        assertTrue(map.containsKey("request-properties"));
    }

    @Test
    public void testReadChildrenTypes() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-children-types");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof List);
        List<?> types = (List<?>) result.getResult();

        assertEquals(1, types.size());
        assertEquals("subsystem", types.get(0));
    }

    @Test
    public void testReadChildrenNames() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-children-names(child-type=subsystem)");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof List);
        List<?> names = (List<?>) result.getResult();

        assertEquals(1, names.size());
        assertTrue(names.contains("logging"));
    }

    @Test
    public void testReadChildrenResources() throws Exception {
        cli.sendLine("/extension=org.jboss.as.logging:read-children-resources(child-type=subsystem)");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?,?> res = (Map<?,?>) result.getResult();
        assertTrue(res.get("logging") instanceof Map);
    }

    @Test
    public void testAddRemoveOperation() throws Exception {

        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=test:add(port=8181)");
        CLIOpResult result = cli.readAllAsOpResult();
        assertTrue(result.isIsOutcomeSuccess());

        assertTrue(cli.isValidPath("socket-binding-group", "standard-sockets", "socket-binding", "test"));

        cli.sendLine("/socket-binding-group=standard-sockets/socket-binding=test:remove");
        assertFalse(cli.isValidPath("socket-binding-group", "standard-sockets", "socket-binding", "test"));
    }

    @Test
    public void testCompositeOp() throws Exception {
        cli.sendLine("/:composite(steps=[{\"operation\"=>\"read-resource\"}])");
        CLIOpResult result = cli.readAllAsOpResult();

        assertTrue(result.isIsOutcomeSuccess());
        assertTrue(result.getResult() instanceof Map);
        Map<?,?> map = (Map<?,?>) result.getResult();

        assertTrue(map.get("step-1") instanceof Map);

        assertTrue(((Map<?,?>)map.get("step-1")).get("result") instanceof Map);

        assertTrue(((Map<?,?>)((Map<?,?>)map.get("step-1")).get("result")).containsKey("management-major-version"));
    }

    @Test
    public void testStringValueParsing() throws Exception {
        try {
            cli.sendLine("/subsystem=logging/console-handler=TEST-FILTER:add");
            CLIOpResult result = cli.readAllAsOpResult();
            assertTrue(result.isIsOutcomeSuccess());
            cli.sendLine("/subsystem=logging/console-handler=TEST-FILTER:write-attribute(name=filter-spec, value=\"substituteAll(\\\"JBAS\\\",\\\"DUMMY\\\")\")");
            result = cli.readAllAsOpResult();
            assertTrue(result.isIsOutcomeSuccess());
            cli.sendLine("/subsystem=logging/console-handler=TEST-FILTER:read-resource(recursive=true)");
            result = cli.readAllAsOpResult();
            assertTrue(result.isIsOutcomeSuccess());
            Map<String, Object> resource = result.getResultAsMap();
            assertTrue(resource.containsKey("filter-spec"));
            MatcherAssert.assertThat((String) resource.get("filter-spec"), org.hamcrest.CoreMatchers.is("substituteAll(\"JBAS\",\"DUMMY\")"));
        } finally {
            cli.sendLine("/subsystem=logging/console-handler=TEST-FILTER:remove");
        }
    }
}
