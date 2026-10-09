/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartException;
import org.junit.Test;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * Exercises the shared construction and invalidation contract without starting an MSC container.
 */
public class ElytronDoohickeyTestCase {

    @Test
    public void serviceReusesEarlyValue() throws Exception {
        TestDoohickey doohickey = new TestDoohickey(new CountDownLatch(0), new CountDownLatch(0));
        Object value = doohickey.apply(context());

        assertSame(value, doohickey.getForService(() -> {
            throw new StartException("The service must reuse the early value");
        }));
        assertEquals(1, doohickey.earlyCreations.get());
    }

    @Test
    public void earlyAccessReusesServiceValue() throws Exception {
        TestDoohickey doohickey = new TestDoohickey(new CountDownLatch(0), new CountDownLatch(0));
        Object value = new Object();

        assertSame(value, doohickey.getForService(() -> value));
        assertSame(value, doohickey.apply(context()));
        assertEquals(0, doohickey.earlyCreations.get());
    }

    @Test
    public void resetWaitsForConstructionAndClearsAssociatedState() throws Exception {
        CountDownLatch associatedValueSet = new CountDownLatch(1);
        CountDownLatch finishConstruction = new CountDownLatch(1);
        CountDownLatch resetStarted = new CountDownLatch(1);
        TestDoohickey doohickey = new TestDoohickey(associatedValueSet, finishConstruction);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> earlyValue = executor.submit(() -> doohickey.apply(context()));
            assertTrue(associatedValueSet.await(5, TimeUnit.SECONDS));

            Future<?> reset = executor.submit(() -> {
                resetStarted.countDown();
                doohickey.reset();
            });
            assertTrue(resetStarted.await(5, TimeUnit.SECONDS));
            // The constructor still holds the Doohickey lock, so invalidation cannot pass it.
            try {
                reset.get(50, TimeUnit.MILLISECONDS);
                fail("Reset completed while construction still held the lock");
            } catch (TimeoutException expected) {
                // Construction still owns the lock.
            }

            finishConstruction.countDown();
            earlyValue.get(5, TimeUnit.SECONDS);
            reset.get(5, TimeUnit.SECONDS);
            assertNull(doohickey.cachedValue());
            assertNull(doohickey.associatedValue);

            Object restarted = new Object();
            assertSame(restarted, doohickey.getForService(() -> {
                doohickey.associatedValue = restarted;
                return restarted;
            }));
            assertSame(restarted, doohickey.associatedValue);
        } finally {
            finishConstruction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void resetWaitsForServiceHandoff() throws Exception {
        TestDoohickey doohickey = new TestDoohickey(new CountDownLatch(0), new CountDownLatch(0));
        CountDownLatch valuePublished = new CountDownLatch(1);
        CountDownLatch finishHandoff = new CountDownLatch(1);
        CountDownLatch resetStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> handoff = executor.submit(() -> DoohickeySimultaneity.withLockForLifecycle(() -> {
                Object value = doohickey.getForService(() -> {
                    Object created = new Object();
                    doohickey.associatedValue = created;
                    return created;
                });
                valuePublished.countDown();
                if (!finishHandoff.await(5, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Timed out waiting to complete service handoff");
                }
                assertSame(value, doohickey.associatedValue);
                return value;
            }));
            assertTrue(valuePublished.await(5, TimeUnit.SECONDS));

            Future<?> reset = executor.submit(() -> {
                resetStarted.countDown();
                doohickey.reset();
            });
            assertTrue(resetStarted.await(5, TimeUnit.SECONDS));
            try {
                reset.get(50, TimeUnit.MILLISECONDS);
                fail("Reset completed during service handoff");
            } catch (TimeoutException expected) {
                // The service still owns the lifecycle lock.
            }

            finishHandoff.countDown();
            handoff.get(5, TimeUnit.SECONDS);
            reset.get(5, TimeUnit.SECONDS);
            assertNull(doohickey.cachedValue());
            assertNull(doohickey.associatedValue);
        } finally {
            finishHandoff.countDown();
            executor.shutdownNow();
        }
    }

    private static OperationContext context() {
        return (OperationContext) Proxy.newProxyInstance(OperationContext.class.getClassLoader(),
                new Class<?>[] { OperationContext.class }, (proxy, method, args) -> {
                    if (method.getName().equals("readResourceFromRoot")) {
                        return Resource.Factory.create();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestDoohickey extends ElytronDoohickey<Object> {

        private final CountDownLatch associatedValueSet;
        private final CountDownLatch finishConstruction;
        private final AtomicInteger earlyCreations = new AtomicInteger();
        private volatile Object associatedValue;

        private TestDoohickey(CountDownLatch associatedValueSet, CountDownLatch finishConstruction) {
            super(PathAddress.pathAddress(PathElement.pathElement("test", "resource")));
            this.associatedValueSet = associatedValueSet;
            this.finishConstruction = finishConstruction;
        }

        @Override
        protected void resolveRuntime(ModelNode model, OperationContext context) {
        }

        @Override
        protected ExceptionSupplier<Object, StartException> prepareServiceSupplier(OperationContext context,
                CapabilityServiceBuilder<?> serviceBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Object createImmediately(OperationContext context) throws OperationFailedException {
            Object created = new Object();
            associatedValue = created;
            earlyCreations.incrementAndGet();
            associatedValueSet.countDown();
            try {
                if (!finishConstruction.await(5, TimeUnit.SECONDS)) {
                    throw new OperationFailedException("Timed out waiting to complete construction");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OperationFailedException(e);
            }
            return created;
        }

        @Override
        protected void onReset() {
            associatedValue = null;
        }
    }
}
