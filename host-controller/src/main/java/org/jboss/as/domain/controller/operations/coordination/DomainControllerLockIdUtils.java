/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.coordination;

import org.jboss.as.controller.OperationContext.AttachmentKey;

/**
 * Contains operation headers and attachments used to communicate the domain controller's lock id to the slaves.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public final class DomainControllerLockIdUtils {

    /**
     * The domain controller lock id header sent by the DC to the slave host. This is used by the slaves
     * to latch onto any ongoing operation in the DC.
     */
    public static final String DOMAIN_CONTROLLER_LOCK_ID = "domain-controller-lock-id";

    /**
     * The attachment used by the slave to keep track of the lock id on the DC (if any)
     */
    public static final AttachmentKey<Integer> DOMAIN_CONTROLLER_LOCK_ID_ATTACHMENT = AttachmentKey.create(Integer.class);

    private DomainControllerLockIdUtils() {
    }
}
