/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.logging;

import java.io.IOException;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.junit.Assert.assertEquals;

import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2013 Red Hat, inc.
 */
public class LogFilterTestCase extends AbstractOperationsTestCase {

    private KernelServices kernelServices;

    @Before
    public void bootKernelServices() throws Exception {
        kernelServices = boot();
    }

    @After
    public void shutdown() {
        if (kernelServices != null) {
            kernelServices.shutdown();
        }
    }

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/default-subsystem.xml");
    }

    @Test
    public void addLoggingFilter() {
        ModelNode consoleAddress = createAddress("console-handler", "CONSOLE").toModelNode();
        ModelNode replaceValue = new ModelNode();
        replaceValue.get("pattern").set("JBAS");
        replaceValue.get("replacement").set("DUMMY");
        replaceValue.get("replace-all").set(true);
        ModelNode filterAttributeValue = new ModelNode();
        filterAttributeValue.get("replace").set(replaceValue);

        final ModelNode writeOp = Operations.createWriteAttributeOperation(consoleAddress, "filter", filterAttributeValue);
        executeOperation(kernelServices, writeOp);
        // Create the read operation
        final ModelNode readAttributeOp = Operations.createReadAttributeOperation(consoleAddress, "filter");
        ModelNode result = executeOperation(kernelServices, readAttributeOp);
        assertThat(result, is(notNullValue()));
        assertThat(result.get(OUTCOME).asString(), is("success"));
        assertEquals("{\"replace\" => {\"replace-all\" => true,\"pattern\" => \"JBAS\",\"replacement\" => \"DUMMY\"}}",
                Operations.readResult(result).asString());
        ModelNode readResourceOp = Operations.createReadResourceOperation(consoleAddress);
        readResourceOp.get(ModelDescriptionConstants.INCLUDE_ALIASES).set(true);
        result = executeOperation(kernelServices, readResourceOp);
        assertThat(result, is(notNullValue()));
        assertThat(result.get(OUTCOME).asString(), is("success"));
        assertThat(result.get(RESULT).hasDefined("filter-spec"), is(true));
        ModelNode filterSpec = result.get(RESULT).get("filter-spec");
        assertThat(filterSpec.asString(), is("substituteAll(\"JBAS\",\"DUMMY\")"));

        assertThat(result.toJSONString(false), result.get(RESULT).hasDefined("filter"), is(true));
        assertThat(result.get(RESULT).get("filter").hasDefined("replace"), is(true));
        ModelNode replaceResult = result.get(RESULT).get("filter").get("replace");
        assertThat(replaceResult.hasDefined("pattern"), is(true));
        assertThat(replaceResult.get("pattern").asString(), is("JBAS"));
        assertThat(replaceResult.hasDefined("replacement"), is(true));
        assertThat(replaceResult.get("replacement").asString(), is("DUMMY"));
        assertThat(replaceResult.hasDefined("pattern"), is(true));
        assertThat(replaceResult.get("pattern").asString(), is("JBAS"));
    }
}
