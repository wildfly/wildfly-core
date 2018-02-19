/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller;

import static java.time.Instant.now;
import static org.jboss.as.controller.audit.JsonAuditLogItemFormatter.REMOTE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_DATE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.net.InetAddress;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;

/**
 * Collects configuration changes.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public interface ConfigurationChangesCollector {

    ConfigurationChangesCollectorImpl INSTANCE = new ConfigurationChangesCollectorImpl(0);

    void addConfigurationChanges(ConfigurationChange change);

    List<ModelNode> getChanges();

    void setMaxHistory(int maxHistory);

    boolean trackAllowed();

    void deactivate();

    static class ConfigurationChangesCollectorImpl implements ConfigurationChangesCollector {

        private final TreeSet<ConfigurationChange> history = new TreeSet<>();
        private int maxHistory;

        private ConfigurationChangesCollectorImpl(final int maxHistory) {
            this.maxHistory = maxHistory;
        }

        @Override
        public void addConfigurationChanges(ConfigurationChange change) {
            synchronized (history) {
                if (history.size() == maxHistory) {
                    history.remove(history.last());
                }
                history.add(change);
            }
        }

        @Override
        public void setMaxHistory(int maxHistory) {
            synchronized (history) {
                this.maxHistory = maxHistory;
                while(maxHistory < history.size()) {
                    history.remove(history.last());
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<ModelNode> getChanges() {
            TreeSet<ConfigurationChange> changes;
            synchronized (history) {
                changes = new TreeSet<>(history);
            }
            ModelNode result = new ModelNode().setEmptyList();
            for (ConfigurationChange change : changes) {
                result.add(change.asModel());
            }
            return result.asList();
        }

        @Override
        public boolean trackAllowed() {
            synchronized (history) {
                return this.maxHistory > 0;
            }
        }

        @Override
        public void deactivate() {
             synchronized (history) {
                this.maxHistory = 0;
                this.history.clear();
            }
        }
    }

    static final class ConfigurationChange implements Comparable<ConfigurationChange> {

        private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter(Locale.ENGLISH);
        private final OperationContext.ResultAction resultAction;
        private final String userId;
        private final String domainUuid;
        private final AccessMechanism accessMecanism;
        private final InetAddress inetAddress;
        private final List<ModelNode> operations;
        private final Instant date;

        public ConfigurationChange(OperationContext.ResultAction resultAction, String userId, String domainUuid,
                AccessMechanism accessMecanism, InetAddress inetAddress, List<ModelNode> operations) {
            this.resultAction = resultAction;
            this.userId = userId;
            this.domainUuid = domainUuid;
            this.accessMecanism = accessMecanism;
            this.inetAddress = inetAddress;
            this.operations = operations;
            date = now();
        }

        private String getDate() {
            return DATE_FORMAT.format(date);
        }

        public Instant getOperationInstant() {
            return date;
        }

        public ModelNode asModel() {
            ModelNode entry = new ModelNode().setEmptyObject();
            entry.get(OPERATION_DATE).set(getDate());
            if (domainUuid != null) {
                entry.get(DOMAIN_UUID).set(domainUuid);
            }
            if (accessMecanism != null) {
                entry.get(ACCESS_MECHANISM).set(accessMecanism.toString());
            }
            if (inetAddress != null) {
                entry.get(REMOTE_ADDRESS).set(inetAddress.toString());
            }
            entry.get(OUTCOME).set(resultAction == OperationContext.ResultAction.KEEP ? SUCCESS : FAILED);
            if (operations != null && !operations.isEmpty()) {
                ModelNode changes = entry.get(OPERATIONS).setEmptyList();
                for (ModelNode op : operations) {
                    changes.add(op);
                }
            }
            return entry;
        }

        @Override
        public int compareTo(ConfigurationChange change) {
            return change.getOperationInstant().compareTo(date);
        }
    }
}
