/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY_GROUPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies the uses of jboss.server.[base,log,data,temp].dir properties as managed server JMV options. The test uses a
 * preconfigured domain with three server groups with a server on each server group. The above properties are used to
 * configure the host, server-group and server JVM settings. The test validates that these properties can be used and
 * contains the expected values by resolving the property like: '/host=primary/server=server-one:resolve-expression(expression=${my_property})'
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 * @author <a href="mailto:pesilva@redhat.com">Pedro Hos</a>
 */
public class JVMServerPropertiesTestCase {

    protected static final PathAddress PRIMARY_ADDR = PathAddress.pathAddress(HOST, "primary");
    protected static final PathAddress SERVER_CONFIG_ONE_ADDR = PathAddress.pathAddress(SERVER_CONFIG, "server-one");
    protected static final PathAddress SERVER_CONFIG_TWO_ADDR = PathAddress.pathAddress(SERVER_CONFIG, "server-two");
    protected static final PathAddress SERVER_CONFIG_THREE_ADDR = PathAddress.pathAddress(SERVER_CONFIG, "server-three");

    public static final String BY_SERVER = "by-server";
    public static final String BY_TYPE = "by-type";

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil primaryLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        final DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(JVMServerPropertiesTestCase.class.getSimpleName(),
                "domain-configs/domain-jvm-properties.xml",
                "host-configs/host-primary-jvm-properties.xml",
                null
        );

        testSupport = DomainTestSupport.create(configuration);
        primaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        testSupport.start();
        primaryLifecycleUtil.awaitServers(TimeoutUtil.adjust(30 * 1000));
    }

    @AfterClass
    public static void tearDownDomain() {
        testSupport.close();
        testSupport = null;
        primaryLifecycleUtil = null;
    }

    @Test
    public void testServerProperties() throws IOException, MgmtOperationException, InterruptedException, TimeoutException {
        validateProperties("server-one", 8080, BY_SERVER);
        validateProperties("server-two", 8180, BY_SERVER);
        validateProperties("server-three", 8280, BY_SERVER);

        ModelNode op = Util.getWriteAttributeOperation(PRIMARY_ADDR, DIRECTORY_GROUPING, BY_TYPE);
        DomainTestUtils.executeForResult(op, primaryLifecycleUtil.createDomainClient());

        op = Util.createEmptyOperation(RELOAD, PRIMARY_ADDR);
        op.get(RESTART_SERVERS).set(true);
        primaryLifecycleUtil.executeAwaitConnectionClosed(op);
        primaryLifecycleUtil.connect();
        primaryLifecycleUtil.awaitHostController(System.currentTimeMillis());

        DomainClient primaryClient = primaryLifecycleUtil.createDomainClient();
        DomainTestUtils.waitUntilState(primaryClient, PRIMARY_ADDR.append(SERVER_CONFIG_ONE_ADDR), ServerStatus.STARTED.toString());
        DomainTestUtils.waitUntilState(primaryClient, PRIMARY_ADDR.append(SERVER_CONFIG_TWO_ADDR), ServerStatus.STARTED.toString());
        DomainTestUtils.waitUntilState(primaryClient, PRIMARY_ADDR.append(SERVER_CONFIG_THREE_ADDR), ServerStatus.STARTED.toString());

        validateProperties("server-one", 8080, BY_TYPE);
        validateProperties("server-two", 8180, BY_TYPE);
        validateProperties("server-three", 8280, BY_TYPE);
    }

    private void validateProperties(String server, int port, String directoryGrouping) throws IOException, MgmtOperationException {
        final Path serverHome = DomainTestSupport.getHostDir(JVMServerPropertiesTestCase.class.getSimpleName(), "primary").toPath();
        final Path serverBaseDir = serverHome.resolve("servers").resolve(server);
        final Path serverLogDir = BY_SERVER.equals(directoryGrouping) ? serverBaseDir.resolve("log") : serverHome.resolve("log").resolve("servers").resolve(server);
        final Path serverDataDir = BY_SERVER.equals(directoryGrouping) ? serverBaseDir.resolve("data") : serverHome.resolve("data").resolve("servers").resolve(server);
        final Path serverTmpDir = BY_SERVER.equals(directoryGrouping) ? serverBaseDir.resolve("tmp") : serverHome.resolve("tmp").resolve("servers").resolve(server);

        Assert.assertEquals(serverBaseDir.toAbsolutePath().toString(), resolveExpression(server, "${test.jboss.server.base.dir}"));
        Assert.assertEquals(serverLogDir.toAbsolutePath().toString(), resolveExpression(server, "${test.jboss.server.log.dir}"));
        Assert.assertEquals(serverDataDir.toAbsolutePath().toString(), resolveExpression(server, "${test.jboss.server.data.dir}"));
        Assert.assertEquals(serverTmpDir.toAbsolutePath().toString(), resolveExpression(server, "${test.jboss.server.temp.dir}"));
        Assert.assertEquals(server, resolveExpression(server, "${test.jboss.server.name}"));
    }

    private String resolveExpression(String server, String property) throws IOException, MgmtOperationException {
        PathAddress pathAddress = PRIMARY_ADDR.append(PathAddress.pathAddress(SERVER, server));
        ModelNode operation = Util.createOperation("resolve-expression", pathAddress);
        operation.get("expression").set(property);
        ModelNode result = DomainTestUtils.executeForResult(operation, primaryLifecycleUtil.createDomainClient());
        return result.asString();
    }

}
