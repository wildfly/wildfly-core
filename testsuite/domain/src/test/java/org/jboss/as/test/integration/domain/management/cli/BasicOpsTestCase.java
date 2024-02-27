/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management.cli;

import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class BasicOpsTestCase {

    @BeforeClass
    public static void beforeClass() throws Exception {
        CLITestSuite.createSupport(BasicOpsTestCase.class.getSimpleName());
    }

    @AfterClass
    public static void afterClass() throws Exception {
        CLITestSuite.stopSupport();
    }

    @Test
    public void testConnect() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(false, DomainTestSupport.primaryAddress)) {
            assertFalse(cli.isConnected());
            assertTrue(cli.sendConnect(DomainTestSupport.primaryAddress));
            assertTrue(cli.isConnected());
            cli.quit();
        }
    }

    @Test
    public void testDomainSetup() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(false, DomainTestSupport.primaryAddress)) {
            assertFalse(cli.isConnected());

            assertTrue(cli.sendConnect(DomainTestSupport.primaryAddress));
            assertTrue(cli.isConnected());

            // check hosts
            cli.sendLine(":read-children-names(child-type=host)");
            CLIOpResult res = cli.readAllAsOpResult();
            assertTrue(res.getResult() instanceof List);
            List<?> hosts = (List<?>) res.getResult();

            assertTrue(hosts.contains("primary"));
            assertTrue(hosts.contains("secondary"));

            // check servers
            assertTrue(checkHostServers(cli, "primary", new String[]{"main-one", "main-two", "other-one", "reload-one"}));
            assertTrue(checkHostServers(cli, "secondary", new String[]{"main-three", "main-four", "other-two", "reload-two"}));
            cli.quit();
        }

    }

    @Test
    public void testWalkLocalHosts() throws Exception {

        try (CLIWrapper cli = new CLIWrapper(true, DomainTestSupport.primaryAddress)) {
            cli.sendLine("cd /host=primary/server=main-one");
                cli.sendLine("cd /host=primary");
                cli.sendLine("cd server=main-one");
                cli.sendLine("cd core-service=platform-mbean/type=garbage-collector");
                boolean failed = false;
                try {
                    cli.sendLine("cd nonexistent=path");
                } catch (Throwable t) {
                    failed = true;
                }
                assertTrue("should have failed", failed);
                cli.quit();
            }
        }

    @Test
    public void testWalkRemoteHosts() throws Exception {

        try (CLIWrapper cli = new CLIWrapper(true, DomainTestSupport.primaryAddress)) {
            cli.sendLine("cd /host=secondary/server=main-three");
            cli.sendLine("cd /host=secondary");
            cli.sendLine("cd server=main-three");
            cli.sendLine("cd core-service=platform-mbean/type=garbage-collector");
            boolean failed = false;
            try {
                cli.sendLine("cd nonexistent=path");
            } catch (Throwable t) {
                failed = true;
            }
            assertTrue("should have failed", failed);
            cli.quit();
        }

    }

    private boolean checkHostServers(CLIWrapper cli, String host, String[] serverList) throws Exception {
        cli.sendLine("/host=" + host + ":read-children-names(child-type=server-config)");
        CLIOpResult res = cli.readAllAsOpResult();
        assertTrue(res.getResult() instanceof List);
        List<?> servers = (List<?>) res.getResult();

        if (servers.size() != serverList.length) {
            return false;
        }
        for (String server : serverList) {
            if (!servers.contains(server)) {
                return false;
            }
        }

        return true;
    }

}
