/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.client.helpers.domain.UpdateFailedException;

/**
 * Encapsulates the results provided by the domain controller in response
 * to a request to apply an update to the domain model and to the relevant
 * hosts. Used to provide input parameters to the callback methods in a
 * {@link DomainUpdateApplier}.
 *
 * @author Brian Stansberry
 */
public class BasicDomainUpdateResult implements Serializable {

    private static final long serialVersionUID = -3525117172870002485L;

    private final UpdateFailedException domainFailure;
    private final Map<String, UpdateFailedException> hostFailures = new HashMap<String, UpdateFailedException>();
    private final boolean cancelled;
    private final boolean rolledBack;

    public BasicDomainUpdateResult(boolean cancelled) {
        this.cancelled = cancelled;
        this.rolledBack = !cancelled;
        this.domainFailure = null;
    }

    public BasicDomainUpdateResult(final UpdateFailedException domainFailure, final boolean rolledBack) {
        this.domainFailure = domainFailure;
        this.cancelled = false;
        this.rolledBack = rolledBack;
    }

    public BasicDomainUpdateResult(final Map<String, UpdateFailedException> hostFailures, final boolean rolledBack) {
        this.domainFailure = null;
        if (hostFailures != null) {
            this.hostFailures.putAll(hostFailures);
        }
        this.cancelled = false;
        this.rolledBack = rolledBack;
    }

    public BasicDomainUpdateResult() {
        this.domainFailure = null;
        this.cancelled = false;
        this.rolledBack = false;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public UpdateFailedException getDomainFailure() {
        return domainFailure;
    }

    public Map<String, UpdateFailedException> getHostFailures() {
        return Collections.unmodifiableMap(hostFailures);
    }


}
