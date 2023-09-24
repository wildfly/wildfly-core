/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jboss.as.controller.operations.global.ReportAttacher.AbstractReportAttacher;
import org.jboss.dmr.ModelNode;

/**
 * Allow to produce a json file to be attached to the response.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
class JsonReportAttacher  extends AbstractReportAttacher {
    private final ModelNode result ;

    JsonReportAttacher(boolean record) {
        super(record);
        result = new ModelNode().setEmptyList();
    }

    @Override
    public void addReport(ModelNode report) {
        if (record) {
            result.add(report);
        }
    }

    @Override
    public InputStream getContent() {
        if (record ) {
            return new ByteArrayInputStream(result.toJSONString(false).getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(EMPTY);
    }

    @Override
    protected String getMimeType() {
        return "application/json";
    }
}
