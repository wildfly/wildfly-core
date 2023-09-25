/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.io.IOException;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.cpu.ProcessorInfo;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class IOSubsystem20TestCase extends AbstractSubsystemBaseTest {

    public IOSubsystem20TestCase() {
        super(IOExtension.SUBSYSTEM_NAME, new IOExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("io-2.0.xml");
    }

    protected void standardSubsystemTest(final String configId) throws Exception {
        standardSubsystemTest(configId, false);
    }

    @Test
    public void testRuntime() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml());
        KernelServices mainServices = builder.build();
        if (!mainServices.isSuccessfulBoot()) {
            Assert.fail(String.valueOf(mainServices.getBootError()));
        }
        ServiceController<XnioWorker> workerServiceController = (ServiceController<XnioWorker>) mainServices.getContainer().getService(IOServices.WORKER.append("default"));
        workerServiceController.setMode(ServiceController.Mode.ACTIVE);
        workerServiceController.awaitValue();
        XnioWorker worker = workerServiceController.getService().getValue();
        Assert.assertEquals(ProcessorInfo.availableProcessors() * 2, worker.getIoThreadCount());
        Assert.assertEquals(ProcessorInfo.availableProcessors() * 16, worker.getOption(Options.WORKER_TASK_MAX_THREADS).intValue());
        PathAddress addr = PathAddress.parseCLIStyleAddress("/subsystem=io/worker=default");
        ModelNode op = Util.createOperation("read-resource", addr);
        op.get("include-runtime").set(true);
        mainServices.executeOperation(op);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization() {
            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.NORMAL;
            }
        };
    }

    protected static final OptionAttributeDefinition ENABLED_PROTOCOLS = OptionAttributeDefinition.builder("enabled-protocols", Options.SSL_ENABLED_PROTOCOLS)
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .build();

    @Test
    public void testSequence() throws Exception {
        OptionMap.Builder builder = OptionMap.builder();
        ModelNode model = new ModelNode();
        ModelNode operation = new ModelNode();
        operation.get(ENABLED_PROTOCOLS.getName()).set("TLSv1, TLSv1.1, TLSv1.2");
        ENABLED_PROTOCOLS.validateAndSet(operation, model);
        ENABLED_PROTOCOLS.resolveOption(ExpressionResolver.SIMPLE, model, builder);
        Sequence<String> protocols = builder.getMap().get(Options.SSL_ENABLED_PROTOCOLS);
        Assert.assertEquals(3, protocols.size());
        Assert.assertEquals("TLSv1", protocols.get(0));
        Assert.assertEquals("TLSv1.1", protocols.get(1));
        Assert.assertEquals("TLSv1.2", protocols.get(2));

    }

}
