/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.msc.service.ServiceController;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.common.cpu.ProcessorInfo;
import org.xnio.Options;
import org.xnio.XnioWorker;

import java.io.IOException;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class IOSubsystem10TestCase extends AbstractSubsystemBaseTest {

    public IOSubsystem10TestCase() {
        super(IOExtension.SUBSYSTEM_NAME, new IOExtension());
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) throws Exception {
        super.compareXml(configId, marshalled, readResource("shipped-default.xml"));
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("io-1.0.xml");
    }

    @Test
    public void testRuntime() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(getSubsystemXml());
        KernelServices mainServices = builder.build();
        if (!mainServices.isSuccessfulBoot()) {
            Assert.fail(mainServices.getBootError().toString());
        }
        ServiceController<XnioWorker> workerServiceController = (ServiceController<XnioWorker>) mainServices.getContainer().getService(IOServices.WORKER.append("default"));
        workerServiceController.setMode(ServiceController.Mode.ACTIVE);
        workerServiceController.awaitValue();
        XnioWorker worker = workerServiceController.getService().getValue();
        Assert.assertEquals(ProcessorInfo.availableProcessors() * 2, worker.getIoThreadCount());
        Assert.assertEquals(ProcessorInfo.availableProcessors() * 16, worker.getOption(Options.WORKER_TASK_MAX_THREADS).intValue());
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

}
