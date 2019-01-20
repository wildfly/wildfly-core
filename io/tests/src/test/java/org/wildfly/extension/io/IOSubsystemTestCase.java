/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
 */

package org.wildfly.extension.io;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;

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
public class IOSubsystemTestCase extends AbstractSubsystemBaseTest {

    public IOSubsystemTestCase() {
        super(IOExtension.SUBSYSTEM_NAME, new IOExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("io-3.0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-io_3_0.xsd";
    }

    @Override
    protected String[] getSubsystemTemplatePaths() throws IOException {
        return new String[]{
                "/subsystem-templates/io.xml"
        };
    }

    @Test
    @Override
    public void testSchemaOfSubsystemTemplates() throws Exception {
        super.testSchemaOfSubsystemTemplates();
    }

    @Test
    public void testRuntime() throws Exception {
        KernelServices mainServices = startKernelServices(getSubsystemXml());
        XnioWorker worker = startXnioWorker(mainServices);
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

    @Test
    public void testThreadPoolAttrPropagation() throws Exception {
        KernelServices kernelServices = startKernelServices(getSubsystemXml());
        startXnioWorker(kernelServices);

        int coreThreads = 4; // default = 2
        int maxThreads = 64; // default = 128
        int keepAliveMillis = 5000; // default = 100 (0 in seconds)
        PathAddress addr = PathAddress.parseCLIStyleAddress("/subsystem=io/worker=default");
        ModelNode maxThreadsOp = Util.getWriteAttributeOperation(addr, Constants.WORKER_TASK_MAX_THREADS, maxThreads);
        ModelNode coreThreadsOp = Util.getWriteAttributeOperation(addr, Constants.WORKER_TASK_CORE_THREADS, coreThreads);
        ModelNode keepAliveOp = Util.getWriteAttributeOperation(addr, Constants.WORKER_TASK_KEEPALIVE, keepAliveMillis);

        kernelServices.executeOperation(maxThreadsOp);
        kernelServices.executeOperation(coreThreadsOp);
        kernelServices.executeOperation(keepAliveOp);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName threadPoolName = new ObjectName("jboss.threads:name=\"default\",type=thread-pool");
        Assert.assertEquals(coreThreads, (int) mbs.getAttribute(threadPoolName, "CorePoolSize"));
        Assert.assertEquals(maxThreads, (int) mbs.getAttribute(threadPoolName, "MaximumPoolSize"));
        Assert.assertEquals(keepAliveMillis / 1000, (long) mbs.getAttribute(threadPoolName, "KeepAliveTimeSeconds"));
    }

    protected KernelServices startKernelServices(String subsystemXml) throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXml(subsystemXml);

        KernelServices kernelServices = builder.build();
        if (!kernelServices.isSuccessfulBoot()) {
            Assert.fail(String.valueOf(kernelServices.getBootError()));
        }

        return kernelServices;
    }

    protected XnioWorker startXnioWorker(KernelServices kernelServices) throws InterruptedException {
        ServiceController<XnioWorker> workerServiceController = (ServiceController<XnioWorker>) kernelServices.getContainer().getService(IOServices.WORKER.append("default"));
        workerServiceController.setMode(ServiceController.Mode.ACTIVE);
        workerServiceController.awaitValue();
        return workerServiceController.getService().getValue();
    }
}
