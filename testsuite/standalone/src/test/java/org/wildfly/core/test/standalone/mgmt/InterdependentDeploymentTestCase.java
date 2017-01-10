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

package org.wildfly.core.test.standalone.mgmt;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeployment;
import org.jboss.as.test.deployment.trivial.ServiceActivatorDeploymentUtil;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceName;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test of deployment ops for deployments that depend on other deployments.
 * Smoke test for issues like MSC-155/MSC-156.
 *
 * @author Brian Stansberry
 */
@RunWith(WildflyTestRunner.class)
public class InterdependentDeploymentTestCase {

    @Inject
    private ManagementClient managementClient;

    @Test
    public void test() throws Exception {

        final JavaArchive deplA = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("interrelated-a.jar",
                Collections.singletonMap("interrelated-a.jar", "a"));
        Map<String, JavaArchive> dependents = new HashMap<>();
        dependents.put("b", getDependentDeployment("b", ServiceActivatorDeploymentB.class, "interrelated-a.jar"));
        dependents.put("c", getDependentDeployment("c", ServiceActivatorDeploymentC.class, "interrelated-a.jar"));
        dependents.put("d", getDependentDeployment("d", ServiceActivatorDeploymentD.class, "interrelated-a.jar","interrelated-b.jar"));
        dependents.put("e", getDependentDeployment("e", ServiceActivatorDeploymentE.class, "interrelated-a.jar","interrelated-d.jar"));
        dependents.put("f", getDependentDeployment("f", ServiceActivatorDeploymentF.class, "interrelated-a.jar","interrelated-c.jar","interrelated-d.jar"));

        List<InputStream> streams = new ArrayList<>();
        ModelNode add = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        ModelNode steps = add.get("steps");
        streams.add(deplA.as(ZipExporter.class).exportAsInputStream());
        steps.add(createDeployStep("a", streams.size() - 1));
        for (Map.Entry<String, JavaArchive> entry : dependents.entrySet()) {
            streams.add(entry.getValue().as(ZipExporter.class).exportAsInputStream());
            steps.add(createDeployStep(entry.getKey(), streams.size() - 1));
        }

        ModelNode response = managementClient.getControllerClient().execute(Operation.Factory.create(add, streams, true));
        Assert.assertEquals(response.toString(), "success", response.get("outcome").asString());

        validateDeployment("a", "a");

        for (String dependent : dependents.keySet()) {
            validateDeployment(dependent, dependent);
        }

        managementClient.executeForResult(Util.createEmptyOperation("undeploy", PathAddress.pathAddress("deployment", "interrelated-a.jar")));

        validateNoDeployment("a");

        for (String dependent : dependents.keySet()) {
            validateNoDeployment(dependent);
        }

        managementClient.executeForResult(Util.createEmptyOperation("deploy", PathAddress.pathAddress("deployment", "interrelated-a.jar")));

        validateDeployment("a", "a");

        for (String dependent : dependents.keySet()) {
            validateDeployment(dependent, dependent);
        }

        // USE A SYSTEM PROPERTY TO EXECUTE full-replace-deployment repeatedly
        int loops = Integer.parseInt(System.getProperty("InterdependentDeploymentTestCase.count", "1"));
        int blockingTime = TimeoutUtil.adjust(30); // set this to fail faster if it fails
        for (int i = 0; i < loops; i++) {

            try {
                String newVal = "a" + i;
                final JavaArchive replA = ServiceActivatorDeploymentUtil.createServiceActivatorDeploymentArchive("interrelated-a.jar",
                        Collections.singletonMap("interrelated-a.jar", newVal));

                ModelNode frd = Util.createEmptyOperation("full-replace-deployment", PathAddress.EMPTY_ADDRESS);
                frd.get("name").set("interrelated-a.jar");
                frd.get("content").add().get("input-stream-index").set(0);
                frd.get("enabled").set(true);
                frd.get("operation-headers", "blocking-timeout").set(blockingTime);

                response = managementClient.getControllerClient().execute(Operation.Factory.create(frd,
                        Collections.singletonList(replA.as(ZipExporter.class).exportAsInputStream()), true));
                Assert.assertEquals("Response to iteration " + i + " -- " + response.toString(), "success", response.get("outcome").asString());

                validateDeployment("a", newVal);

                for (String dependent : dependents.keySet()) {
                    validateDeployment(dependent, dependent);
                }
            } catch (AssertionError ae) {
                dumpServiceDetails();
                throw ae;
            }
        }

        ModelNode undeploy = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        steps = undeploy.get("steps");
        steps.add(Util.createEmptyOperation("undeploy", PathAddress.pathAddress("deployment", "interrelated-a.jar")));
        for (String dependent : dependents.keySet()) {
            steps.add(Util.createEmptyOperation("undeploy", PathAddress.pathAddress("deployment", "interrelated-" + dependent + ".jar")));
        }

        managementClient.executeForResult(undeploy);

        validateNoDeployment("a");

        for (String dependent : dependents.keySet()) {
            validateNoDeployment(dependent);
        }

        ModelNode remove = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        steps = remove.get("steps");
        steps.add(Util.createEmptyOperation("remove", PathAddress.pathAddress("deployment", "interrelated-a.jar")));
        for (String dependent : dependents.keySet()) {
            steps.add(Util.createEmptyOperation("remove", PathAddress.pathAddress("deployment", "interrelated-" + dependent + ".jar")));
        }

        managementClient.executeForResult(remove);

    }

