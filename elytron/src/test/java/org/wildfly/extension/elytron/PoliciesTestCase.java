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

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_REQUIRES_RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * Tests handling of the policy=* resource.
 *
 * @author Brian Stansberry
 */
public class PoliciesTestCase extends AbstractElytronSubsystemBaseTest {

    private static final ModelNode JACC_POLICY;
    private static final ModelNode CUSTOM_POLICY;

    static {
        ModelNode policy = new ModelNode();
        policy.get("class-name").set("a.b.c.CustomPolicy");
        policy.get("module").set("a.b.c.custom");
        CUSTOM_POLICY = policy;
        policy = new ModelNode();
        policy.get("policy").set("a.b.c.Policy");
        policy.get("configuration-factory").set("a.b.PolicyConfigurationFactory");
        policy.get("module").set("a.b");
        JACC_POLICY = policy;
    }

    public PoliciesTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("jacc-provider.xml");
    }

    @Override
    public void testSubsystem() throws Exception {
        KernelServices services = standardSubsystemTest(null, true);

        // Check no default-policy
        checkNoDefaultPolicy(services, "elytron-a");

        // Check alternatives and list parameter correction
        checkPolicyAttributes(services, true);
    }

    @Test
    public void testParseAndMarshalModel_JaccWithProviders() throws Exception {
        // Here we know that unused elements from the original xml will be dropped (see WFCORE-3041).
        // So we don't want to compare what we persist to that original. But we do want to compare
        // to a model parsed from an equivalent config that does not have extraneous elements
        standardSubsystemTest("jacc-with-providers.xml", "jacc-provider.xml",false);
    }

    // Skip this variant as we use this config in the basic testSubsystem
//    @Test
//    public void testParseAndMarshalModel_Jacc() throws Exception {
//        standardSubsystemTest("jacc-provider.xml");
//    }

    @Test
    public void testParseAndMarshalModel_CustomPolicies() throws Exception {
        // Here we know that unused elements from the original xml will be dropped (see WFCORE-3041).
        // So we don't want to compare what we persist to that original. But we do want to compare
        // to a model parsed from an equivalent config that does not have extraneous elements
        standardSubsystemTest("custom-policies.xml", "custom-policy.xml",false);
    }

    @Test
    public void testParseAndMarshalModel_CustomPolicy() throws Exception {
        KernelServices services = standardSubsystemTest("custom-policy.xml", true);

        // Check no default-policy
        checkNoDefaultPolicy(services, "custom-b");

        // Check alternatives and list parameter correction
        checkPolicyAttributes(services, false);
    }

    @Test
    public void testDefaultPolicyWriteIgnored() throws Exception {
        KernelServices services = standardSubsystemTest("custom-policy.xml", true);
        ModelNode write = Util.getWriteAttributeOperation(getPolicyAddress("custom-b"), "default-policy", "test");
        ModelNode response = services.executeOperation(write);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        assertFalse(response.toString(), response.has(RESPONSE_HEADERS, OPERATION_REQUIRES_RELOAD));
        assertFalse(response.toString(), response.has(RESPONSE_HEADERS, OPERATION_REQUIRES_RESTART));
        checkNoDefaultPolicy(services, "custom-b");
    }

    private static PathAddress getPolicyAddress(String policy) {
        return PathAddress.pathAddress(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME).append("policy", policy);
    }

    private static void checkNoDefaultPolicy(KernelServices services, String policy) throws OperationFailedException {
        ModelNode result = services.executeForResult(Util.createEmptyOperation(READ_RESOURCE_OPERATION,
                getPolicyAddress(policy)));
        assertTrue(result.toString(), result.has("default-policy"));
        assertFalse(result.toString(), result.hasDefined("default-policy"));
    }

    private static void checkPolicyAttributes(KernelServices services, boolean forJACC) throws OperationFailedException {
        PathAddress address = forJACC ? getPolicyAddress("elytron-a")  : getPolicyAddress("custom-b");
        String attribute= forJACC ? "jacc-policy" : "custom-policy";
        ModelNode policy = forJACC ?  JACC_POLICY : CUSTOM_POLICY;
        String alternative = forJACC ? "custom-policy" : "jacc-policy";
        ModelNode alternativePolicy = forJACC ? CUSTOM_POLICY : JACC_POLICY;

        // Check alternatives
        ModelNode write = Util.getWriteAttributeOperation(address, alternative, alternativePolicy);
        write.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);

        services.executeForFailure(write);

        // Check parameter correction with a single element list
        ModelNode list = new ModelNode();
        list.add(policy);
        write.get(VALUE).set(list);
        write.get(NAME).set(attribute);

        services.executeForResult(write);

        ModelNode value = services.executeForResult(Util.getReadAttributeOperation(address, attribute));
        assertEquals(policy, value);

        // Check parameter not corrected with a multiple element list
        list.add(policy); // just be lazy reuse the same element
        write.get(VALUE).set(list);
        services.executeForFailure(write);

        value = services.executeForResult(Util.getReadAttributeOperation(address, attribute));
        assertEquals(policy, value);
    }
}
