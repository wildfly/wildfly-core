/*
 * Copyright 2021 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

