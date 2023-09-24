/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import java.io.IOException;
import java.io.InputStream;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Interfaceto build a report from ModelNode as a binary stream and attach it to the response.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public interface ReportAttacher {

    void addReport(ModelNode report) throws OperationFailedException;

    void attachResult(OperationContext context) throws OperationFailedException;

    abstract static class AbstractReportAttacher implements ReportAttacher {
    protected final boolean record;
    protected static final byte[] EMPTY = new byte[0];

    public AbstractReportAttacher(boolean record) {
        this.record = record;
    }

    protected abstract InputStream getContent();
    protected abstract String getMimeType();

    @Override
    public void attachResult(OperationContext context) throws OperationFailedException {
        if (record) {
            try (InputStream content = getContent()) {
                context.attachResultStream(getMimeType(), content);
            } catch (IOException ex) {
                 throw ControllerLogger.MGMT_OP_LOGGER.failedToBuildReport(ex);
            }
        }
    }
}
}
