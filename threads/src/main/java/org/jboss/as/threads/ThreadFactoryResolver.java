/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.as.controller.OperationContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Resolves the service name of the thread factory a thread pool service should use. Provides a default thread factory
 * for a thread pool in case the thread pool does not have a specifically configured thread factory. The absence of a
 * specifically configured thread pool would be typical.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ThreadFactoryResolver {

    /**
     * Resolves the service name of the thread factory a thread pool service should use, providing a default thread
     * factory in case the thread pool does not have a specifically configured thread factory.
     *
     *
     * @param threadFactoryName the simple name of the thread factory. Typically a reference value from
     *                          the thread pool resource's configuration. Can be {@code null} in which case a
     *                          default thread factory should be returned.
     * @param threadPoolName the name of the thread pool
     * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
     * @param serviceTarget service target that is installing the thread pool service; can be used to install
     *                      a {@link org.jboss.as.threads.ThreadFactoryService}
     * @return the {@link ServiceName} of the {@link ThreadFactoryService} the thread pool should use. Cannot be
     *         {@code null}
     */
    ServiceName resolveThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName,
                                     ServiceTarget serviceTarget);

    /**
     * Releases the thread factory, doing any necessary cleanup, such as removing a default thread factory that
     * was installed by {@link #resolveThreadFactory(String, String, org.jboss.msc.service.ServiceName, org.jboss.msc.service.ServiceTarget)}.
     *
     * @param threadFactoryName the simple name of the thread factory. Typically a reference value from
     *                          the thread pool resource's configuration. Can be {@code null} in which case a
     *                          default thread factory should be released.
     * @param threadPoolName the name of the thread pool
     * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
     * @param context the context of the current operation; can be used to perform any necessary
     *                {@link OperationContext#removeService(ServiceName) service removals}
     */
    void releaseThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName,
                              OperationContext context);

    /**
     * Base class for {@code ThreadFactoryResolver} implementations that handles the case of a null
     * {@code threadFactoryName} by installing a {@link ThreadFactoryService} whose service name is
     * the service name of the thread pool with {@code thread-factory} appended.
     */
    abstract class AbstractThreadFactoryResolver implements ThreadFactoryResolver {

        @Override
        public ServiceName resolveThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName, ServiceTarget serviceTarget) {
            ServiceName threadFactoryServiceName;

            if (threadFactoryName != null) {
                threadFactoryServiceName = resolveNamedThreadFactory(threadFactoryName, threadPoolName, threadPoolServiceName);
            } else {
                // Create a default
                threadFactoryServiceName = resolveDefaultThreadFactory(threadPoolName, threadPoolServiceName, serviceTarget);
            }
            return threadFactoryServiceName;
        }

        @Override
        public void releaseThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName,
                              OperationContext context) {
            if (threadFactoryName != null) {
                releaseNamedThreadFactory(threadFactoryName, threadPoolName, threadPoolServiceName, context);
            } else {
                releaseDefaultThreadFactory(threadPoolServiceName, context);
            }
        }

        /**
         * Create a service name to use for the thread factory in the case where a simple name for the factory was provided.
         *
         * @param threadFactoryName the simple name of the thread factory. Will not be {@code null}
         * @param threadPoolName the simple name of the related thread pool
         * @param threadPoolServiceName the full service name of the thread pool
         *
         * @return the {@link ServiceName} of the {@link ThreadFactoryService} the thread pool should use. Cannot be
         *         {@code null}
         */
        protected abstract ServiceName resolveNamedThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName);

        /**
         * Handles the work of {@link #releaseThreadFactory(String, String, ServiceName, OperationContext)} for the case
         * where {@code threadFactoryName} is not {@code null}. This default implementation does nothing, assuming
         * the thread factory is independently managed from the pool.
         *
         * @param threadFactoryName the simple name of the thread factory. Will not be {@code null}
         * @param threadPoolName    the simple name of the related thread pool
         * @param threadPoolServiceName the full service name of the thread pool
         * @param context  the context of the current operation; can be used to perform any necessary
         *                {@link OperationContext#removeService(ServiceName) service removals}
         */
        @SuppressWarnings("unused")
        protected void releaseNamedThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName,
                              OperationContext context) {
            // no-op
        }

        /**
         * Installs a {@link ThreadFactoryService} whose service name is the service name of the thread pool with {@code thread-factory} appended.
         *
         * @param threadPoolName the name of the thread pool
         * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
         * @param serviceTarget service target that is installing the thread pool service; can be used to install
         *                      a {@link org.jboss.as.threads.ThreadFactoryService}
         * @return the {@link ServiceName} of the {@link ThreadFactoryService} the thread pool should use. Cannot be
         *         {@code null}
         */
        private ServiceName resolveDefaultThreadFactory(String threadPoolName, ServiceName threadPoolServiceName,
                                                        ServiceTarget serviceTarget) {
            final ServiceName threadFactoryServiceName = threadPoolServiceName.append("thread-factory");
            final ThreadFactoryService service = new ThreadFactoryService();
            service.setThreadGroupName(getThreadGroupName(threadPoolName));
            service.setNamePattern("%G - %t");
            serviceTarget.addService(threadFactoryServiceName, service).install();
            return threadFactoryServiceName;
        }

        protected String getThreadGroupName(String threadPoolName) {
            return threadPoolName + "-threads";
        }

        /**
         * Removes any default thread factory installed in {@link #resolveDefaultThreadFactory(String, org.jboss.msc.service.ServiceName, org.jboss.msc.service.ServiceTarget)}.
         *
         * @param threadPoolServiceName the full name of the {@link org.jboss.msc.service.Service} that provides the thread pool
         * @param context the context of the current operation; can be used to perform any necessary
     *                {@link OperationContext#removeService(ServiceName) service removals}
         */
        private void releaseDefaultThreadFactory(ServiceName threadPoolServiceName, OperationContext context) {
            final ServiceName threadFactoryServiceName = threadPoolServiceName.append("thread-factory");
            context.removeService(threadFactoryServiceName);
        }
    }

    /**
     * Extends {@link AbstractThreadFactoryResolver} to deal with named thread factories by appending their
     * simple name to a provided base name.
     */
    class SimpleResolver extends AbstractThreadFactoryResolver {

        final ServiceName threadFactoryServiceNameBase;

        public SimpleResolver(ServiceName threadFactoryServiceNameBase) {
            this.threadFactoryServiceNameBase = threadFactoryServiceNameBase;
        }

        @Override
        public ServiceName resolveNamedThreadFactory(String threadFactoryName, String threadPoolName, ServiceName threadPoolServiceName) {
            return threadFactoryServiceNameBase.append(threadFactoryName);
        }
    }
}
