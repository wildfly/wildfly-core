/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.audit;

import java.net.InetAddress;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.audit.SyslogAuditLogHandler.Facility;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;

/**
 * @author John Bailey
 * @author Kabir Khan
 */
public interface AuditLogger {

    enum Status {
        /** The logger is queueing log messages in memory until it is switched to status {@link #LOGGING} or status {@link #DISABLED} */
        QUEUEING,
        /** The logger is actively logging messages to an external store */
        LOGGING,
        /**
         * The logger will log one more record, after which it will switch to status {@link #DISABLED}.
         * This status is set by an operation that is disabling logging, as a means of ensuring that operation
         * is itself logged.
         */
        DISABLE_NEXT,
        /** The logger is discarding any log requests, and any {@link #QUEUEING queued} requests have been discarded */
        DISABLED
    }

    void log(boolean readOnly, OperationContext.ResultAction resultAction, String userId, String domainUUID,
             AccessMechanism accessMechanism, InetAddress remoteAddress, final Resource resultantModel, List<ModelNode> operations);

    void logJmxMethodAccess(final boolean readOnly, final String userId, final String domainUUID, final AccessMechanism accessMechanism, InetAddress remoteAddress,
             final String methodName, final String[] methodSignature, final Object[] methodParams, final Throwable error);
    /**
     * An audit logger that doesn't log.
     */
    ManagedAuditLogger NO_OP_LOGGER = new ManagedAuditLogger() {
        @Override
        public boolean isLogReadOnly() {
            return false;
        }

        @Override
        public void setLogReadOnly(boolean logReadOnly) {
        }

        @Override
        public boolean isLogBoot() {
            return false;
        }

        @Override
        public void setLogBoot(boolean logBoot) {
        }

        public Status getLoggerStatus() {
            return Status.DISABLED;
        }

        @Override
        public void log(boolean readOnly, OperationContext.ResultAction resultAction, String userId, String domainUUID, AccessMechanism accessMechanism, InetAddress remoteAddress, Resource resultantModel, List<ModelNode> operations) {
        }

        @Override
        public void logJmxMethodAccess(boolean readOnly, String userId, String domainUUID, AccessMechanism accessMechanism, InetAddress remoteAddress, String methodName, String[] methodSignature, Object[] methodParams, Throwable error) {
        }

        @Override
        public void setLoggerStatus(Status newStatus) {
        }


        @Override
        public void removeFormatter(String name) {
        }

        @Override
        public void addFormatter(AuditLogItemFormatter formatter) {
        }

        @Override
        public JsonAuditLogItemFormatter getJsonFormatter(String name) {
            return null;
        }

        @Override
        public AuditLogHandlerUpdater getUpdater() {
            return new ManagedAuditLogger.AuditLogHandlerUpdater(){

                @Override
                public void addHandler(AuditLogHandler handler) {
                }

                @Override
                public void updateHandler(AuditLogHandler handler) {
                }

                @Override
                public void removeHandler(String name) {
                }

                @Override
                public void addHandlerReference(PathAddress referenceAddress) {
                }

                @Override
                public void removeHandlerReference(PathAddress referenceAddress) {
                }

                @Override
                public void rollbackChanges() {
                }

                @Override
                public void applyChanges() {
                }
            };
        }

        @Override
        public ManagedAuditLogger createNewConfiguration(boolean manualCommit) {
            return this;
        }

        @Override
        public void updateHandlerFormatter(String name, String formatterName) {
        }

        @Override
        public void recycleHandler(String name) {
        }

        @Override
        public int getHandlerFailureCount(String name) {
            return 0;
        }

        @Override
        public boolean getHandlerDisabledDueToFailure(String name) {
            return false;
        }

        @Override
        public void updateHandlerMaxFailureCount(String name, int count) {
        }

        @Override
        public void bootDone() {
        }

        @Override
        public void startBoot() {
        }

        @Override
        public void updateSyslogHandlerFacility(String name, Facility facility) {
        }

        @Override
        public void updateSyslogHandlerAppName(String name, String appName) {
        }

        @Override
        public void updateSyslogHandlerReconnectTimeout(String name, int reconnectTimeout) {
        }

        @Override
        public void updateInMemoryHandlerMaxHistory(String name, int maxHistory) {
        }
    };
}