/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.security.password;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.domain.management.logging.DomainManagementLogger;

/**
 * A {@link PasswordValidation} which wraps multiple other restrictions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CompoundRestriction implements PasswordRestriction {

    private final List<PasswordRestriction> wrapped = new ArrayList<PasswordRestriction>();
    private final boolean must;

    public CompoundRestriction(final boolean must) {
        this.must = must;
    }

    synchronized void add(final PasswordRestriction restriction) {
        wrapped.add(restriction);
    }

    public List<PasswordRestriction> getRestrictions() {
        return Collections.unmodifiableList(wrapped);
    }

    @Override
    public synchronized String getRequirementMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wrapped.size(); i++) {
            sb.append(wrapped.get(i).getRequirementMessage());
            if (i + 1 < wrapped.size()) {
                sb.append(", ");
            }
        }

        return must ? DomainManagementLogger.ROOT_LOGGER.passwordMustContainInfo(sb.toString()) : DomainManagementLogger.ROOT_LOGGER.passwordShouldContainInfo(sb.toString());
    }

    @Override
    public void validate(String userName, String password) throws PasswordValidationException {
        for (PasswordRestriction current : wrapped) {
            current.validate(userName, password);
        }

    }

}
