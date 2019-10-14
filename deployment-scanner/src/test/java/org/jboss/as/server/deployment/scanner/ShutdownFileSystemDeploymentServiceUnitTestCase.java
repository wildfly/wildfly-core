/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERSISTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEPLOY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.threads.AsyncFuture;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests of {@link FileSystemDeploymentService}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ShutdownFileSystemDeploymentServiceUnitTestCase {

    private static Logger logger = Logger.getLogger(ShutdownFileSystemDeploymentServiceUnitTestCase.class);

    private static long count = System.currentTimeMillis();

    private static final Random random = new Random(System.currentTimeMillis());

    private static final DiscardTaskExecutor executor = new DiscardTaskExecutor();

    private static final PathAddress resourceAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, DeploymentScannerExtension.SUBSYSTEM_NAME),
            PathElement.pathElement(DeploymentScannerExtension.SCANNERS_PATH.getKey(), DeploymentScannerExtension.DEFAULT_SCANNER_NAME));


    private static AutoDeployTestSupport testSupport;
    private File tmpDir;

    @BeforeClass
    public static void createTestSupport() throws Exception {
        testSupport = new AutoDeployTestSupport(ShutdownFileSystemDeploymentServiceUnitTestCase.class.getSimpleName());
    }

    @AfterClass
    public static void cleanup() throws Exception {
        if (testSupport != null) {
            testSupport.cleanupFiles();
        }
    }

    @Before
    public void setup() throws Exception {
        executor.clear();

        File root = testSupport.getTempDir();
        for (int i = 0; i < 200; i++) {
            tmpDir = new File(root, String.valueOf(count++));
            if (!tmpDir.exists() && tmpDir.mkdirs()) {
                break;
            }
        }

        if (!tmpDir.exists()) {
            throw new RuntimeException("cannot create tmpDir");
        }
    }

    @After
    public void tearDown() throws Exception {
        testSupport.cleanupChannels();
    }

    @Test
    public void testUncleanShutdown() throws Exception {
        File deployment = new File(tmpDir, "foo.war");
        final DiscardTaskExecutor myExecutor = new DiscardTaskExecutor();
        MockServerController sc = new MockServerController(myExecutor);
        final BlockingDeploymentOperations ops = new BlockingDeploymentOperations(sc.create());
        final FileSystemDeploymentService testee = new FileSystemDeploymentService(resourceAddress, null, tmpDir, null, sc, myExecutor);
        testee.setAutoDeployZippedContent(true);
        sc.addCompositeSuccessResponse(1);
        testSupport.createZip(deployment, 0, false, false, true, true);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> lockDone = executorService.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        synchronized (ops.lock) {
                            logger.info("Executor service should be locked");
                            while (!ops.ready) {//Waiting for deployment to start.
                                Thread.sleep(100);
                            }
                            logger.info("About to stop the scanner");
                            testee.stopScanner();
                            logger.info("Closing executor service " + myExecutor);
                            myExecutor.shutdown();
                            logger.info("Executor service should be closed");
                        }
                        return true;
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        throw new RuntimeException(ex);
                    }
                }
            });
            final File dodeploy = new File(tmpDir, "foo.war" + FileSystemDeploymentService.DO_DEPLOY);
            Files.createFile(dodeploy.toPath());
            testee.startScanner(ops);
            testee.scan();
            lockDone.get(100000, TimeUnit.MILLISECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static class MockServerController implements LocalModelControllerClient, ModelControllerClientFactory, DeploymentOperations.Factory {

        private final DiscardTaskExecutor executorService;
        private final List<ModelNode> requests = new ArrayList<ModelNode>(1);
        private final List<Response> responses = new ArrayList<Response>(1);
        private final Map<String, byte[]> added = new HashMap<String, byte[]>();
        private final Map<String, byte[]> deployed = new HashMap<String, byte[]>();
        private final Set<String> externallyDeployed = new HashSet<String>();

        @Override
        public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) {
            ModelNode rawOp = operation.getOperation();
            requests.add(rawOp);
            return OperationResponse.Factory.createSimple(processOp(rawOp));
        }

        @Override
        public AsyncFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
            logger.info("Executing deploy command from MockServerController, its executor service should be closed");
            return executorService.submit(new Callable<ModelNode>() {
                @Override
                public ModelNode call() throws Exception {
                    return execute(operation);
                }
            });
        }

        @Override
        public AsyncFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {

        }

        @Override
        public DeploymentOperations create() {
            return new DefaultDeploymentOperations(this, executorService);
        }

        @Override
        public LocalModelControllerClient createClient(Executor executor) {
            return this;
        }

        @Override
        public LocalModelControllerClient createSuperUserClient(Executor executor, boolean forUserCalls) {
            return this;
        }

        private static class Response {

            private final boolean ok;
            private final ModelNode rsp;

            Response(boolean ok, ModelNode rsp) {
                this.ok = ok;
                this.rsp = rsp;
            }
        }

        MockServerController(String... existingDeployments) {
            this(executor, existingDeployments);
        }

        MockServerController(DiscardTaskExecutor executorService, String... existingDeployments) {
            for (String dep : existingDeployments) {
                added.put(dep, randomHash());
                deployed.put(dep, added.get(dep));
            }
            this.executorService = executorService;
        }

        public void addCompositeSuccessResponse(int count) {
            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                result.get("step-" + i, OUTCOME).set(SUCCESS);
                result.get("step-" + i, RESULT);
            }

            responses.add(new Response(true, rsp));
        }

        public void addCompositeFailureResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(FAILED);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    result.get(step, OUTCOME).set(FAILED);
                    result.get(step, RESULT);
                    result.get(step, ROLLED_BACK).set(true);
                } else if (i == failureStep) {
                    result.get(step, OUTCOME).set(FAILED);
                    result.get(step, FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
                    result.get(step, ROLLED_BACK).set(true);
                } else {
                    result.get(step, OUTCOME).set(CANCELLED);
                }
            }
            rsp.get(FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
            rsp.get(ROLLED_BACK).set(true);

            responses.add(new Response(true, rsp));
        }

        public void addCompositeFailureResultResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            ModelNode failedStep = result.get("step-" + failureStep);
            failedStep.get(OUTCOME).set(SUCCESS);
            ModelNode stepResult = failedStep.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    stepResult.get(step, OUTCOME).set(SUCCESS);
                    stepResult.get(step, RESULT);
                    stepResult.get(step, ROLLED_BACK).set(true);
                } else if (i == failureStep) {
                    stepResult.get(step, OUTCOME).set(FAILED);
                    stepResult.get(step, FAILURE_DESCRIPTION).set(new ModelNode().set("true failed step"));
                    stepResult.get(step, ROLLED_BACK).set(true);
                } else {
                    stepResult.get(step, OUTCOME).set(CANCELLED);
                }
            }
            rsp.get(FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
            rsp.get(ROLLED_BACK).set(true);

            responses.add(new Response(true, rsp));
        }

        public void addPartialCompositeFailureResultResponse(int count, int failureStep) {

            if (count < failureStep) {
                throw new IllegalArgumentException("failureStep must be > count");
            }

            ModelNode rsp = new ModelNode();
            rsp.get(OUTCOME).set(SUCCESS);
            ModelNode result = rsp.get(RESULT);
            for (int i = 1; i <= count; i++) {
                String step = "step-" + i;
                if (i < failureStep) {
                    result.get(step, OUTCOME).set(SUCCESS);
                    result.get(step, RESULT);
                    result.get(step, RESULT, "step-1", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-1", RESULT);
                    result.get(step, RESULT, "step-2", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-2", RESULT);
                } else if (i == failureStep) {
                    result.get(step, OUTCOME).set(SUCCESS);
                    result.get(step, RESULT);
                    result.get(step, RESULT, "step-1", OUTCOME).set(SUCCESS);
                    result.get(step, RESULT, "step-1", RESULT);
                    result.get(step, RESULT, "step-2", OUTCOME).set(FAILED);
                    result.get(step, RESULT, "step-2", FAILURE_DESCRIPTION).set(new ModelNode().set("badness happened"));
                } else {
                    result.get(step, OUTCOME).set(CANCELLED);
                }
            }

            responses.add(new Response(true, rsp));
        }

        private ModelNode getDeploymentNamesResponse() {
            ModelNode content = new ModelNode();
            content.get(OUTCOME).set(SUCCESS);
            ModelNode result = content.get(RESULT);
            result.setEmptyObject();
            for (String deployment : added.keySet()) {
                result.get(deployment, ENABLED).set(deployed.containsKey(deployment));
                result.get(deployment, PERSISTENT).set(externallyDeployed.contains(deployment));
            }
            return content;
        }

        private ModelNode processOp(ModelNode op) {

            String opName = op.require(OP).asString();
            if (READ_CHILDREN_RESOURCES_OPERATION.equals(opName)) {
                return getDeploymentNamesResponse();
            } else if (COMPOSITE.equals(opName)) {
                for (ModelNode child : op.require(STEPS).asList()) {
                    opName = child.require(OP).asString();
                    if (COMPOSITE.equals(opName)) {
                        return processOp(child);
                    }

                    if (responses.isEmpty()) {
                        Assert.fail("unexpected request " + op);
                        return null; // unreachable
                    }

                    if (!responses.get(0).ok) {
                        // don't change state for a failed response
                        continue;
                    }

                    PathAddress address = PathAddress.pathAddress(child.require(OP_ADDR));
                    if (ADD.equals(opName)) {
                        // Since AS7-431 the content is no longer managed
                        //added.put(address.getLastElement().getValue(), child.require(CONTENT).require(0).require(HASH).asBytes());
                        added.put(address.getLastElement().getValue(), randomHash());
                    } else if (REMOVE.equals(opName)) {
                        added.remove(address.getLastElement().getValue());
                    } else if (DEPLOY.equals(opName)) {
                        String name = address.getLastElement().getValue();
                        deployed.put(name, added.get(name));
                    } else if (UNDEPLOY.equals(opName)) {
                        deployed.remove(address.getLastElement().getValue());
                    } else if (FULL_REPLACE_DEPLOYMENT.equals(opName)) {
                        String name = child.require(NAME).asString();
                        // Since AS7-431 the content is no longer managed
                        //byte[] hash = child.require(CONTENT).require(0).require(HASH).asBytes();
                        final byte[] hash = randomHash();
                        added.put(name, hash);
                        deployed.put(name, hash);
                    } else {
                        throw new IllegalArgumentException("unexpected step " + opName);
                    }
                }
                return responses.remove(0).rsp;
            } else {
                throw new IllegalArgumentException("unexpected operation " + opName);
            }
        }

    }

    private static class DiscardTaskExecutor extends ScheduledThreadPoolExecutor {

        private final List<Runnable> tasks = new ArrayList<Runnable>();
        private final Set<CallOnGetFuture<?>> futures = new HashSet<CallOnGetFuture<?>>();
        private DiscardTaskExecutor() {
            super(0);
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit delayUnit) {
            tasks.add(command);
            return new RunnableScheduledFuture<Object>() {
                private FutureTask<?> task = new FutureTask<>(command, new Object());

                @Override
                public boolean isPeriodic() {
                    return false;
                }

                @Override
                public void run() {
                    task.run();
                }

                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    logger.info("Task is being cancelled " +  mayInterruptIfRunning);
                    return task.cancel(mayInterruptIfRunning);
                }

                @Override
                public boolean isCancelled() {
                    return task.isCancelled();
                }

                @Override
                public boolean isDone() {
                    return task.isDone();
                }

                @Override
                public Object get() throws InterruptedException, ExecutionException {
                    return task.get();
                }

                @Override
                public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                   return task.get(timeout, unit);
                }

                @Override
                public long getDelay(TimeUnit unit) {
                    return delayUnit.convert(delay, unit);
                }

                @Override
                public int compareTo(Delayed o) {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            };
        }

        @Override
        public <T> AsyncFuture<T> submit(Callable<T> tCallable) {
            if (isShutdown() || isTerminating()) {
                throw new RejectedExecutionException("DiscardTaskExecutor has shutdown we can't run " + tCallable);
            }
            CallOnGetFuture<T> future = new CallOnGetFuture<>(tCallable);
            futures.add(future);
            return future;
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> superList = super.shutdownNow();
            for(CallOnGetFuture<?> future : futures) {
                future.cancel(true);
            }
            superList.addAll(tasks);
            return superList;
        }

        @Override
        public void shutdown() {
            super.shutdown();
            for(CallOnGetFuture<?> future : futures) {
                future.cancel(false);
            }
        }



        void clear() {
            tasks.clear();
        }
    }

    private static class CallOnGetFuture<T> implements AsyncFuture<T> {

        final Callable<T> callable;
        private boolean cancelled = false;

        private CallOnGetFuture(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public boolean cancel(boolean interrupt) {
            this.cancelled = true;
            logger.info("CallOnGetFuture is to be cancelled " + callable);
            if (interrupt) {
                logger.info("CallOnGetFuture interrupted " + callable);
                Thread.currentThread().interrupt();
                return true;
            }
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                logger.info("CallOnGetFuture get " + callable);
                return callable.call();
            } catch (InterruptedException | ExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public T get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }

        @Override
        public AsyncFuture.Status await() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncFuture.Status await(long timeout, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getUninterruptibly() throws CancellationException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public T getUninterruptibly(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncFuture.Status awaitUninterruptibly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncFuture.Status awaitUninterruptibly(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncFuture.Status getStatus() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A> void addListener(AsyncFuture.Listener<? super T, A> listener, A attachment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void asyncCancel(boolean interruptionDesired) {
            throw new UnsupportedOperationException();
        }
    }

    private static class BlockingDeploymentOperations implements DeploymentOperations {

        private volatile boolean ready = false;
        private final DeploymentOperations delegate;
        private final Object lock = new Object();

        BlockingDeploymentOperations(final DeploymentOperations delegate) {
            this.delegate = delegate;
        }

        @Override
        public Future<ModelNode> deploy(final ModelNode operation, ExecutorService executorService) {
            ready = true;
            logger.info("Ready to deploy");
            synchronized(lock) {
                logger.info("Deploying on delegate.");
                return delegate.deploy(operation, null);
            }
        }

        @Override
        public Map<String, Boolean> getDeploymentsStatus() {
            return delegate.getDeploymentsStatus();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public Set<String> getUnrelatedDeployments(ModelNode owner) {
            return delegate.getUnrelatedDeployments(owner);
        }

    }

    private static byte[] randomHash() {
        final byte[] hash = new byte[20];
        random.nextBytes(hash);
        return hash;
    }
}
