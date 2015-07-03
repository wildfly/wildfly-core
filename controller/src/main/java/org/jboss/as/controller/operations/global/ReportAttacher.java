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
