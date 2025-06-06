/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.OperationContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Resolves the service name of the executor service a thread pool service should use if it cannot itself accept
 * a task. Optionally provides an executor service for the thread pool to use in case the thread pool does not have a
 * specifically configured handoff executor. The absence of a specifically configured thread pool would be typical.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface HandoffExecutorResolver {

    /**
     * Resolves the service name of the handoff executor a thread pool service should use, optionally providing a default
     * executor in case the thread pool does not have a specifically configured handoff executor.
     *
     *
     * @param handoffExecutorName the simple name of the handoff executor. Typically a reference value from
     *                          the thread pool resource's configuration. Can be {@code null} in which case a
     *                          default handoff executor may be returned.
     * @param threadPoolName the name of the thread pool
     * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
     * @param serviceTarget service target that is installing the thread pool service; can be used to install
     *                      a {@link org.jboss.as.threads.ThreadFactoryService}
     * @return the {@link ServiceName} of the executor service the thread pool should use. May be {@link null}
     */
    ServiceName resolveHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName,
                                       ServiceTarget serviceTarget);

    /**
     * Releases the handoff executor, doing any necessary cleanup, such as removing a default executor that
     * was installed by {@link #resolveHandoffExecutor(String, String, org.jboss.msc.service.ServiceName, org.jboss.msc.service.ServiceTarget)}.
     *
     * @param handoffExecutorName the simple name of the thread factory. Typically a reference value from
     *                          the thread pool resource's configuration. Can be {@code null} in which case a
     *                          default thread factory should be released.
     * @param threadPoolName the name of the thread pool
     * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
     * @param context the context of the current operation; can be used to perform any necessary
     *                {@link OperationContext#removeService(ServiceName) service removals}
     */
    void releaseHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName,
                                OperationContext context);

    /**
     * Base class for {@code ThreadFactoryResolver} implementations that handles the case of a null
     * {@code threadFactoryName} by installing a {@link ThreadFactoryService} whose service name is
     * the service name of the thread pool with {@code thread-factory} appended.
     */
    abstract class AbstractThreadFactoryResolver implements HandoffExecutorResolver {

        @Override
        public ServiceName resolveHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName, ServiceTarget serviceTarget) {
            ServiceName threadFactoryServiceName;

            if (handoffExecutorName != null) {
                threadFactoryServiceName = resolveNamedHandoffExecutor(handoffExecutorName, threadPoolName, threadPoolServiceName);
            } else {
                // Create a default
                threadFactoryServiceName = resolveDefaultHandoffExecutor(threadPoolName, threadPoolServiceName, serviceTarget);
            }
            return threadFactoryServiceName;
        }

        @Override
        public void releaseHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName,
                                           OperationContext context) {
            if (handoffExecutorName != null) {
                releaseNamedHandoffExecutor(handoffExecutorName, threadPoolName, threadPoolServiceName, context);
            } else {
                releaseDefaultHandoffExecutor(threadPoolServiceName, context);
            }
        }

        /**
         * Create a service name to use for the thread factory in the case where a simple name for the factory was provided.
         *
         * @param handoffExecutorName the simple name of the thread factory. Will not be {@code null}
         * @param threadPoolName the simple name of the related thread pool
         * @param threadPoolServiceName the full service name of the thread pool
         *
         * @return the {@link ServiceName} of the {@link ThreadFactoryService} the thread pool should use. Cannot be
         *         {@code null}
         */
        protected abstract ServiceName resolveNamedHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName);

        /**
         * Handles the work of {@link #releaseHandoffExecutor(String, String, ServiceName, OperationContext)} for the case
         * where {@code threadFactoryName} is not {@code null}. This default implementation does nothing, assuming
         * the thread factory is independently managed from the pool.
         *
         * @param handoffExecutorName the simple name of the thread factory. Will not be {@code null}
         * @param threadPoolName    the simple name of the related thread pool
         * @param threadPoolServiceName the full service name of the thread pool
         * @param context  the context of the current operation; can be used to perform any necessary
         *                {@link OperationContext#removeService(ServiceName) service removals}
         */
        @SuppressWarnings("unused")
        protected void releaseNamedHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName,
                                                   OperationContext context) {
            // no-op
        }

        /**
         * Optionally provides the service name of a default handoff executor. This implementation simply returns
         * {@code null}, meaning there is no default.
         *
         * @param threadPoolName the name of the thread pool
         * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
         * @param serviceTarget service target that is installing the thread pool service; can be used to install
         *                      a {@link org.jboss.as.threads.ThreadFactoryService}
         * @return the {@link ServiceName} of the {@link ThreadFactoryService} the thread pool should use. May be {@code null}
         */
        @SuppressWarnings("unused")
        protected ServiceName resolveDefaultHandoffExecutor(String threadPoolName, ServiceName threadPoolServiceName,
                                                            ServiceTarget serviceTarget) {
            return null;
        }

        /**
         * Removes any default thread factory installed in {@link #resolveDefaultHandoffExecutor(String, org.jboss.msc.service.ServiceName, org.jboss.msc.service.ServiceTarget)}.
         * This default implementation does nothing, but any subclass that installs a default service should override this
         * method to remove it.
         *
         * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
         * @param context the context of the current operation; can be used to perform any necessary
         *                {@link OperationContext#removeService(ServiceName) service removals}
         */
        @SuppressWarnings("unused")
        protected void releaseDefaultHandoffExecutor(ServiceName threadPoolServiceName, OperationContext context) {
            // nothing to do since we didn't create anything
        }
    }

    /**
     * Extends {@link AbstractThreadFactoryResolver} to deal with named thread factories by appending their
     * simple name to a provided base name.
     */
    class SimpleResolver extends AbstractThreadFactoryResolver {

        final ServiceName handoffExecutorServiceNameBase;

        public SimpleResolver(ServiceName handoffExecutorServiceNameBase) {
            this.handoffExecutorServiceNameBase = handoffExecutorServiceNameBase;
        }

        @Override
        public ServiceName resolveNamedHandoffExecutor(String handoffExecutorName, String threadPoolName, ServiceName threadPoolServiceName) {
            return handoffExecutorServiceNameBase.append(handoffExecutorName);
        }
    }
}
