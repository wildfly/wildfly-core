/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.io;

import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_0_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_1_0;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.io.IOExtension.SUBSYSTEM_PATH;
import static org.wildfly.extension.io.IOExtension.WORKER_PATH;
import static org.wildfly.extension.io.WorkerResourceDefinition.STACK_SIZE;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_IO_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_CORE_THREADS;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_KEEPALIVE;
import static org.wildfly.extension.io.WorkerResourceDefinition.WORKER_TASK_MAX_THREADS;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.FailedOperationTransformationConfig.ChainedConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class IOSubsystemTransformerTestCase extends AbstractSubsystemBaseTest {

    public IOSubsystemTransformerTestCase() {
        super(IOExtension.SUBSYSTEM_NAME, new IOExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("io-3.0-transformer.xml");
    }


    @Test
    public void testTransformerEAP700() throws Exception {
        testTransformation(EAP_7_0_0);
    }

    @Test
    public void testTransformerEAP710() throws Exception {
        testTransformation(EAP_7_1_0);
    }

    private KernelServices buildKernelServices(ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        return this.buildKernelServices(this.getSubsystemXml(), controllerVersion, version, mavenResourceURLs);
    }

    private KernelServices buildKernelServices(String xml, ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }

    private void testTransformation(final ModelTestControllerVersion controllerVersion) throws Exception {
        final ModelVersion version = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());
        final String xml = readResource(String.format("io-%d.0-transformer.xml", version.getMajor()));

        KernelServices services = this.buildKernelServices(xml, controllerVersion, version,
                controllerVersion.getCoreMavenGroupId() + ":wildfly-io:" + controllerVersion.getCoreVersion());

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, version, null, false);

        ModelNode transformed = services.readTransformedModel(version);
        Assert.assertTrue(transformed.isDefined());
    }

    @Test
    public void testRejectingTransformersEAP_7_0_0() throws Exception {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM_PATH);
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(WORKER_PATH),
                        ChainedConfig.createBuilder(STACK_SIZE, WORKER_IO_THREADS, WORKER_TASK_KEEPALIVE, WORKER_TASK_MAX_THREADS, WORKER_TASK_CORE_THREADS)
                            .addConfig(
                                    new FailedOperationTransformationConfig.RejectExpressionsConfig(
                                            STACK_SIZE,
                                            WORKER_IO_THREADS,
                                            WORKER_TASK_KEEPALIVE,
                                            WORKER_TASK_MAX_THREADS
                                    )
                            )
                            .addConfig(new FailedOperationTransformationConfig.NewAttributesConfig(WORKER_TASK_CORE_THREADS))
                            .build()
                )
                .addFailedAttribute(subsystemAddress.append(PathElement.pathElement(WORKER_PATH.getKey(), "fourth-worker"), PathElement.pathElement("outbound-bind-address")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                );
        testRejectingTransformers(EAP_7_0_0, config);
    }

    @Test
    public void testRejectingTransformersEAP_7_1_0() throws Exception {
        PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM_PATH);
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig()
                .addFailedAttribute(subsystemAddress.append(WORKER_PATH),
                        new FailedOperationTransformationConfig.NewAttributesConfig(
                                WORKER_TASK_CORE_THREADS
                        )
                );
        testRejectingTransformers(EAP_7_1_0, config);
    }

    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion, FailedOperationTransformationConfig config) throws Exception {
        ModelVersion modelVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization());
        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), controllerVersion, modelVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId() + ":wildfly-io:" + controllerVersion.getCoreVersion())
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(modelVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource("io-reject.xml");
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, ops, config);
    }
}