    private void dumpServiceDetails() {
        ModelNode op = Util.createEmptyOperation("dump-services",
                PathAddress.pathAddress("core-service", "service-container")
        );
        try {
            System.out.println(managementClient.executeForResult(op));
        } catch (UnsuccessfulOperationException e) {
            System.out.println("Failed to dump service details -- " + e.toString());
        }
    }

    private ModelNode createDeployStep(String name, int inputStreamIndex) {
        ModelNode result = Util.createAddOperation(PathAddress.pathAddress("deployment", "interrelated-" + name + ".jar"));
        result.get("enabled").set(true);
        result.get("content").add().get("input-stream-index").set(inputStreamIndex);
        return  result;
    }

    private void validateDeployment(String name, String value) throws IOException, MgmtOperationException {
        ServiceActivatorDeploymentUtil.validateProperties(managementClient.getControllerClient(),
                Collections.singletonMap("interrelated-" + name + ".jar", value));
    }

    private void validateNoDeployment(String name) throws IOException, MgmtOperationException {
        ServiceActivatorDeploymentUtil.validateNoProperties(managementClient.getControllerClient(),
                Collections.singleton("interrelated-" + name + ".jar"));
    }

    private JavaArchive getDependentDeployment(String name, Class clazz, String... dependencies) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.addClass(clazz);
        archive.addAsServiceProvider(ServiceActivator.class, clazz);
        archive.addAsManifestResource(new StringAsset("Dependencies: org.jboss.msc\n"), "MANIFEST.MF");

        String propFileContent = "interrelated-" + name + ".jar=" + name + '\n';
        archive.addAsResource(new StringAsset(propFileContent), name + ".properties");

        archive.addAsResource(new StringAsset(getJBossDeploymentStructure(dependencies)), "META-INF/jboss-deployment-structure.xml");

        return archive;
    }

    private String getJBossDeploymentStructure(String... dependencies) {
        StringBuilder sb = new StringBuilder(
                "<jboss-deployment-structure>\n" +
                "  <deployment>\n" +
                "    <dependencies>\n");
        for (String dep : dependencies) {
            sb.append(
                    "      <module name=\"deployment.").append(dep).append("\"");
            if (dep.equals("interrelated-c.jar")) {
                sb.append(" optional=\"true\"");
            }
            sb.append("/>\n");
        }
        sb.append(
                "    </dependencies>\n" +
                "  </deployment>\n" +
                "</jboss-deployment-structure>");

        return sb.toString();
    }

    public static class ServiceActivatorDeploymentB extends ServiceActivatorDeployment {

        public ServiceActivatorDeploymentB() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "b"), "b.properties");
        }
    }

    public static class ServiceActivatorDeploymentC extends ServiceActivatorDeployment {

        public ServiceActivatorDeploymentC() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "c"), "c.properties");
        }
    }

    public static class ServiceActivatorDeploymentD extends ServiceActivatorDeployment {

        private final ServiceActivatorDeploymentB imported = new ServiceActivatorDeploymentB();

        public ServiceActivatorDeploymentD() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "d"), "d.properties");
        }
    }

    public static class ServiceActivatorDeploymentE extends ServiceActivatorDeployment {

        private final ServiceActivatorDeploymentD imported = new ServiceActivatorDeploymentD();

        public ServiceActivatorDeploymentE() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "e"), "e.properties");
        }
    }

    public static class ServiceActivatorDeploymentF extends ServiceActivatorDeployment {

        //private final ServiceActivatorDeploymentC importedC = new ServiceActivatorDeploymentC();
        private final ServiceActivatorDeploymentD importedD = new ServiceActivatorDeploymentD();

        public ServiceActivatorDeploymentF() {
            super(ServiceName.of(ServiceActivatorDeployment.class.getSimpleName(), "f"), "f.properties");
        }
    }
}
