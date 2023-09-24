/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.boot;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICES_MISSING_DEPENDENCIES;

import java.util.List;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class HostBootErrorsTestCase extends AbstractBootErrorTestCase {

    public HostBootErrorsTestCase() {
        super(TestModelType.HOST);
    }

    @Override
    protected String getXmlResource() {
        return "host.xml";
    }

    @Test
    public void testBootErrors() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder()
                .setXmlResource(getXmlResource())
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), getXmlResource()), marshalled);

        kernelServices = createKernelServicesBuilder()
                .setXml(marshalled)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        ModelNode readBootErrorsOp = Util.createOperation("read-boot-errors", PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MANAGEMENT)));
        ModelNode result = kernelServices.executeForResult(readBootErrorsOp);
        MatcherAssert.assertThat(result, is(notNullValue()));
        MatcherAssert.assertThat(result.asString(), result.getType(), is(ModelType.LIST));
        List<ModelNode> errors = result.asList();
        MatcherAssert.assertThat(errors.size(), is(3));
        ModelNode error = errors.get(0);
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(OP).asString(), is(ADD));
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(ADDRESS).asString(), is("[(\"host\" => \"primary\"),(\"core-service\" => \"management\"),(\"access\" => \"audit\"),(\"syslog-handler\" => \"syslog-udp\")]"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILURE_DESCRIPTION), is(true));
        MatcherAssert.assertThat(error.asString(), error.get(FAILURE_DESCRIPTION).asString(), containsString("testhost"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILED_SERVICES), is(false));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(SERVICES_MISSING_DEPENDENCIES), is(false));
        error = errors.get(1);
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(OP).asString(), is(ADD));
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(ADDRESS).asString(), is("[(\"host\" => \"primary\"),(\"core-service\" => \"management\"),(\"access\" => \"audit\"),(\"syslog-handler\" => \"syslog-tcp\")]"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILURE_DESCRIPTION), is(true));
        MatcherAssert.assertThat(error.asString(), error.get(FAILURE_DESCRIPTION).asString(), containsString("testhost"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILED_SERVICES), is(false));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(SERVICES_MISSING_DEPENDENCIES), is(false));
        error = errors.get(2);
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(OP).asString(), is(ADD));
        MatcherAssert.assertThat(error.asString(), error.get(FAILED_OPERATION).get(ADDRESS).asString(), is("[(\"host\" => \"primary\"),(\"core-service\" => \"management\"),(\"access\" => \"audit\"),(\"syslog-handler\" => \"syslog-tls\")]"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILURE_DESCRIPTION), is(true));
        MatcherAssert.assertThat(error.asString(), error.get(FAILURE_DESCRIPTION).asString(), containsString("testhost"));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(FAILED_SERVICES), is(false));
        MatcherAssert.assertThat(error.asString(), error.hasDefined(SERVICES_MISSING_DEPENDENCIES), is(false));
    }
}
