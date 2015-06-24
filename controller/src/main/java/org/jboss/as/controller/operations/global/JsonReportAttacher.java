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
