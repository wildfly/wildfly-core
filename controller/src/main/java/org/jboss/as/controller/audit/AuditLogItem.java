/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import java.net.InetAddress;
import java.util.Date;
import java.util.List;

import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class AuditLogItem {

    private final Date date = new Date();
    private final String asVersion;
    protected final boolean readOnly;
    protected final boolean booting;
    protected final String userId;
    protected final String domainUUID;
    protected final AccessMechanism accessMechanism;
    protected final InetAddress remoteAddress;

    AuditLogItem(String asVersion, boolean readOnly, boolean booting, String userId, String domainUUID, AccessMechanism accessMechanism,
            InetAddress remoteAddress) {
        this.asVersion = asVersion;
        this.readOnly = readOnly;
        this.booting = booting;
        this.userId = userId;
        this.domainUUID = domainUUID;
        this.accessMechanism = accessMechanism;
        this.remoteAddress = remoteAddress;
    }

    static AuditLogItem createModelControllerItem(String asVersion, boolean readOnly, boolean booting, ResultAction resultAction, String userId,
                String domainUUID, AccessMechanism accessMechanism, InetAddress remoteAddress, Resource resultantModel,
                List<ModelNode> operations) {
        return new ModelControllerAuditLogItem(asVersion, readOnly, booting, resultAction, userId, domainUUID, accessMechanism, remoteAddress, resultantModel, operations);
    }

    static AuditLogItem createMethodAccessItem(String asVersion, boolean readOnly, boolean booting, String userId, String domainUUID,
                AccessMechanism accessMechanism, InetAddress remoteAddress, String methodName, String[] methodSignature,
                Object[] methodParams, Throwable error) {
        return new JmxAccessAuditLogItem(asVersion, readOnly, booting, userId, domainUUID, accessMechanism, remoteAddress, methodName, methodSignature, methodParams, error);
    }


    abstract String format(AuditLogItemFormatter formatter);

    /**
     * Get the asVersion
     * @return the asVersion
     */
    public String getAsVersion() {
        return asVersion;
    }

    /**
     * Get the readOnly
     * @return the readOnly
     */
    boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Get the booting
     * @return the booting
     */
    boolean isBooting() {
        return booting;
    }

    /**
     * Get the userId
     * @return the userId
     */
    String getUserId() {
        return userId;
    }

    /**
     * Get the domainUUID
     * @return the domainUUID
     */
    String getDomainUUID() {
        return domainUUID;
    }

    /**
     * Get the accessMechanism
     * @return the accessMechanism
     */
    AccessMechanism getAccessMechanism() {
        return accessMechanism;
    }

    /**
     * Get the remoteAddress
     * @return the remoteAddress
     */
    InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Get the date
     * @return the date
     */
    public Date getDate() {
        return (Date)date.clone();
    }


    static class ModelControllerAuditLogItem extends AuditLogItem {
        private final ResultAction resultAction;
        private final Resource resultantModel;
        private final List<ModelNode> operations;

        ModelControllerAuditLogItem(String asVersion, boolean readOnly, boolean booting, ResultAction resultAction, String userId,
                String domainUUID, AccessMechanism accessMechanism, InetAddress remoteAddress, Resource resultantModel,
                List<ModelNode> operations) {
            super(asVersion, readOnly, booting, userId, domainUUID, accessMechanism, remoteAddress);
            this.resultAction = resultAction;
            this.resultantModel = resultantModel;
            this.operations = operations;
        }

        @Override
        String format(AuditLogItemFormatter formatter) {
            return formatter.formatAuditLogItem(this);
        }

        /**
         * Get the resultAction
         * @return the resultAction
         */
        ResultAction getResultAction() {
            return resultAction;
        }

        /**
         * Get the resultantModel
         * @return the resultantModel
         */
        Resource getResultantModel() {
            return resultantModel;
        }

        /**
         * Get the operations
         * @return the operations
         */
        List<ModelNode> getOperations() {
            return operations;
        }
    }

    static class JmxAccessAuditLogItem extends AuditLogItem {
        private final String methodName;
        private final String[] methodSignature;
        private final Object[] methodParams;
        private final Throwable error;

        JmxAccessAuditLogItem(String asVersion, boolean readOnly, boolean booting, String userId, String domainUUID,
                AccessMechanism accessMechanism, InetAddress remoteAddress, String methodName, String[] methodSignature,
                Object[] methodParams, Throwable error) {
            super(asVersion, readOnly, booting, userId, domainUUID, accessMechanism, remoteAddress);
            this.methodName = methodName;
            this.methodSignature = methodSignature;
            this.methodParams = methodParams;
            this.error = error;
        }

        @Override
        String format(AuditLogItemFormatter formatter) {
            return formatter.formatAuditLogItem(this);
        }

        /**
         * Get the methodName
         * @return the methodName
         */
        String getMethodName() {
            return methodName;
        }

        /**
         * Get the methodSignature
         * @return the methodSignature
         */
        String[] getMethodSignature() {
            return methodSignature;
        }

        /**
         * Get the methodParams
         * @return the methodParams
         */
        Object[] getMethodParams() {
            return methodParams;
        }

        /**
         * Get the error
         * @return the error
         */
        Throwable getError() {
            return error;
        }
    }
}
