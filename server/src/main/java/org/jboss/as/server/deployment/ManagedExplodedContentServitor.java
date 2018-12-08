/*
Copyright 2016 Red Hat, Inc.

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

package org.jboss.as.server.deployment;

import static org.jboss.as.server.Services.requireServerExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Provides the virtual file for use with managed exploded content by copying the content out of the
 * content repo to the jboss.server.data.dir.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class ManagedExplodedContentServitor implements Service {

    private final Consumer<VirtualFile> virtualFileConsumer;
    private final Supplier<ContentRepository> contentRepositorySupplier;
    private final Supplier<ServerEnvironment> serverEnvironmentSupplier;
    private final Supplier<ExecutorService> executorSupplier;
    private final String managementName;
    private final byte[] hash;
    private volatile Path deploymentRoot;

    static ServiceController<?> addService(final ServiceTarget serviceTarget, final ServiceName serviceName, final String managementName, final byte[] hash) {
        final ServiceBuilder<?> sb = serviceTarget.addService(serviceName);
        final Consumer<VirtualFile> vfConsumer = sb.provides(serviceName);
        final Supplier<ContentRepository> crSupplier = sb.requires(ContentRepository.SERVICE_NAME);
        final Supplier<ServerEnvironment> seSupplier = sb.requires(ServerEnvironmentService.SERVICE_NAME);
        final Supplier<ExecutorService> esSupplier = requireServerExecutor(sb);
        sb.setInstance(new ManagedExplodedContentServitor(managementName, hash, vfConsumer, crSupplier, seSupplier, esSupplier));
        return sb.install();
    }

    private ManagedExplodedContentServitor(final String managementName, final byte[] hash,
                                           final Consumer<VirtualFile> virtualFileConsumer,
                                           final Supplier<ContentRepository> contentRepositorySupplier,
                                           final Supplier<ServerEnvironment> serverEnvironmentSupplier,
                                           final Supplier<ExecutorService> executorSupplier) {
        this.managementName = managementName;
        this.hash = hash;
        this.virtualFileConsumer = virtualFileConsumer;
        this.contentRepositorySupplier = contentRepositorySupplier;
        this.serverEnvironmentSupplier = serverEnvironmentSupplier;
        this.executorSupplier = executorSupplier;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        final Path root = DeploymentHandlerUtil.getExplodedDeploymentRoot(serverEnvironmentSupplier.get(), managementName);

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    CountDownLatch latch = asyncCleanup(root);
                    if (latch != null) {
                        try {
                            if (!latch.await(60, TimeUnit.SECONDS)) {
                                // TODO proper message
                                context.failed(new StartException());
                                return;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            // TODO proper message
                            context.failed(new StartException());
                            return;
                        }
                    }

                    Files.createDirectories(root.getParent());
                    contentRepositorySupplier.get().copyExplodedContent(hash, root);
                    deploymentRoot = root;
                    virtualFileConsumer.accept(VFS.getChild(deploymentRoot.toAbsolutePath().toString()));
                    context.complete();
                } catch (IOException | ExplodedContentException e) {
                    context.failed(new StartException(e));
                }
            }
        };
        try {
            executorSupplier.get().execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

    @Override
    public void stop(StopContext context) {
        final Path theRoot = deploymentRoot;
        deploymentRoot = null;
        if (theRoot != null) {

            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        virtualFileConsumer.accept(null);
                        CountDownLatch latch = asyncCleanup(theRoot);
                        if (latch != null) {
                            try {
                                if (!latch.await(60, TimeUnit.SECONDS)) {
                                    // TODO log
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                // TODO log
                            }
                        }
                    } catch (IOException e) {
                        // TODO log
                    } finally {
                        context.complete();
                    }
                }
            };
            try {
                executorSupplier.get().execute(task);
            } catch (RejectedExecutionException e) {
                task.run();
            } finally {
                context.asynchronous();
            }

        }
    }

    private CountDownLatch asyncCleanup(Path root) throws IOException {
        final CountDownLatch result;
        if (root.toFile().exists()) {
            result = new CountDownLatch(1);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        recursiveDelete(root);
                    } finally {
                        result.countDown();
                    }
                }
            };
            executorSupplier.get().submit(r);
        } else {
            result = null;
        }
        return result;
    }

    private static void recursiveDelete(Path path) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.list(path)){
                files.forEach(ManagedExplodedContentServitor::recursiveDelete);
            } catch (IOException e) {
                // TODO log
            }
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // TODO log
        }
    }
}
