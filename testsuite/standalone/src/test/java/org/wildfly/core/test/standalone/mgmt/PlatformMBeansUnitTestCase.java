/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.test.shared.TestSuiteEnvironment.isWindows;

import java.util.HashSet;
import java.util.Set;
import jakarta.inject.Inject;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test validating that the platform mbean resources exist and are reachable.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@RunWith(WildFlyRunner.class)
public class PlatformMBeansUnitTestCase {

    private static final Set<String> ignored = new HashSet<String>();

    static {
        // Only a few subsystems are NOT supposed to work in the domain mode
        ignored.add("deployment-scanner");
    }

    @Inject
    private ManagementClient managementClient;

    @Test
    public void testReadClassLoadingMXBean() throws Exception {
        // Get a list of all registered subsystems
        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        ModelNode address = new ModelNode();
        address.add(CORE_SERVICE, PLATFORM_MBEAN);
        address.add(TYPE, "class-loading");
        operation.get(OP_ADDR).set(address);
        operation.get(NAME).set("loaded-class-count");

        final ModelNode result = executeForResult(operation);
        org.junit.Assert.assertEquals(ModelType.INT, result.getType());
    }

    @Test
    public void testChangeInJVMManagement() throws Exception {
        // The minimal JDK version
        Integer vers = Integer.getInteger("java.min.version");
        if (vers != null) {
            AssumeTestGroupUtil.assumeJDKVersionExact(vers);
        }
        String[] wildflyOperations = {
            "list-add",
            "list-clear",
            "list-get",
            "list-remove",
            "map-clear",
            "map-get",
            "map-put",
            "map-remove",
            "query",
            "read-attribute",
            "read-attribute-group",
            "read-attribute-group-names",
            "read-children-names",
            "read-children-resources",
            "read-children-types",
            "read-operation-description",
            "read-operation-names",
            "read-resource",
            "read-resource-description",
            "undefine-attribute",
            "whoami",
            "write-attribute"
        };
        String[] deprecated = {
            "free-physical-memory-size",
            "total-physical-memory-size",
            "system-cpu-load"
        };
        // Temurin JDK17 exposes attributes that are actually defined since JDK21
        String[] notInJDK17 = {
            "total-thread-allocated-bytes"
        };
        // Not in Windows JVM
        String[] notOnWindows = {
            "max-file-descriptor-count",
            "open-file-descriptor-count"
        };

        Map<String, String> mappedOperations = new HashMap<>();
        mappedOperations.put("get-threads-allocated-bytes", "get-thread-allocated-bytes");

        Set<String> ignoredOperations = new HashSet<>(Arrays.asList(wildflyOperations));
        Set<String> deprecatedAttributes = new HashSet<>(Arrays.asList(deprecated));
        Set<String> notInJDK17Attributes = new HashSet<>(Arrays.asList(notInJDK17));
        Set<String> notOnWindowsAttributes = new HashSet<>(Arrays.asList(notOnWindows));

        final ObjectName pattern = new ObjectName("java.lang:*");
        Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer().queryNames(pattern, null);
        ModelNode op = getOperation(READ_CHILDREN_NAMES_OPERATION, null, null);
        op.get(CHILD_TYPE).set(TYPE);
        ModelNode result = executeForResult(op);
        Assert.assertTrue(result.isDefined());
        Map<ObjectName, ModelNode> resources = new HashMap<>();
        Map<ObjectName, ModelNode> resourcesDescriptions = new HashMap<>();
        for (ModelNode r : result.asList()) {
            ModelNode readResource = getOperation(READ_RESOURCE_OPERATION, r.asString(), null);
            readResource.get(INCLUDE_RUNTIME).set("true");
            ModelNode resource = executeForResult(readResource);
            if (resource.has("object-name")) {
                ObjectName objName = ObjectName.getInstance(resource.get("object-name").asString());
                resources.put(objName, resource);
                ModelNode resourceDescriptionOp = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, r.asString(), null);
                resourceDescriptionOp.get(OPERATIONS).set("true");
                ModelNode resourceDescription = executeForResult(resourceDescriptionOp);
                resourcesDescriptions.put(objName, resourceDescription);
            } else {
                if (resource.has("name")) {
                    for (String s : resource.get("name").keys()) {
                        ModelNode readChildResource = getOperation(READ_RESOURCE_OPERATION, r.asString(), s);
                        readChildResource.get(INCLUDE_RUNTIME).set("true");
                        ModelNode childResource = executeForResult(readChildResource);
                        ObjectName objName = ObjectName.getInstance(childResource.get("object-name").asString());
                        resources.put(objName, childResource);
                        ModelNode resourceDescriptionOp = getOperation(READ_RESOURCE_DESCRIPTION_OPERATION, r.asString(), s);
                        resourceDescriptionOp.get(OPERATIONS).set("true");
                        ModelNode resourceDescription = executeForResult(resourceDescriptionOp);
                        resourcesDescriptions.put(objName, resourceDescription);
                    }
                }
            }
        }
        // Check that all JVM MBeans are in the model.
        for (ObjectName objName : names) {
            Assert.assertTrue("ObjectName not in management model " + objName, resources.keySet().contains(objName));
        }
        // Check that MBeans in the management model are actually registered in the JVM
        for (ObjectName objName : resources.keySet()) {
            Assert.assertTrue("MBean in the management model is not registered in the JVM " + objName,
                    ManagementFactory.getPlatformMBeanServer().isRegistered(objName));
        }
        //Check that all attributes and operations in the JVM MBeans are exposed.
        // Check that all exposed attributes and operations are in the JVM MBeans
        for (ObjectName objName : names) {
            MBeanInfo info = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(objName);
            ModelNode attributes = resourcesDescriptions.get(objName).get("attributes");
            ModelNode operations = resourcesDescriptions.get(objName).get("operations");
            for (MBeanAttributeInfo aInfo : info.getAttributes()) {
                String name = format(aInfo.getName());
                if (!deprecatedAttributes.contains(name) && !notInJDK17Attributes.contains(name)) {
                    Assert.assertTrue(objName + " Attribute " + name + " is not in the model", attributes.has(name));
                }
            }
            for (String attribute : attributes.keys()) {
                if (isWindows() && notOnWindowsAttributes.contains(attribute)) {
                    continue;
                }
                String name = formatToJMX(attribute, true);
                boolean found = false;
                for (MBeanAttributeInfo aInfo : info.getAttributes()) {
                    if (aInfo.getName().equals(name)) {
                        found = true;
                        break;
                    }
                }
                Assert.assertTrue(objName + " Attribute " + attribute + " is not in the JVM", found);
            }
            for (MBeanOperationInfo aInfo : info.getOperations()) {
                String name = format(aInfo.getName());
                Assert.assertTrue(objName + " Operation " + name + " is not in the model", operations.has(name));
            }
            for (String o : operations.keys()) {
                if (!ignoredOperations.contains(o)) {
                    String mapped = mappedOperations.get(o);
                    if (mapped != null) {
                        o = mapped;
                    }
                    String jmxName = formatToJMX(o, false);
                    boolean found = false;
                    ModelNode params = operations.get(o).get("request-properties");
                    for (MBeanOperationInfo aInfo : info.getOperations()) {
                        if (aInfo.getName().equals(jmxName)) {
                            found = sameSignature(params, aInfo);
                            if (found) {
                                break;
                            }
                        }
                    }
                    // overridden methods with array parameter.
                    if (!found && o.endsWith("s")) {
                        jmxName = jmxName.substring(0, jmxName.length() - 1);
                        for (MBeanOperationInfo aInfo : info.getOperations()) {
                            if (aInfo.getName().equals(jmxName)) {
                                found = sameSignature(params, aInfo);
                                if (found) {
                                    break;
                                }
                            }
                        }
                    }
                    Assert.assertTrue(objName + " Operation " + o + " ==> " + jmxName + " is not in the JVM MBean", found);
                }
            }
        }
    }

    private static boolean sameSignature(ModelNode params, MBeanOperationInfo aInfo) {
        boolean sameSignature = false;
        if (aInfo.getSignature().length == params.keys().size()) {
            sameSignature = true;
            Set<MBeanParameterInfo> allParams = new HashSet<>(Arrays.asList(aInfo.getSignature()));
            for (String key : params.keys()) {
                ModelNode mn = params.get(key);
                Iterator<MBeanParameterInfo> it = allParams.iterator();
                boolean foundParam = false;
                while (it.hasNext()) {
                    MBeanParameterInfo pInfo = it.next();
                    if (sameType(mn, pInfo.getType())) {
                        foundParam = true;
                        it.remove();
                        break;
                    }
                }
                if (!foundParam) {
                    sameSignature = false;
                    break;
                }
            }
        }
        return sameSignature;
    }

    private static boolean sameType(ModelNode properties, String type) {
        ModelType modelType = properties.get("type").asType();
        boolean same = false;
        if (modelType == ModelType.LIST) {
            if (type.startsWith("[")) {
                ModelType valueType = properties.get("value-type").asType();
                same = sameType(valueType, type.substring(1), true);
            }
        } else {
            same = sameType(modelType, type, false);
        }
        return same;
    }

    private static boolean sameType(ModelType modelType, String signature, boolean array) {
        boolean same = false;
        switch (modelType) {
            case BOOLEAN:
                same = array ? signature.equals("Z") : signature.equals("boolean");
                break;
            case DOUBLE:
                same = array ? signature.equals("D") : signature.equals("double");
                break;
            case INT:
                same = array ? signature.equals("I") : signature.equals("int");
                break;
            case LONG:
                same = array ? signature.equals("J") : signature.equals("long");
                break;
            case STRING:
                same = array ? signature.equals("Ljava.lang.String;")
                        : signature.equals("java.lang.String");
                break;
            default:
                break;
        }
        return same;
    }

    private static String format(String name) {
        StringBuilder res = new StringBuilder();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                c = Character.toLowerCase(c);
                if (i != 0) {
                    res.append("-");
                }
            }
            res.append(c);
        }
        return res.toString();
    }

    private static String formatToJMX(String name, boolean isAttribute) {
        StringBuilder res = new StringBuilder();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i == 0 && isAttribute) {
                c = Character.toUpperCase(c);
            } else {
                if (c == '-') {
                    i += 1;
                    c = chars[i];
                    c = Character.toUpperCase(c);
                }
            }
            res.append(c);
        }
        return res.toString();
    }

    private static ModelNode getOperation(final String opName, final String type, final String name) {
        return Util.getEmptyOperation(opName, getAddress(type, name));
    }

    private static ModelNode getAddress(final String type, final String name) {
        final ModelNode result = new ModelNode();
        result.add(CORE_SERVICE, PLATFORM_MBEAN);
        if (type != null) {
            result.add(TYPE, type);
            if (name != null) {
                result.add(NAME, name);
            }
        }

        return result;
    }

    private ModelNode executeForResult(final ModelNode operation) throws Exception {
        final ModelNode result = managementClient.getControllerClient().execute(operation);
        checkSuccessful(result, operation);
        return result.get(RESULT);
    }

    static void checkSuccessful(final ModelNode result, final ModelNode operation) {
        if(! SUCCESS.equals(result.get(OUTCOME).asString())) {
            System.out.println("Failed result:\n" + result + "\n for operation:\n" + operation);
            Assert.fail("operation failed: " + result.get(FAILURE_DESCRIPTION));
        }
    }

}
