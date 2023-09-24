/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public interface TransformationContext {

    /**
     * Get the transformation target.
     *
     * @return the transformation target
     */
    TransformationTarget getTarget();

    /**
     * Get the type of process in which this operation is executing.
     *
     * @return the process type. Will not be {@code null}
     */
    ProcessType getProcessType();

    /**
     * Gets the running mode of the process.
     *
     * @return the running mode. Will not be {@code null}
     */
    RunningMode getRunningMode();

    /**
     * Get the management resource registration.
     *
     * @param address the path address
     * @return the registration
     */
    ImmutableManagementResourceRegistration getResourceRegistration(PathAddress address);

    /**
     * Get the management resource registration.
     *
     * @param address the path address
     * @return the registration
     */
    ImmutableManagementResourceRegistration getResourceRegistrationFromRoot(PathAddress address);

    /**
     * Read a model resource.
     *
     * @param address the path address
     * @return a read-only resource
     */
    Resource readResource(PathAddress address);

    /**
     * Read a model resource from the root.
     *
     * @param address the path address
     * @return the read-only resource
     */
    Resource readResourceFromRoot(PathAddress address);

    /**
     * Returns Transformers logger that must be used for reporting any problems with transformation
     *
     * @return TransformersLogger associated with target host
     */
    TransformersLogger getLogger();

    /**
     * Retrieves an object that has been attached to this context.
     *
     * @param key the key to the attachment.
     * @param <T> the value type of the attachment.
     *
     * @return the attachment if found otherwise {@code null}.
     */
    <T> T getAttachment(OperationContext.AttachmentKey<T> key);

    /**
     * Attaches an arbitrary object to this context.
     *
     * @param key   they attachment key used to ensure uniqueness and used for retrieval of the value.
     * @param value the value to store.
     * @param <T>   the value type of the attachment.
     *
     * @return the previous value associated with the key or {@code null} if there was no previous value.
     */
    <T> T attach(OperationContext.AttachmentKey<T> key, T value);

    /**
     * Attaches an arbitrary object to this context only if the object was not already attached. If a value has already
     * been attached with the key provided, the current value associated with the key is returned.
     *
     * @param key   they attachment key used to ensure uniqueness and used for retrieval of the value.
     * @param value the value to store.
     * @param <T>   the value type of the attachment.
     *
     * @return the previous value associated with the key or {@code null} if there was no previous value.
     */
    <T> T attachIfAbsent(OperationContext.AttachmentKey<T> key, T value);

    /**
     * Detaches or removes the value from this context.
     *
     * @param key the key to the attachment.
     * @param <T> the value type of the attachment.
     *
     * @return the attachment if found otherwise {@code null}.
     */
    <T> T detach(OperationContext.AttachmentKey<T> key);
}
