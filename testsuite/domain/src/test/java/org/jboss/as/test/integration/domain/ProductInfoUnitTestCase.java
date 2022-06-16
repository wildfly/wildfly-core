/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ORGANIZATION;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.ARCH;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.AVAILABLE_PROCESSORS;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.CPU;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.HOSTNAME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.INSTANCE_ID;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_HOME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_VENDOR;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.JVM_VERSION;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.NODE_NAME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.OPERATION_NAME;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.OS;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PRODUCT_COMMUNITY_IDENTIFIER;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.PROJECT_TYPE;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.STANDALONE_DOMAIN_IDENTIFIER;
import static org.jboss.as.controller.operations.global.GlobalInstallationReportHandler.SUMMARY;
import static org.jboss.as.test.integration.domain.management.util.DomainTestSupport.validateResponse;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.BeforeClass;
//import org.junit.Ignore;
import org.junit.Test;

/**
 * Testing the product-info operation on a domain instance.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
//@Ignore("[WFCORE-1958] Clean up testsuite Elytron registration.")
public class ProductInfoUnitTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSupport.createAndStartSupport(DomainTestSupport.Configuration.create(ProductInfoUnitTestCase.class.getSimpleName(),
                "domain-configs/domain-minimal.xml", "host-configs/host-primary.xml", "host-configs/host-minimal.xml", false, false, false, false, false));
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
    }

    @Test
    public void testProductInfo() throws Exception {
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
        operation.get(OP).set(OPERATION_NAME);
        List<ModelNode> results = validateResponse(domainMasterLifecycleUtil.createDomainClient().execute(operation), true).asList();
        assertThat(results.size(), is(2));
        checkMaster(results.get(0));
        checkSlave(results.get(1));
    }

    private void checkMaster(ModelNode result) {
        List<Property> response = validateResponse(result, true).asPropertyList();
        assertThat(response.size(), is(5));
        for (Property serverSummary : response) {
            assertThat(serverSummary.getName(), is(SUMMARY));
            final ModelNode report = serverSummary.getValue();
            assertThat(report.isDefined(), is(true));
            assertThat(report.hasDefined(NODE_NAME), is(true));
            String nodeName = report.get(NODE_NAME).asString();
            assertThat(nodeName, anyOf(is("primary:main-one"), is("primary:main-two"), is("primary"), is("primary:reload-one"), is("primary:other-one")));
            boolean isRunning = "primary".equals(nodeName) || "primary:main-one".equals(nodeName);
            if (isRunning) {
                assertThat(report.get(ORGANIZATION).asString(), is("core-primary"));
                assertThat(report.hasDefined(HOSTNAME), is(true));
                assertThat(report.hasDefined(INSTANCE_ID), is(true));
                assertThat(report.hasDefined(PRODUCT_COMMUNITY_IDENTIFIER), is(true));
                assertThat(report.get(PRODUCT_COMMUNITY_IDENTIFIER).asString(), is(PROJECT_TYPE));
                assertThat(report.hasDefined(STANDALONE_DOMAIN_IDENTIFIER), is(true));
                if ("primary".equals(nodeName)) {
                    assertThat(report.get(STANDALONE_DOMAIN_IDENTIFIER).asString(), is(ProcessType.HOST_CONTROLLER.name()));
                } else {
                    assertThat(report.get(STANDALONE_DOMAIN_IDENTIFIER).asString(), is(ProcessType.DOMAIN_SERVER.name()));
                }
                assertThat(report.hasDefined(OS), is(true));
                assertThat(report.hasDefined(CPU), is(true));
                assertThat(report.hasDefined(CPU, ARCH), is(true));
                assertThat(report.hasDefined(CPU,AVAILABLE_PROCESSORS), is(true));
                assertThat(report.hasDefined(JVM), is(true));
                assertThat(report.hasDefined(JVM, NAME), is(true));
                assertThat(report.hasDefined(JVM, JVM_VENDOR), is(true));
                assertThat(report.hasDefined(JVM, JVM_VERSION), is(true));
                assertThat(report.hasDefined(JVM, JVM_HOME), is(true));
            }
        }
    }

    private void checkSlave(ModelNode result) {
        List<Property> response = validateResponse(result, true).asPropertyList();
        assertThat(response.size(), is(5));
        for (Property serverSummary : response) {
            assertThat(serverSummary.getName(), is(SUMMARY));
            final ModelNode report = serverSummary.getValue();
            assertThat(report.isDefined(), is(true));
            assertThat(report.hasDefined(NODE_NAME), is(true));
            String nodeName = report.get(NODE_NAME).asString();
            assertThat(report.hasDefined(ORGANIZATION), is(true));
            assertThat(report.get(ORGANIZATION).asString(), is("wildfly-core"));
            assertThat(nodeName, anyOf(is("secondary:main-three"), is("secondary:main-four"), is("secondary"), is("secondary:reload-two"), is("secondary:other-two")));
            boolean isRunning = "secondary".equals(nodeName) || "secondary:main-three".equals(nodeName) || "secondary:other-two".equals(nodeName);
            if (isRunning) {
                assertThat(report.hasDefined(HOSTNAME), is(true));
                assertThat(report.hasDefined(INSTANCE_ID), is(true));
                assertThat(report.hasDefined(PRODUCT_COMMUNITY_IDENTIFIER), is(true));
                assertThat(report.get(PRODUCT_COMMUNITY_IDENTIFIER).asString(), is(PROJECT_TYPE));
                assertThat(report.hasDefined(STANDALONE_DOMAIN_IDENTIFIER), is(true));
                if ("secondary".equals(nodeName)) {
                    assertThat(report.get(STANDALONE_DOMAIN_IDENTIFIER).asString(), is(ProcessType.HOST_CONTROLLER.name()));
                } else {
                    assertThat(report.get(STANDALONE_DOMAIN_IDENTIFIER).asString(), is(ProcessType.DOMAIN_SERVER.name()));
                }
                assertThat(report.hasDefined(OS), is(true));
                assertThat(report.hasDefined(CPU), is(true));
                assertThat(report.hasDefined(CPU, ARCH), is(true));
                assertThat(report.hasDefined(CPU,AVAILABLE_PROCESSORS), is(true));
                assertThat(report.hasDefined(JVM), is(true));
                assertThat(report.hasDefined(JVM, NAME), is(true));
                assertThat(report.hasDefined(JVM, JVM_VENDOR), is(true));
                assertThat(report.hasDefined(JVM, JVM_VERSION), is(true));
                assertThat(report.hasDefined(JVM, JVM_HOME), is(true));
            }
        }
    }
}
