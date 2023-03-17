/*
Copyright 2018 Red Hat, Inc.

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

package org.jboss.as.server;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests Services.addServerExecutorDependency handling for WFCORE-4099 / MSC-240.
 *
 * @author Brian Stansberry
 */
public class AddServerExecutorDependencyTestCase {

    private static final String TESTNAME = AddServerExecutorDependencyTestCase.class.getSimpleName();

    private ServiceContainer container;
    private Path tmpDir;
    private ExecutorService executorService;

    @Before
    public void setup() throws IOException {
        container = ServiceContainer.Factory.create(TESTNAME);
        tmpDir = Files.createTempDirectory(TESTNAME);
        executorService = Executors.newSingleThreadExecutor();
    }

    @After
    public void teardown() throws IOException {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
        if (tmpDir != null) {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileVisitResult result = super.visitFile(file, attrs);
                    Files.deleteIfExists(file);
                    return  result;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    FileVisitResult result = super.postVisitDirectory(dir, exc);
                    Files.deleteIfExists(dir);
                    return  result;
                }
            });
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * Tests that Services.requireServerExecutor's dependency injection works regardless of what
     * ServiceTarget API was used for creating the target ServiceBuilder.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testDifferentServiceBuilderTypes() {
        ServiceTarget serviceTarget = container.subTarget();

        ServiceBuilder<?> mgmtExecutorSB = serviceTarget.addService(ServerService.MANAGEMENT_EXECUTOR);
        Consumer<ExecutorService> executorServiceConsumer = mgmtExecutorSB.provides(ServerService.MANAGEMENT_EXECUTOR);
        mgmtExecutorSB.setInstance(org.jboss.msc.Service.newInstance(executorServiceConsumer, executorService));
        ServiceController<?> executorController = mgmtExecutorSB.install();

        //TestService legacy = new TestService();
        ServiceBuilder<?> legacyBuilder = serviceTarget.addService(ServiceName.of("LEGACY"));
        TestService legacy = new TestService(Services.requireServerExecutor(legacyBuilder));
        legacyBuilder.setInstance(legacy);
        ServiceController<?> legacyController = legacyBuilder.install();

        ServiceBuilder<?> currentBuilder = serviceTarget.addService(ServiceName.of("CURRENT"));
        TestService current = new TestService(Services.requireServerExecutor(currentBuilder));
        currentBuilder.setInstance(current);
        ServiceController<?> currentController = currentBuilder.install();

        try {
            container.awaitStability(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("Interrupted");
        }

        Assert.assertEquals(ServiceController.State.UP, executorController.getState());
        Assert.assertEquals(ServiceController.State.UP, legacyController.getState());
        Assert.assertEquals(ServiceController.State.UP, currentController.getState());

        Assert.assertSame(executorService, legacy.getValue());
        Assert.assertSame(executorService, current.getValue());
    }

    @SuppressWarnings("deprecation")
    private static class TestService implements Service<ExecutorService>, org.jboss.msc.Service {

        private final Supplier<ExecutorService> supplier;

        private ExecutorService value;

        private TestService(final Supplier<ExecutorService> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void start(StartContext context) throws StartException {
            value = supplier.get();
        }

        @Override
        public void stop(StopContext context) {
            value = null;
        }

        @Override
        public ExecutorService getValue() throws IllegalStateException, IllegalArgumentException {
            return value;
        }
    }
}
