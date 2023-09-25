/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.util.List;

import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public final class RequestWithAttachments {

    private final Attachments attachments;
    private final ModelNode request;

    public RequestWithAttachments(ModelNode request, Attachments attachments) {
        this.request = checkNotNullParamWithNullPointerException("request", request);
        this.attachments = checkNotNullParamWithNullPointerException("attachments", attachments);
    }

    public ModelNode getRequest() {
        return request;
    }

    public List<String> getAttachedFiles() {
        return attachments.getAttachedFiles();
    }
}
