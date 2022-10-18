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
package org.jboss.as.test.integration.domain.management;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.jboss.as.test.integration.domain.management.util.DomainTestUtils.waitUntilState;

import java.io.IOException;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport.Configuration;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Testing that a server failing to start is correctly traced.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
public class ServerStartFailureTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    private static final ModelNode hostPrimary = new ModelNode();
    private static final ModelNode hostSecondary = new ModelNode();
    private static final ModelNode mainOne = new ModelNode();
    private static final ModelNode mainTwo = new ModelNode();
    private static final ModelNode mainThree = new ModelNode();
    private static final ModelNode mainFour = new ModelNode();
    private static final ModelNode otherOne = new ModelNode();
    private static final ModelNode otherTwo = new ModelNode();
    private static final ModelNode otherThree = new ModelNode();
    private static final ModelNode otherFour = new ModelNode();


    static {
        // (host=primary)
        hostPrimary.add("host", "primary");
        // (host=primary),(server-config=main-one)
        mainOne.add("host", "primary");
        mainOne.add("server-config", "main-one");
        // (host=primary),(server-config=main-two)
        mainTwo.add("host", "primary");
        mainTwo.add("server-config", "main-two");
        // (host=primary),(server-config=other-one)
        otherOne.add("host", "primary");
        otherOne.add("server-config", "other-one");
        // (host=primary),(server-config=other-two)
        otherTwo.add("host", "primary");
        otherTwo.add("server-config", "other-two");

        // (host=secondary)
        hostSecondary.add("host", "secondary");
        // (host=secondary),(server-config=main-three)
        mainThree.add("host", "secondary");
        mainThree.add("server-config", "main-three");
        // (host=secondary),(server-config=main-four)
        mainFour.add("host", "secondary");
        mainFour.add("server-config", "main-four");
        // (host=secondary),(server-config=other-three)
        otherThree.add("host", "secondary");
        otherThree.add("server-config", "other-three");
        // (host=secondary),(server-config=other-four)
        otherFour.add("host", "secondary");
        otherFour.add("server-config", "other-four");

    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        Configuration configuration = DomainTestSupport.Configuration.create(ServerStartFailureTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary-failure.xml");
        configuration.getPrimaryConfiguration().addHostCommandLineProperty("-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=n");
        configuration.getSecondaryConfiguration().addHostCommandLineProperty("-agentlib:jdwp=transport=dt_socket,address=9787,server=y,suspend=n");
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        domainPrimaryLifecycleUtil = null;
    }

    @Test
    public void testDomainLifecycleMethods() throws Throwable {

        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        executeLifecycleOperation(client, null, START_SERVERS);
        waitUntilState(client, "primary", "main-one", "STARTED");
        waitUntilState(client, "primary", "main-two", "STARTED");
        waitUntilState(client, "primary", "other-one", "STARTED");
        waitUntilState(client, "secondary", "main-three", "STARTED");
        waitUntilState(client, "secondary", "failure-one", "STARTED");
        waitUntilState(client, "secondary", "failure-two", "FAILED");
        waitUntilState(client, "secondary", "failure-three", "FAILED");

        executeLifecycleOperation(client, null, STOP_SERVERS);
        //When stopped auto-start=true -> STOPPED, auto-start=false -> DISABLED
        waitUntilState(client, "primary", "main-one", "STOPPED");
        waitUntilState(client, "primary", "main-two", "DISABLED");
        waitUntilState(client, "primary", "other-one", "DISABLED");
        waitUntilState(client, "secondary", "main-three", "STOPPED");
        waitUntilState(client, "secondary", "failure-two", "DISABLED");
        waitUntilState(client, "secondary", "failure-three", "DISABLED");

        validateResponse(startServer(client, "primary", "main-one"));
        ModelNode result = validateResponse(startServer(client, "secondary", "failure-two"));
        MatcherAssert.assertThat(result.asString(), is("FAILED"));
        result = validateResponse(startServer(client, "secondary", "failure-three"));
        MatcherAssert.assertThat(result.asString(), is("FAILED"));
    }

    private ModelNode startServer(final ModelControllerClient client, String host, String serverName) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set("start");
        operation.get(OP_ADDR).set("/host=" + host + "/server-config=" + serverName);
        return client.execute(operation);
    }

    private void executeLifecycleOperation(final ModelControllerClient client, String groupName, String opName) throws IOException {
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(opName);
        if (groupName == null) {
            operation.get(OP_ADDR).setEmptyList();
        } else {
            operation.get(OP_ADDR).add(SERVER_GROUP, groupName);
        }
        validateResponse(client.execute(operation));
    }
}
