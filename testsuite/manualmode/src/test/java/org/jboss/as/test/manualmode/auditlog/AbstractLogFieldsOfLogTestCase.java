/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECURITY_REALM;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractLogFieldsOfLogTestCase {

    static final Pattern DATE_STAMP_PATTERN = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - \\{");

    private static final String DEFAULT_USER_KEY = "wildfly.sasl.local-user.default-user";
    final Path FILE = Paths.get(TestSuiteEnvironment.getJBossHome(), "standalone", "data", "audit-log.log");

    private ModelNode userAuthAddress;
    private ModelNode userIdentRealmAddress;
    private boolean elytronEnabled = false;

    void configureUser(final ModelControllerClient client, final CompositeOperationBuilder compositeOp) throws IOException {

        // Determine the we should be using a security-realm or SASL (we assume SASL means Elytron is enabled)
        String securityRealm = "ManagementRealm";
        ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("core-service", "management", "management-interface", "http-interface"));
        ModelNode result = executeForSuccess(client, OperationBuilder.create(op).build());
        if (result.hasDefined("security-realm")) {
            securityRealm = result.get("security-realm").asString();
        } else if (result.hasDefined("http-upgrade")) {
            final ModelNode httpUpgrade = result.get("http-upgrade");
            // We could query this further to get the actual name of the configurable-sasl-server-factory. Since this
            // is a test we're making some assumptions to limit the number of query calls made to the server.
            if (httpUpgrade.hasDefined("sasl-authentication-factory")) {
                elytronEnabled = true;
            }
        }

        if (elytronEnabled) {
            userAuthAddress = Operations.createAddress("subsystem", "elytron", "configurable-sasl-server-factory", "configured");
            op = Operations.createOperation("map-remove", userAuthAddress);
            op.get("name").set("properties");
            op.get("key").set(DEFAULT_USER_KEY);
            compositeOp.addStep(op.clone());
            op = Operations.createOperation("map-put", userAuthAddress);
            op.get("name").set("properties");
            op.get("key").set(DEFAULT_USER_KEY);
            op.get("value").set("IAmAdmin");
            compositeOp.addStep(op.clone());

            userIdentRealmAddress = Operations.createAddress("subsystem", "elytron", "identity-realm", "local");
            compositeOp.addStep(Operations.createWriteAttributeOperation(userIdentRealmAddress, "identity", "IAmAdmin"));

        } else {
            userAuthAddress = Operations.createAddress(CORE_SERVICE, MANAGEMENT, SECURITY_REALM, securityRealm, AUTHENTICATION, LOCAL);
            compositeOp.addStep(Operations.createWriteAttributeOperation(userAuthAddress, "default-user", "IAmAdmin"));
        }
    }

    void resetUser(final CompositeOperationBuilder compositeOp) {
        if (elytronEnabled) {
            ModelNode op = Operations.createOperation("map-clear", userAuthAddress);
            op.get("name").set("properties");
            compositeOp.addStep(op.clone());
            op = Operations.createOperation("map-put", userAuthAddress);
            op.get("name").set("properties");
            op.get("key").set(DEFAULT_USER_KEY);
            op.get("value").set("$local");
            compositeOp.addStep(op.clone());
            op = Operations.createOperation("map-put", userAuthAddress);
            op.get("name").set("properties");
            op.get("key").set("wildfly.sasl.local-user.challenge-path");
            op.get("value").set("${jboss.server.temp.dir}/auth");
            compositeOp.addStep(op.clone());

            userIdentRealmAddress = Operations.createAddress("subsystem", "elytron", "identity-realm", "local");
            compositeOp.addStep(Operations.createWriteAttributeOperation(userIdentRealmAddress, "identity", "$local"));
        } else {
            compositeOp.addStep(Operations.createWriteAttributeOperation(userAuthAddress, "default-user", "$local"));
        }
    }

    List<ModelNode> readFile(int expectedRecords, boolean trimModulesLoaderUnregister) throws IOException {
        List<ModelNode> list = new ArrayList<ModelNode>();
        final BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8);
        try {
            StringWriter writer = null;
            String line = reader.readLine();
            while (line != null) {
                if (DATE_STAMP_PATTERN.matcher(line).matches()) {
                    if (writer != null) {
                        list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
                    }
                    writer = new StringWriter();
                    writer.append("{");
                } else {
                    writer.append("\n" + line);
                }
                line = reader.readLine();
            }
            if (writer != null) {
                list.add(ModelNode.fromJSONString(writer.getBuffer().toString()));
            }
        } finally {
            IoUtils.safeClose(reader);
        }
        if (trimModulesLoaderUnregister) {
            for (Iterator<ModelNode> it = list.iterator() ; it.hasNext() ; ) {
                ModelNode log = it.next();
                if (isJmxAuditLogRecord(log)) {
                    //See https://issues.jboss.org/browse/WFCORE-2997 for why we remove this
                     it.remove();
                }
            }
        }
        Assert.assertEquals(list.toString(), expectedRecords, list.size());
        return list;
    }

    boolean isJmxAuditLogRecord(ModelNode record) {
        final String type = record.get("type").asString();
        if (type.equals("jmx") && record.get("method").asString().equals("unregisterMBean")) {
            List<ModelNode> params = record.get("params").asList();
            if (params.size() == 1 && params.get(0).asString().contains("jboss.modules:type=ModuleLoader,name=ServiceModuleLoader-")) {
                return true;
            }
        }
        return false;
    }

    static ModelNode executeForSuccess(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
        return Operations.readResult(result);
    }
}
