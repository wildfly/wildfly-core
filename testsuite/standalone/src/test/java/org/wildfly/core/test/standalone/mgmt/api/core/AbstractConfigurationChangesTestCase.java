/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.test.standalone.mgmt.api.core;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public abstract class AbstractConfigurationChangesTestCase extends ContainerResourceMgmtTestBase {

    protected static final int ALL_MAX_HISTORY_SIZE = 5;
    protected static final int MAX_HISTORY_SIZE = 4;
    protected static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.MANAGEMENT)
            .append(ModelDescriptionConstants.MANAGEMENT_INTERFACE, ModelDescriptionConstants.HTTP_INTERFACE);
    protected static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(ModelDescriptionConstants.SYSTEM_PROPERTY, "test");

    public void createConfigurationChanges(ModelControllerClient client) throws Exception {
        ModelNode setAllowedOrigins = Util.createEmptyOperation("list-add", ALLOWED_ORIGINS_ADDRESS);
        setAllowedOrigins.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.ALLOWED_ORIGINS);
        setAllowedOrigins.get(ModelDescriptionConstants.VALUE).set("http://www.wildfly.org");
        client.execute(setAllowedOrigins);
        ModelNode setSystemProperty = Util.createAddOperation(SYSTEM_PROPERTY_ADDRESS);
        setSystemProperty.get(ModelDescriptionConstants.VALUE).set("changeConfig");
        client.execute(setSystemProperty);
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ModelDescriptionConstants.ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins);
        ModelNode unsetSystemProperty = Util.createRemoveOperation(SYSTEM_PROPERTY_ADDRESS);
        client.execute(unsetSystemProperty);
        //read
        client.execute(Util.getReadAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ModelDescriptionConstants.ALLOWED_ORIGINS));
        //invalid operation
        client.execute(Util.getUndefineAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute"));
        //invalid operation
        client.execute(Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, "not-exists-attribute", "123456"));
        //write operation, failed
        ModelNode setAllowedOriginsFails = Util.getWriteAttributeOperation(ALLOWED_ORIGINS_ADDRESS, ModelDescriptionConstants.ALLOWED_ORIGINS, "123456"); //wrong type, expected is LIST, op list-add
        client.execute(setAllowedOriginsFails);
    }
}
