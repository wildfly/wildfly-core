/*
 * Copyright 2016 JBoss by Red Hat.
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
package org.jboss.as.test.integration.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_MEMORY_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_HISTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.LegacyConfigurationChangeResourceDefinition;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public abstract class AbstractConfigurationChangesTestCase {
    protected static DomainTestSupport testSupport;
    protected static DomainLifecycleUtil domainPrimaryLifecycleUtil;
    protected static DomainLifecycleUtil domainSecondaryLifecycleUtil;
    private static final Logger logger = Logger.getLogger(AbstractConfigurationChangesTestCase.class);

    protected static final int MAX_HISTORY_SIZE = 100;

    protected static final PathElement HOST_PRIMARY = PathElement.pathElement(HOST, "primary");
    protected static final PathElement HOST_SECONDARY = PathElement.pathElement(HOST, "secondary");
    protected static final PathAddress ALLOWED_ORIGINS_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(MANAGEMENT_INTERFACE, HTTP_INTERFACE);
    protected static final PathAddress AUDIT_LOG_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(LOGGER, AUDIT_LOG);
    protected static final PathAddress SYSTEM_PROPERTY_ADDRESS = PathAddress.pathAddress()
            .append(SYSTEM_PROPERTY, "test");
    protected static final PathAddress IN_MEMORY_HANDLER_ADDRESS = PathAddress.pathAddress()
            .append(CORE_SERVICE, MANAGEMENT)
            .append(ACCESS, AUDIT)
            .append(IN_MEMORY_HANDLER, "test");
    @BeforeClass
    public static void setupDomain() throws Exception {
        DomainTestSupport.Configuration configuration = DomainTestSupport.Configuration.create(
                AbstractConfigurationChangesTestCase.class.getSimpleName(),
                "domain-configs/domain-config-changes.xml",
                "host-configs/host-primary-config-changes.xml",
                "host-configs/host-secondary-config-changes.xml",
                false, false, false, false, false);
        testSupport = DomainTestSupport.createAndStartSupport(configuration);
        domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
        domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.close();
        domainPrimaryLifecycleUtil = null;
        domainSecondaryLifecycleUtil = null;
        testSupport = null;
    }

    protected abstract PathAddress getAddress();

    public void createConfigurationChanges(PathElement host) throws Exception {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode add = Util.createAddOperation(PathAddress.pathAddress().append(host).append(getAddress()));
        add.get(LegacyConfigurationChangeResourceDefinition.MAX_HISTORY.getName()).set(MAX_HISTORY_SIZE);
        executeForResult(client, add); // 0 -- write to host subsystem
        PathAddress allowedOrigins = PathAddress.pathAddress().append(host).append(ALLOWED_ORIGINS_ADDRESS);
        ModelNode setAllowedOrigins = Util.createEmptyOperation("list-add", allowedOrigins);
        setAllowedOrigins.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.ALLOWED_ORIGINS);
        setAllowedOrigins.get(ModelDescriptionConstants.VALUE).set("http://www.wildfly.org");
        client.execute(setAllowedOrigins); // 1 -- write to host core
        PathAddress auditLogAddress = PathAddress.pathAddress().append(host).append(AUDIT_LOG_ADDRESS);
        ModelNode disableLogBoot = Util.getWriteAttributeOperation(auditLogAddress, ModelDescriptionConstants.LOG_BOOT, false);
        client.execute(disableLogBoot); // 2 -- write to host core
        //read
        client.execute(Util.getReadAttributeOperation(allowedOrigins, ModelDescriptionConstants.ALLOWED_ORIGINS));
        //invalid operation; not recorded
        client.execute(Util.getUndefineAttributeOperation(allowedOrigins, "not-exists-attribute"));
        //invalid operation; not recorded
        client.execute(Util.getWriteAttributeOperation(allowedOrigins, "not-exists-attribute", "123456"));
        //write operation, failed
        ModelNode setAllowedOriginsFails = Util.getWriteAttributeOperation(allowedOrigins, ModelDescriptionConstants.ALLOWED_ORIGINS, "123456"); //wrong type, expected is LIST, op list-add
        client.execute(setAllowedOriginsFails); // 3 -- write to host core (recorded despite failure)
        PathAddress systemPropertyAddress = PathAddress.pathAddress().append(host).append(SYSTEM_PROPERTY_ADDRESS);
        ModelNode setSystemProperty = Util.createAddOperation(systemPropertyAddress);
        setSystemProperty.get(ModelDescriptionConstants.VALUE).set("changeConfig");
        client.execute(setSystemProperty); // 4  -- write to host core
        ModelNode unsetAllowedOrigins = Util.getUndefineAttributeOperation(allowedOrigins, ModelDescriptionConstants.ALLOWED_ORIGINS);
        client.execute(unsetAllowedOrigins); // 5 -- write to host core
        ModelNode enableLogBoot = Util.getWriteAttributeOperation(auditLogAddress, ModelDescriptionConstants.LOG_BOOT, true);
        client.execute(enableLogBoot);  // 6 -- write to host core
        ModelNode unsetSystemProperty = Util.createRemoveOperation(systemPropertyAddress);
        client.execute(unsetSystemProperty); // 7 -- write to host core
        PathAddress inMemoryAddress = PathAddress.pathAddress().append(host).append(IN_MEMORY_HANDLER_ADDRESS);
        ModelNode addInMemoryHandler = Util.createAddOperation(inMemoryAddress);
        client.execute(addInMemoryHandler); // 8  -- write to host core
        ModelNode editInMemoryHandler = Util.getWriteAttributeOperation(inMemoryAddress, ModelDescriptionConstants.MAX_HISTORY, 50);
        client.execute(editInMemoryHandler); // 9  -- write to host core
        ModelNode removeInMemoryHandler = Util.createRemoveOperation(inMemoryAddress);
        client.execute(removeInMemoryHandler); // 10  -- write to host core
    }

    protected PathAddress removePrefix(ModelNode operation) {
        return PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR)).subAddress(1);
    }

    public List<ModelNode> readConfigurationChanges(DomainClient client, PathElement prefix) throws IOException {
        PathAddress address = getAddress();
        if (prefix != null) {
            address = PathAddress.pathAddress().append(prefix).append(getAddress());
        }
        ModelNode readConfigChanges = Util.createOperation(LegacyConfigurationChangeResourceDefinition.OPERATION_NAME, address);
        ModelNode response = client.execute(readConfigChanges);
        assertThat(response.asString(), response.get(ClientConstants.OUTCOME).asString(), is(ClientConstants.SUCCESS));
        logger.info("For " + prefix + " we have " + response.get(ClientConstants.RESULT));
        return response.get(ClientConstants.RESULT).asList();
    }

    protected void setConfigurationChangeMaxHistory(DomainClient client, PathElement prefix, int size) throws IOException, UnsuccessfulOperationException {
        PathAddress address = PathAddress.pathAddress().append(prefix).append(getAddress());
        ModelNode writeMaxHistorySize = Util.getWriteAttributeOperation(address, "max-history", size);
        executeForResult(client, writeMaxHistorySize);
        checkMaxHistorySize(client, size, prefix);
    }

    protected void checkMaxHistorySize(DomainClient client,  int expectedSize, PathElement ... prefix) throws UnsuccessfulOperationException {
        PathAddress address = PathAddress.pathAddress().append(prefix).append(getAddress());
        ModelNode readMaxHistorySize = Util.getReadAttributeOperation(address, MAX_HISTORY);
        ModelNode result = executeForResult(client, readMaxHistorySize);
        assertThat(result.asInt(), is(expectedSize));
    }

    protected void clearConfigurationChanges(PathElement host) throws UnsuccessfulOperationException {
        DomainClient client = domainPrimaryLifecycleUtil.getDomainClient();
        final ModelNode remove = Util.createRemoveOperation(PathAddress.pathAddress().append(host).append(getAddress()));
        executeForResult(client, remove);
    }

    protected ModelNode executeForResult(final DomainClient client, final ModelNode operation) throws UnsuccessfulOperationException {
        try {
            final ModelNode result = client.execute(operation);
            checkSuccessful(result, operation);
            return result.get(ClientConstants.RESULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void checkSuccessful(final ModelNode result, final ModelNode operation) throws UnsuccessfulOperationException {
        if (!ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            logger.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new UnsuccessfulOperationException(result.get(ClientConstants.FAILURE_DESCRIPTION).toString());
        }
    }
}
