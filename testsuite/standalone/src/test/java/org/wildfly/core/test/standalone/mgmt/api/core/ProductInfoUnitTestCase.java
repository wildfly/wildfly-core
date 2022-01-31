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

package org.wildfly.core.test.standalone.mgmt.api.core;


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
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Testing the product-info operation on a standalone instance.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a>  (c) 2015 Red Hat, inc.
 */
@RunWith(WildflyTestRunner.class)
public class ProductInfoUnitTestCase extends ContainerResourceMgmtTestBase {

    @Test
    public void testProductInfo() throws Exception {
        final ModelNode setOrganizationOp = Util.getWriteAttributeOperation(PathAddress.EMPTY_ADDRESS, ORGANIZATION, "wildfly-core");
        executeOperation(setOrganizationOp, true);
        try {
            final ModelNode operation = new ModelNode();
            operation.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
            operation.get(OP).set(OPERATION_NAME);

            final List<Property> result = executeOperation(operation, true).asPropertyList();
            assertThat(result.size(), is(1));
            assertThat(result.get(0).getName(), is(SUMMARY));
            final ModelNode report = result.get(0).getValue();
            assertThat(report.isDefined(), is(true));
            assertThat(report.hasDefined(NODE_NAME), is(false));
            assertThat(report.hasDefined(HOSTNAME), is(true));
            assertThat(report.hasDefined(HOSTNAME), is(true));
            assertThat(report.hasDefined(ORGANIZATION), is(true));
            assertThat(report.get(ORGANIZATION).asString(), is("wildfly-core"));
            assertThat(report.hasDefined(PRODUCT_COMMUNITY_IDENTIFIER), is(true));
            assertThat(report.get(PRODUCT_COMMUNITY_IDENTIFIER).asString(), is(PROJECT_TYPE));
            assertThat(report.hasDefined(STANDALONE_DOMAIN_IDENTIFIER), is(true));
            assertThat(report.get(STANDALONE_DOMAIN_IDENTIFIER).asString(), is(ProcessType.STANDALONE_SERVER.name()));
            assertThat(report.hasDefined(OS), is(true));
            assertThat(report.hasDefined(CPU), is(true));
            assertThat(report.get(CPU).hasDefined(ARCH), is(true));
            assertThat(report.get(CPU).hasDefined(AVAILABLE_PROCESSORS), is(true));
            assertThat(report.hasDefined(JVM), is(true));
            assertThat(report.get(JVM).hasDefined(NAME), is(true));
            assertThat(report.get(JVM).hasDefined(JVM_VENDOR), is(true));
            assertThat(report.get(JVM).hasDefined(JVM_VERSION), is(true));
            assertThat(report.get(JVM).hasDefined(JVM_HOME), is(true));
        } finally {
            final ModelNode unsetOrganizationOp = Util.getUndefineAttributeOperation(PathAddress.EMPTY_ADDRESS, ORGANIZATION);
            executeOperation(unsetOrganizationOp);
        }
    }
}
