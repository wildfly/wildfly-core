/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.management;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.api.ReadConfigAsFeaturesTestBase;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * Tests {@code read-config-as-features} operation in domain mode.
 *
 * @author <a href="mailto:rjanik@redhat.com">Richard Janík</a>
 */
public class ReadConfigAsFeaturesDomainTestCase extends ReadConfigAsFeaturesTestBase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainPrimaryLifecycleUtil;

    private String defaultDomainConfig;
    private String defaultHostConfig;
    private ModelNode defaultDomainConfigAsFeatures;
    private ModelNode defaultHostConfigAsFeatures;
    private ModelNode nonNestedDomainConfigAsFeatures;
    private ModelNode nonNestedHostConfigAsFeatures;

    @BeforeClass
    public static void setupDomain() {
        testSupport = DomainTestSupport.createAndStartSupport(DomainTestSupport.Configuration.create(ReadConfigAsFeaturesDomainTestCase.class.getSimpleName(),
                "domain-configs/domain-standard.xml", "host-configs/host-primary.xml", "host-configs/host-secondary.xml"));
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() {
        try {
            Assert.assertNotNull(testSupport);
            testSupport.close();
        } finally {
            domainPrimaryLifecycleUtil = null;
            testSupport = null;
        }
    }

    @Test
    public void domainSystemPropertyTest() {
        ModelNode redefineProperty = Util.getWriteAttributeOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "jboss.domain.test.property.one"), VALUE, "SIX");
        ModelNode addProperty = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, "customProp"));
        addProperty.get(BOOT_TIME).set(false);
        addProperty.get(VALUE).set("customPropVal");

        ModelNode expectedDomainConfigAsFeatures = defaultDomainConfigAsFeatures.clone();

        // modify the existing property
        ModelNode propertyId = new ModelNode();
        propertyId.get(SYSTEM_PROPERTY).set("jboss.domain.test.property.one");
        ModelNode existingProperty = getListElement(expectedDomainConfigAsFeatures, "domain.system-property", propertyId);
        existingProperty.get(PARAMS).get(VALUE).set("SIX");

        // add the new property
        ModelNode newPropertyId = new ModelNode();
        ModelNode newPropertyParams = new ModelNode();
        ModelNode newProperty = new ModelNode();
        newPropertyId.get(SYSTEM_PROPERTY).set("customProp");
        newPropertyParams.get(BOOT_TIME).set(false);
        newPropertyParams.get(VALUE).set("customPropVal");
        newProperty.get(SPEC).set("domain.system-property");
        newProperty.get(ID).set(newPropertyId);
        newProperty.get(PARAMS).set(newPropertyParams);
        // add the new property
        expectedDomainConfigAsFeatures.add(newProperty);

        doTest(Arrays.asList(redefineProperty, addProperty), expectedDomainConfigAsFeatures, PathAddress.EMPTY_ADDRESS);
    }

    @Test
    public void domainProfileTest() {
        ModelNode redefineProfileAttribute = Util.getWriteAttributeOperation(
                PathAddress.pathAddress(PROFILE, DEFAULT).append(SUBSYSTEM, "io").append("buffer-pool", "default"),
                "buffer-size", 500);
        ModelNode removeSubsystemFromProfile = Util.createRemoveOperation(PathAddress.pathAddress(PROFILE, DEFAULT).append(SUBSYSTEM, "request-controller"));

        ModelNode expectedDomainConfigAsFeatures = defaultDomainConfigAsFeatures.clone();

        // remove the request controller subsystem
        ModelNode defaultProfileId = new ModelNode();
        defaultProfileId.get(PROFILE).set(DEFAULT);
        ModelNode defaultProfile = getListElement(expectedDomainConfigAsFeatures, PROFILE, defaultProfileId);
        int requestControllerSubsystemIndex = getFeatureNodeChildIndex(defaultProfile, "profile.subsystem.request-controller");
        defaultProfile.get(CHILDREN).remove(requestControllerSubsystemIndex);

        // rewrite the buffer-pool attribute
        ModelNode ioSubsystem = getFeatureNodeChild(defaultProfile, "profile.subsystem.io");
        ModelNode bufferPool = getFeatureNodeChild(ioSubsystem, "profile.subsystem.io.buffer-pool");
        ModelNode bufferPoolParams = new ModelNode();
        bufferPoolParams.get("buffer-size").set(500);
        bufferPool.get(PARAMS).set(bufferPoolParams);

        doTest(Arrays.asList(redefineProfileAttribute, removeSubsystemFromProfile), expectedDomainConfigAsFeatures, PathAddress.EMPTY_ADDRESS);
    }

    @Test
    public void hostInterfaceTest() {
        ModelNode redefineInterface = Util.getWriteAttributeOperation(PathAddress.pathAddress(HOST, "primary").append(INTERFACE, "management"), INET_ADDRESS, "10.10.10.10");

        ModelNode expectedHostConfigAsFeatures = defaultHostConfigAsFeatures.clone();

        ModelNode managementInterfaceId = new ModelNode();
        managementInterfaceId.get(INTERFACE).set("management");
        ModelNode managementInterface = getFeatureNodeChild(expectedHostConfigAsFeatures.get(0), "host.interface", managementInterfaceId);
        ModelNode managementInterfaceParams = new ModelNode();
        managementInterfaceParams.get(INET_ADDRESS).set("10.10.10.10");
        managementInterface.get(PARAMS).set(managementInterfaceParams);

        doTest(Collections.singletonList(redefineInterface), expectedHostConfigAsFeatures, PathAddress.pathAddress(HOST, "primary"));
    }

    @Test
    public void hostSubsystemTest() {
        ModelNode redefineJmxAttribute = Util.getWriteAttributeOperation(
                PathAddress.pathAddress(HOST, "primary").append(SUBSYSTEM, "jmx").append("expose-model", "resolved"),
                "domain-name", "customDomainName");

        ModelNode expectedHostConfigAsFeatures = defaultHostConfigAsFeatures.clone();

        ModelNode jmxSubsystem = getFeatureNodeChild(expectedHostConfigAsFeatures.get(0), "host.subsystem.jmx");
        ModelNode exposeModelResolved = getFeatureNodeChild(jmxSubsystem, "host.subsystem.jmx.expose-model.resolved");
        ModelNode exposeModelResolvedParams = new ModelNode();
        exposeModelResolvedParams.get("domain-name").set("customDomainName");
        exposeModelResolved.get(PARAMS).set(exposeModelResolvedParams);

        doTest(Collections.singletonList(redefineJmxAttribute), expectedHostConfigAsFeatures, PathAddress.pathAddress(HOST, "primary"));
    }

    @Test
    public void nonNestedDomainTest() {
        ModelNode makeIgnoredSbUnused = Util.getWriteAttributeOperation(PathAddress.pathAddress(SERVER_GROUP, "ignored-sockets"), SOCKET_BINDING_GROUP, "standard-sockets");
        ModelNode removeSocketBindingGroup = Util.createRemoveOperation(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "ignored"));
        ModelNode addSocketBindingGroup = Util.createAddOperation(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "custom"));
        addSocketBindingGroup.get(DEFAULT_INTERFACE).set("public");
        ModelNode addSocketBinding = Util.createAddOperation(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "custom").append(SOCKET_BINDING, "custom"));
        addSocketBinding.get(INTERFACE).set("public");
        addSocketBinding.get(PORT).set(4444);

        ModelNode expectedDomainConfigAsFeatures = nonNestedDomainConfigAsFeatures.clone();

        // remove the usage of ignored socket binding group
        ModelNode serverGroupId = new ModelNode();
        serverGroupId.get(SERVER_GROUP).set("ignored-sockets");
        int serverGroupIndex = getListElementIndex(expectedDomainConfigAsFeatures, "domain.server-group", serverGroupId);
        expectedDomainConfigAsFeatures.get(serverGroupIndex).get(PARAMS).get(SOCKET_BINDING_GROUP).set("standard-sockets");

        // remove the ignored socket binding group and its sole socket binding
        ModelNode ignoredSbgId = new ModelNode();
        ignoredSbgId.get(SOCKET_BINDING_GROUP).set("ignored");
        int ignoredSbgIndex = getListElementIndex(expectedDomainConfigAsFeatures, "domain.socket-binding-group", ignoredSbgId);
        expectedDomainConfigAsFeatures.remove(ignoredSbgIndex);
        ModelNode ignoredSbId = new ModelNode();
        ignoredSbId.get(SOCKET_BINDING_GROUP).set("ignored");
        ignoredSbId.get(SOCKET_BINDING).set("http");
        int ignoredSbIndex = getListElementIndex(expectedDomainConfigAsFeatures, "domain.socket-binding-group.socket-binding", ignoredSbId);
        expectedDomainConfigAsFeatures.remove(ignoredSbIndex);

        // add the custom socket binding group and its sole socket binding
        ModelNode customSocketBindingGroup = new ModelNode();
        ModelNode customSocketBindingGroupId = new ModelNode();
        ModelNode customSocketBindingGroupParams = new ModelNode();
        customSocketBindingGroupParams.get(DEFAULT_INTERFACE).set("public");
        customSocketBindingGroupId.get(SOCKET_BINDING_GROUP).set("custom");
        customSocketBindingGroup.get(SPEC).set("domain.socket-binding-group");
        customSocketBindingGroup.get(ID).set(customSocketBindingGroupId);
        customSocketBindingGroup.get(PARAMS).set(customSocketBindingGroupParams);
        ModelNode customSb = new ModelNode();
        ModelNode customSbId = new ModelNode();
        ModelNode customSbParams = new ModelNode();
        customSbParams.get(INTERFACE).set("public");
        customSbParams.get(PORT).set(4444);
        customSbId.get(SOCKET_BINDING_GROUP).set("custom");
        customSbId.get(SOCKET_BINDING).set("custom");
        customSb.get(SPEC).set("domain.socket-binding-group.socket-binding");
        customSb.get(ID).set(customSbId);
        customSb.get(PARAMS).set(customSbParams);
        expectedDomainConfigAsFeatures.add(customSocketBindingGroup);
        expectedDomainConfigAsFeatures.add(customSb);

        doTest(Arrays.asList(makeIgnoredSbUnused, removeSocketBindingGroup, addSocketBindingGroup, addSocketBinding), expectedDomainConfigAsFeatures, PathAddress.EMPTY_ADDRESS, false);
    }

    @Test
    public void nonNestedHostTest() {
        ModelNode removeJvm = Util.createRemoveOperation(PathAddress.pathAddress(HOST, "primary").append(JVM, DEFAULT));
        ModelNode addCustomJvm = Util.createAddOperation(PathAddress.pathAddress(HOST, "primary").append(JVM, "custom"));
        ModelNode environmentVariables = new ModelNode();
        environmentVariables.get("DOMAIN_TEST_JVM").set("custom");
        addCustomJvm.get("heap-size").set("64m");
        addCustomJvm.get("jvm-options").add("-ea");
        addCustomJvm.get("max-heap-size").set("128m");
        addCustomJvm.get("environment-variables").set(environmentVariables);

        ModelNode expectedHostConfigAsFeatures = nonNestedHostConfigAsFeatures.clone();

        // remove the default jvm
        ModelNode defaultJvmId = new ModelNode();
        defaultJvmId.get(HOST).set("primary");
        defaultJvmId.get(JVM).set(DEFAULT);
        int defaultJvmIndex = getListElementIndex(expectedHostConfigAsFeatures, "host.jvm", defaultJvmId);
        expectedHostConfigAsFeatures.remove(defaultJvmIndex);

        // add the custom jvm
        ModelNode customJvm = new ModelNode();
        ModelNode customJvmId = new ModelNode();
        ModelNode customJvmParams = new ModelNode();
        ModelNode customJvmEnvVars = new ModelNode();
        customJvmEnvVars.get("DOMAIN_TEST_JVM").set("custom");
        customJvmParams.get("heap-size").set("64m");
        customJvmParams.get("jvm-options").add("-ea");
        customJvmParams.get("max-heap-size").set("128m");
        customJvmParams.get("environment-variables").set(customJvmEnvVars);
        customJvmId.get(HOST).set("primary");
        customJvmId.get(JVM).set("custom");
        customJvm.get(SPEC).set("host.jvm");
        customJvm.get(ID).set(customJvmId);
        customJvm.get(PARAMS).set(customJvmParams);
        expectedHostConfigAsFeatures.add(customJvm);

        doTest(Arrays.asList(removeJvm, addCustomJvm), expectedHostConfigAsFeatures, PathAddress.pathAddress(HOST, "primary"), false);
    }

    @Test
    public void ensureNoNestedSpecsTest() {
        ensureNoNestedSpecs(nonNestedDomainConfigAsFeatures);
        ensureNoNestedSpecs(nonNestedHostConfigAsFeatures);
    }

    private void doTest(List<ModelNode> operations, ModelNode expectedConfigAsFeatures, PathAddress domainOrHostPath) {
        doTest(operations, expectedConfigAsFeatures, domainOrHostPath, true);
    }

    private void doTest(List<ModelNode> operations, ModelNode expectedConfigAsFeatures, PathAddress domainOrHostPath, boolean nested) {
        for (ModelNode operation : operations) {
            domainPrimaryLifecycleUtil.executeForResult(operation);
        }
        if (!equalsWithoutListOrder(expectedConfigAsFeatures, getConfigAsFeatures(nested, domainOrHostPath))) {
            System.out.println("Actual:\n" + getConfigAsFeatures(domainOrHostPath).toJSONString(false) + "\nExpected:\n" + expectedConfigAsFeatures.toJSONString(false));
            Assert.fail("There are differences between the expected and the actual model, see the test output for details");
        }
    }

    @Override
    protected void saveDefaultConfig() {
        if (defaultDomainConfig == null || defaultHostConfig == null) {
            ModelNode takeSnapshotOnDomain = Util.createEmptyOperation(TAKE_SNAPSHOT_OPERATION, PathAddress.EMPTY_ADDRESS);
            ModelNode takeSnapshotOnHost = Util.createEmptyOperation(TAKE_SNAPSHOT_OPERATION, PathAddress.pathAddress(HOST, "primary"));
            domainPrimaryLifecycleUtil.executeForResult(takeSnapshotOnDomain);
            domainPrimaryLifecycleUtil.executeForResult(takeSnapshotOnHost);
            ModelNode listDomainSnapshots = Util.createEmptyOperation(LIST_SNAPSHOTS_OPERATION, PathAddress.EMPTY_ADDRESS);
            ModelNode listHostSnapshots = Util.createEmptyOperation(LIST_SNAPSHOTS_OPERATION, PathAddress.pathAddress(HOST, "primary"));
            ModelNode domainSnapshots = domainPrimaryLifecycleUtil.executeForResult(listDomainSnapshots);
            ModelNode hostSnapshots = domainPrimaryLifecycleUtil.executeForResult(listHostSnapshots);

            defaultDomainConfig = domainSnapshots.get("names").get(0).asString();
            defaultHostConfig = hostSnapshots.get("names").get(0).asString();
        }
    }

    @Override
    protected void saveDefaultResult() {
        if (defaultDomainConfigAsFeatures == null || defaultHostConfigAsFeatures == null) {
            defaultDomainConfigAsFeatures = getConfigAsFeatures(PathAddress.EMPTY_ADDRESS);
            defaultHostConfigAsFeatures = getConfigAsFeatures(PathAddress.pathAddress(HOST, "primary"));
        }
    }

    @Override
    protected void saveNonNestedResult() {
        if (nonNestedDomainConfigAsFeatures == null || nonNestedHostConfigAsFeatures == null) {
            nonNestedDomainConfigAsFeatures = getConfigAsFeatures(false, PathAddress.EMPTY_ADDRESS);
            nonNestedHostConfigAsFeatures = getConfigAsFeatures(false, PathAddress.pathAddress(HOST, "primary"));
        }
    }

    @Override
    protected void restoreDefaultConfig() throws TimeoutException, InterruptedException {
        ModelNode reloadWithSnapshots = Util.createEmptyOperation(RELOAD, PathAddress.pathAddress(HOST, "primary"));
        reloadWithSnapshots.get(DOMAIN_CONFIG).set(defaultDomainConfig);
        reloadWithSnapshots.get(HOST_CONFIG).set(defaultHostConfig);
        domainPrimaryLifecycleUtil.executeForResult(reloadWithSnapshots);
        domainPrimaryLifecycleUtil.awaitHostController(System.currentTimeMillis());
        domainPrimaryLifecycleUtil.awaitServers(System.currentTimeMillis());
    }

    private ModelNode getConfigAsFeatures() {
        return getConfigAsFeatures(true, PathAddress.EMPTY_ADDRESS);
    }

    private ModelNode getConfigAsFeatures(boolean nested) {
        return getConfigAsFeatures(nested, PathAddress.EMPTY_ADDRESS);
    }

    private ModelNode getConfigAsFeatures(PathAddress pathAddress) {
        return getConfigAsFeatures(true, pathAddress);
    }

    private ModelNode getConfigAsFeatures(boolean nested, PathAddress pathElements) {
        ModelNode getConfigAsFeatures = Util.createEmptyOperation(READ_CONFIG_AS_FEATURES_OPERATION, pathElements);
        getConfigAsFeatures.get(NESTED).set(nested);
        return domainPrimaryLifecycleUtil.executeForResult(getConfigAsFeatures);
    }
}
