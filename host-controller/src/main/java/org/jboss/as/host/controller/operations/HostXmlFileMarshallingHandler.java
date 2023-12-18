/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.XmlFileMarshallingHandler;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.domain.controller.LocalHostControllerInfo;

/**
 *
 * @author Emmanuel Hugonnet (c) 2021 Red Hat, Inc.
 */
public class HostXmlFileMarshallingHandler extends XmlFileMarshallingHandler {

    private final LocalHostControllerInfo hostControllerInfo;

    public HostXmlFileMarshallingHandler(final ConfigurationPersister configPersister, final LocalHostControllerInfo hostControllerInfo) {
        super(configPersister);
        this.hostControllerInfo = hostControllerInfo;
    }

    @Override
    protected PathAddress getBaseAddress() {
        return PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.HOST, hostControllerInfo.getLocalHostName()));
    }
}

