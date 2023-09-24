/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALTERNATIVES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NILLABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelTestUtils {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{.*\\}.*");

    /**
     * Read the classpath resource with the given name and return its contents as a string. Hook to
     * for reading in classpath resources for subsequent parsing. The resource is loaded using similar
     * semantics to {@link Class#getResource(String)}
     *
     * @param name the name of the resource
     * @return the contents of the resource as a string
     * @throws IOException
     */
    public static String readResource(final Class<?> clazz, final String name) throws IOException {
        URL configURL = clazz.getResource(name);
        Assert.assertNotNull(name + " url is null", configURL);

        StringWriter writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream(), StandardCharsets.UTF_8))){
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
            }
        }
        return writer.toString();
    }

    /**
     * Checks that the result was successful and gets the real result contents
     *
     * @param result the result to check
     * @return the result contents
     */
    public static ModelNode checkResultAndGetContents(ModelNode result) {
        checkOutcome(result);
        Assert.assertTrue("could not check for result as its missing!  look for yourself here [" + result.toString() +
                "] and result.hasDefined(RESULT) returns " + result.hasDefined(RESULT)
                , result.hasDefined(RESULT));
        return result.get(RESULT);
    }

    /**
     * Checks that the result was successful
     *
     * @param result the result to check
     * @return the result contents
     */
    public static ModelNode checkOutcome(ModelNode result) {
        boolean success = SUCCESS.equals(result.get(OUTCOME).asString());
        Assert.assertTrue(result.get(FAILURE_DESCRIPTION).asString(), success);
        return result;
    }

    /**
     * Checks that the operation failes
     *
     * @param result the result to check
     * @return the failure desciption contents
     */
    public static ModelNode checkFailed(ModelNode result) {
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
        return result.get(FAILURE_DESCRIPTION);
    }

    public static void validateModelDescriptions(PathAddress address, ImmutableManagementResourceRegistration reg) {
        ModelNode description = reg.getModelDescription(PathAddress.EMPTY_ADDRESS).getModelDescription(Locale.getDefault());
        ModelNode attributes = description.get(ATTRIBUTES);
        Set<String> regAttributeNames = reg.getAttributeNames(PathAddress.EMPTY_ADDRESS);
        Set<String> attributeNames = new HashSet<String>();
        if (attributes.isDefined()) {
            if (attributes.asList().size() != regAttributeNames.size()) {
                for (Property p : attributes.asPropertyList()) {
                    attributeNames.add(p.getName());
                }
                if (regAttributeNames.size() > attributeNames.size()) {
                    regAttributeNames.removeAll(attributeNames);
                    Assert.fail("More attributes defined on resource registration than in description, missing: " + regAttributeNames + " for " + address);
                } else if (regAttributeNames.size() < attributeNames.size()) {
                    attributeNames.removeAll(regAttributeNames);
                    Assert.fail("More attributes defined in description than on resource registration, missing: " + attributeNames + " for " + address);
                }
            }
            Map<String, ModelNode> attrMap = new LinkedHashMap<>();
            for (Property p : attributes.asPropertyList()) {
                attrMap.put(p.getName(), p.getValue());
            }
            attributeNames = attrMap.keySet();
            if (!attributeNames.containsAll(regAttributeNames)) {
                Set<String> missDesc = new HashSet<String>(attributeNames);
                missDesc.removeAll(regAttributeNames);

                Set<String> missReg = new HashSet<String>(regAttributeNames);
                missReg.removeAll(attributeNames);

                if (!missReg.isEmpty()) {
                    Assert.fail("There are different attributes defined on resource registration than in description, registered only on Resource Reg: " + missReg + " for " + address);
                }
                if (!missDesc.isEmpty()) {
                    Assert.fail("There are different attributes defined on resource registration than in description, registered only int description: " + missDesc + " for " + address);
                }
            }
            for (Map.Entry<String, ModelNode> entry : attrMap.entrySet()) {
                validateRequiredNillable(ATTRIBUTE + " " + entry.getKey(), entry.getValue());
            }
        }
        if (description.hasDefined(OPERATIONS)) {
            ModelNode operations = description.get(OPERATIONS);

            // TODO compare operation descriptions to the MRR (e.g. same names)

            for (Property property : operations.asPropertyList()) {
                ModelNode opDesc = property.getValue();
                if (opDesc.hasDefined(REQUEST_PROPERTIES)) {
                    String prefix = "operation " + property.getName() + " param ";
                    for (Property param : opDesc.get(REQUEST_PROPERTIES).asPropertyList()) {
                        validateRequiredNillable(prefix + param.getName(), param.getValue());
                    }
                }
                if (opDesc.hasDefined(REPLY_PROPERTIES)) {
                    String prefix = "operation " + property.getName() + " reply field ";
                    for (Property field : opDesc.get(REPLY_PROPERTIES).asPropertyList()) {
                        validateRequiredNillable(prefix + field.getName(), field.getValue());
                    }
                }
            }
        }
        for (PathElement pe : reg.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
            ImmutableManagementResourceRegistration sub = reg.getSubModel(PathAddress.pathAddress(pe));
            validateModelDescriptions(address.append(pe), sub);
        }
    }

    private static void validateRequiredNillable(String name, ModelNode desc) {
        Assert.assertTrue(name + " does not have 'required' metadata", desc.hasDefined(REQUIRED));
        Assert.assertEquals(name + " does not have boolean 'required' metadata", ModelType.BOOLEAN, desc.get(REQUIRED).getType());
        Assert.assertTrue(name + " does not have 'nillable' metadata", desc.hasDefined(NILLABLE));
        Assert.assertEquals(name + " does not have boolean 'nillable' metadata", ModelType.BOOLEAN, desc.get(NILLABLE).getType());
        boolean alternatives = false;
        if (desc.hasDefined(ALTERNATIVES)) {
            Assert.assertEquals(name + " does not have 'alternatives' metadata in list form", ModelType.LIST, desc.get(ALTERNATIVES).getType());
            alternatives = desc.get(ALTERNATIVES).asInt() > 0;
        }
        boolean required = desc.get(REQUIRED).asBoolean();
        Assert.assertEquals(name + " does not have correct 'nillable' metadata. required: " + required + " -- alternatives: " + desc.get(ALTERNATIVES),
                !required || alternatives, desc.get("nillable").asBoolean());

    }

    /**
     * Compares two models to make sure that they are the same
     *
     * @param node1 the first model
     * @param node2 the second model
     */
    public static void compare(ModelNode node1, ModelNode node2) {
        compare(node1, node2, false);
    }

    /**
     * Resolve two models and compare them to make sure that they have same
       content after expression resolution
     *
     * @param node1 the first model
     * @param node2 the second model
     */
    public static void resolveAndCompareModels(ModelNode node1, ModelNode node2) {
        compare(resolve(node1), resolve(node2), false, true, new Stack<String>());
    }

    private static ModelNode resolve(ModelNode unresolved) {
        try {
            return ExpressionResolver.TEST_RESOLVER.resolveExpressions(unresolved);
        } catch (OperationFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Compares two models to make sure that they are the same
     *
     * @param node1           the first model
     * @param node2           the second model
     * @param ignoreUndefined {@code true} if keys containing undefined nodes should be ignored
     */
    public static void compare(ModelNode node1, ModelNode node2, boolean ignoreUndefined) {
        compare(node1, node2, ignoreUndefined, false, new Stack<String>());
    }

    /**
     * Normalize and pretty-print XML so that it can be compared using string
     * compare. The following code does the following: - Removes comments -
     * Makes sure attributes are ordered consistently - Trims every element -
     * Pretty print the document
     *
     * @param xml The XML to be normalized
     * @return The equivalent XML, but now normalized
     */
    public static String normalizeXML(String xml) throws Exception {
        // Remove all white space adjoining tags ("trim all elements")
        xml = xml.replaceAll("\\s*<", "<");
        xml = xml.replaceAll(">\\s*", ">");

        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS domLS = (DOMImplementationLS) registry.getDOMImplementation("LS");
        LSParser lsParser = domLS.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);

        LSInput input = domLS.createLSInput();
        input.setStringData(xml);
        Document document = lsParser.parse(input);

        LSSerializer lsSerializer = domLS.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("comments", Boolean.FALSE);
        lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        return lsSerializer.writeToString(document);
    }

    /**
     * Validate the marshalled xml without adjusting the namespaces for the original and marshalled xml.
     *
     * @param original   the original subsystem xml
     * @param marshalled the marshalled subsystem xml
     * @throws Exception
     */
    public static void compareXml(final String original, final String marshalled) throws Exception {
        compareXml(original, marshalled, false);
    }

    /**
     * Validate the marshalled xml without adjusting the namespaces for the original and marshalled xml.
     *
     * @param original        the original subsystem xml
     * @param marshalled      the marshalled subsystem xml
     * @param ignoreNamespace if {@code true} the subsystem's namespace is ignored, otherwise it is taken into account when comparing the normalized xml.
     * @throws Exception
     */
    public static void compareXml(final String original, final String marshalled, final boolean ignoreNamespace) throws Exception {
        final String xmlOriginal;
        final String xmlMarshalled;
        if (ignoreNamespace) {
            xmlOriginal = removeNamespace(original);
            xmlMarshalled = removeNamespace(marshalled);
        } else {
            xmlOriginal = original;
            xmlMarshalled = marshalled;
        }

        Assert.assertEquals(normalizeXML(xmlOriginal), normalizeXML(xmlMarshalled));
    }

    public static ModelNode getSubModel(ModelNode model, PathElement pathElement) {
        return model.get(pathElement.getKey(), pathElement.getValue());
    }

    public static ModelNode getSubModel(ModelNode model, PathAddress pathAddress) {
        for (PathElement pathElement : pathAddress) {
            model = getSubModel(model, pathElement);
        }
        return model;
    }

    /**
     * Scans for entries of type STRING containing expression formatted strings. This is to trap where parsers
     * call ModelNode.set("${A}") when ModelNode.setExpression("${A}) should have been used
     *
     * @param model the model to check
     */
    public static void scanForExpressionFormattedStrings(ModelNode model) {
        if (model.getType().equals(ModelType.STRING)) {
            if (EXPRESSION_PATTERN.matcher(model.asString()).matches()) {
                Assert.fail("ModelNode with type==STRING contains an expression formatted string: " + model.asString());
            }
        } else if (model.getType() == ModelType.OBJECT) {
            for (String key : model.keys()) {
                final ModelNode child = model.get(key);
                scanForExpressionFormattedStrings(child);
            }
        } else if (model.getType() == ModelType.LIST) {
            List<ModelNode> list = model.asList();
            for (ModelNode entry : list) {
                scanForExpressionFormattedStrings(entry);
            }

        } else if (model.getType() == ModelType.PROPERTY) {
            Property prop = model.asProperty();
            scanForExpressionFormattedStrings(prop.getValue());
        }
    }

    private static String removeNamespace(String xml) {
        int start = xml.indexOf(" xmlns=\"");
        int end = xml.indexOf('"', start + "xmlns=\"".length() + 1);
        if (start != -1) {
            StringBuilder sb = new StringBuilder(xml.substring(0, start));
            sb.append(xml.substring(end + 1));
            return sb.toString();
        }
        return xml;
    }


    private static void compare(ModelNode node1, ModelNode node2, boolean ignoreUndefined, boolean ignoreType, Stack<String> stack) {
        if (! ignoreType) {
            Assert.assertEquals(getCompareStackAsString(stack) + " types", node1.getType(), node2.getType());
        }
        if (node1.getType() == ModelType.OBJECT) {
            ModelNode model1 = ignoreUndefined ? trimUndefinedChildren(node1) : node1;
            ModelNode model2 = ignoreUndefined ? trimUndefinedChildren(node2) : node2;
            final Set<String> keys1 = new TreeSet<String>(model1.keys());
            final Set<String> keys2 = new TreeSet<String>(model2.keys());

            // compare string representations of the keys to help see the difference
            if (!keys1.toString().equals(keys2.toString())){
                //Just to make debugging easier
                System.out.print("");
            }
            Assert.assertEquals(getCompareStackAsString(stack) + ": " + node1 + "\n" + node2, keys1.toString(), keys2.toString());
            Assert.assertTrue(keys1.containsAll(keys2));

            for (String key : keys1) {
                final ModelNode child1 = model1.get(key);
                Assert.assertTrue("Missing: " + key + "\n" + node1 + "\n" + node2, model2.has(key));
                final ModelNode child2 = model2.get(key);

                if (child1.isDefined()) {
                    if (!ignoreUndefined) {
                        Assert.assertTrue(getCompareStackAsString(stack) + " key=" + key + "\n with child1 \n" + child1.toString() + "\n has child2 not defined\n node2 is:\n" + node2.toString(), child2.isDefined());
                    }
                    stack.push(key + "/");
                    compare(child1, child2, ignoreUndefined, ignoreType, stack);
                    stack.pop();
                } else if (!ignoreUndefined) {
                    Assert.assertFalse(getCompareStackAsString(stack) + " key=" + key + "\n with child1 undefined has child2 \n" + child2.asString(), child2.isDefined());
                }
            }
        } else if (node1.getType() == ModelType.LIST) {
            List<ModelNode> list1 = node1.asList();
            List<ModelNode> list2 = node2.asList();
            Assert.assertEquals(list1 + "\n" + list2, list1.size(), list2.size());

            for (int i = 0; i < list1.size(); i++) {
                stack.push(i + "/");
                compare(list1.get(i), list2.get(i), ignoreUndefined, ignoreType, stack);
                stack.pop();
            }

        } else if (node1.getType() == ModelType.PROPERTY) {
            Property prop1 = node1.asProperty();
            Property prop2 = node2.asProperty();
            Assert.assertEquals(prop1 + "\n" + prop2, prop1.getName(), prop2.getName());
            stack.push(prop1.getName() + "/");
            compare(prop1.getValue(), prop2.getValue(), ignoreUndefined, ignoreType, stack);
            stack.pop();

        } else {
            Assert.assertEquals(getCompareStackAsString(stack) +
                        "\n\"" + node1.asString() + "\"\n\"" + node2.asString() + "\"\n-----", node1.asString().trim(), node2.asString().trim());
        }
    }

    private static ModelNode trimUndefinedChildren(ModelNode model) {
        ModelNode copy = model.clone();
        for (String key : new HashSet<String>(copy.keys())) {
            if (!copy.hasDefined(key)) {
                copy.remove(key);
            } else if (copy.get(key).getType() == ModelType.OBJECT) {
                for (ModelNode mn : model.get(key).asList()) {
                    boolean undefined = true;
                    Property p = mn.asProperty();
                    if (p.getValue().getType() != ModelType.OBJECT) { continue; }
                    for (String subKey : new HashSet<String>(p.getValue().keys())) {
                        if (copy.get(key, p.getName()).hasDefined(subKey)) {
                            undefined = false;
                            break;
                        } else {
                            copy.get(key, p.getName()).remove(subKey);
                        }
                    }
                    if (undefined) {
                        copy.get(key).remove(p.getName());
                        if (!copy.hasDefined(key)) {
                            copy.remove(key);
                        } else if (copy.get(key).getType() == ModelType.OBJECT) {     //this is stupid workaround
                            if (copy.get(key).keys().isEmpty()) {
                                copy.remove(key);
                            }
                        }
                    }
                }
            }
        }
        return copy;
    }

    private static String getCompareStackAsString(Stack<String> stack) {
        StringBuilder buf = new StringBuilder();
        for (String element : stack) {
            buf.append(element);
        }
        return buf.toString();
    }

    public static void checkModelAgainstDefinition(final ModelNode model, ManagementResourceRegistration rr) {
        checkModelAgainstDefinition(model, rr, new Stack<PathElement>());
    }

    private static void checkModelAgainstDefinition(final ModelNode model, ManagementResourceRegistration rr, Stack<PathElement> stack) {
        final Set<String> children = rr.getChildNames(PathAddress.EMPTY_ADDRESS);
        final Set<String> attributeNames = rr.getAttributeNames(PathAddress.EMPTY_ADDRESS);
        for (ModelNode el : model.asList()) {
            String name = el.asProperty().getName();
            ModelNode value = el.asProperty().getValue();
            if (attributeNames.contains(name)) {
                AttributeAccess aa = rr.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name);
                Assert.assertNotNull(getComparePathAsString(stack) + " Attribute " + name + " is not known", aa);
                AttributeDefinition ad = aa.getAttributeDefinition();
                if (!value.isDefined()) {
                    // check if the attribute definition allows null *or* if its default value is null
                    Assert.assertTrue(getComparePathAsString(stack) + " Attribute " + name + " does not allow null", (!ad.isRequired() || ad.getDefaultValue() == null));
                } else {
                   // Assert.assertEquals("Attribute '" + name + "' type mismatch", value.getType(), ad.getType()); //todo re-enable this check
                }
                try {
                    if (ad.isRequired() && value.isDefined()){
                        ad.getValidator().validateParameter(name, value);
                    }
                } catch (OperationFailedException e) {
                    Assert.fail(getComparePathAsString(stack) + " validation for attribute '" + name + "' failed, " + e.getFailureDescription().asString());
                }

            } else if (!children.contains(name)) {
                Assert.fail(getComparePathAsString(stack) + " Element '" + name + "' is not known in target definition");
            }
        }


        // When we have overrides those should be used for the checking. The set of child addresses however,
        // may have the wildcard children before more specific ones. So we move the specific ones first
        List<PathElement> sortedChildAddresses = ModelTestUtils.moveWildcardChildrenToEnd(rr.getChildAddresses(PathAddress.EMPTY_ADDRESS));
        Set<PathElement> handledChildren = new HashSet<>();
        for (PathElement pe : sortedChildAddresses) {
            if (pe.isWildcard()) {
                if (children.contains(pe.getKey()) && model.hasDefined(pe.getKey())) {
                    for (ModelNode v : model.get(pe.getKey()).asList()) {
                        String name = v.asProperty().getName();
                        if (!handledChildren.contains(PathElement.pathElement(pe.getKey(), name))) {
                            ModelNode value = v.asProperty().getValue();
                            ManagementResourceRegistration sub = rr.getSubModel(PathAddress.pathAddress(pe));
                            Assert.assertNotNull(getComparePathAsString(stack) + " Child with name '" + name + "' not found", sub);
                            if (value.isDefined()) {
                                stack.push(pe);
                                checkModelAgainstDefinition(value, sub, stack);
                                stack.pop();
                            }
                        }
                    }
                }
            } else {
                if (children.contains(pe.getKey()) && model.hasDefined(pe.getKey()) && model.get(pe.getKey()).hasDefined(pe.getValue())) {
                    String name = pe.getValue();
                    ModelNode value = model.get(pe.getKeyValuePair());
                    ManagementResourceRegistration sub = rr.getSubModel(PathAddress.pathAddress(pe));
                    Assert.assertNotNull(getComparePathAsString(stack) + " Child with name '" + name + "' not found", sub);
                    if (value.isDefined()) {
                        stack.push(pe);
                        checkModelAgainstDefinition(value, sub, stack);
                        stack.pop();
                    }
                }
            }
            handledChildren.add(pe);
        }
    }

    private static String getComparePathAsString(Stack<PathElement> stack) {
        PathAddress pa = PathAddress.EMPTY_ADDRESS;
        for (PathElement element : stack) {
            pa = pa.append(element);
        }
        return pa.toModelNode().asString();
    }

    /**
     * <P>A standard test for transformers where things should be rejected.
     * Takes the operations and installs them in the main controller.
     * </P>
     * <P>
     * It then attempts to transform the same operations for the legacy controller, validating that expected
     * failures take place.
     * It then attempts to fix the operations so they can be executed in the legacy controller, since if an 'add' fails,
     * there could be adds for children later in the list.
     * </P>
     * <P>
     * Internally the operations (both for the main and legacy controllers) are added to a composite so that we have
     * everything we need if any versions of the subsystem use capabilities and requirements. Normally this composite
     * will contain the original operations that have been fixed by the {@code config}. This composite is then transformed
     * before executing it in the legacy controller. However, in some extreme cases the one-shot transformation of the
     * composite intended for the legacy controller may not be possible. For these cases you can call
     * {@link FailedOperationTransformationConfig#setDontTransformComposite()} and the individually transformed operations
     * get added to the composite, which is then used as-is (without any transformation).
     * </P>
     * <P>
     * To configure a callback that gets executed before the composite is transformed for the legacy controller,
     * and executed there, you can call
     * {@link FailedOperationTransformationConfig#setCallback(FailedOperationTransformationConfig.BeforeExecuteCompositeCallback)}.
     * </P>
     *
     * @param mainServices The main controller services
     * @param modelVersion The version of the legacy controller
     * @param operations the operations
     * @param config the config
     */
    public static void checkFailedTransformedBootOperations(ModelTestKernelServices<?> mainServices,
                                                            ModelVersion modelVersion, List<ModelNode> operations,
                                                            FailedOperationTransformationConfig config) throws OperationFailedException {
        //Create a composite to execute all the boot operations in the main controller.
        //We execute the operations in the main controller to make sure it is a valid config in the main controller
        //The reason this needs to be a composite is that an add operation for a resource might have a reference on
        //a capability introduced by a later add operation, and the controller has already started
        final ModelNode mainComposite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        final ModelNode mainSteps = mainComposite.get(STEPS).setEmptyList();
        for (ModelNode op : operations) {
            mainSteps.add(op.clone());
        }
        Assert.assertEquals(operations, mainSteps.asList());
        ModelTestUtils.checkOutcome(mainServices.executeOperation(mainComposite));

        //Next check the transformations of the operations for the legacy controller, fixing them up from the config when
        //they are rejected.

        //We gather all the transformed operations into a composite which we then execute in the legacy controller. The
        //reason this is a composite is the same as for the main controller.
        final ModelNode legacyComposite = Util.createEmptyOperation(COMPOSITE, PathAddress.EMPTY_ADDRESS);
        final ModelNode legacySteps = legacyComposite.get(STEPS).setEmptyList();

        //We also check all the attributes by executing write operations after we have created the legacy composite.
        //Let's gather these while checking rejections of the ops, and creating the legacy composite.
        final List<ModelNode> writeOps = new ArrayList<>();
        for (ModelNode op : operations) {
            writeOps.addAll(config.createWriteAttributeOperations(op));
            checkFailedTransformedAddOperation(mainServices, modelVersion, op, config, legacySteps);
        }

        config.invokeCallback();

        if (config.isTransformComposite()) {
            //Transform and execute the composite
            TransformedOperation transformedComposite = mainServices.transformOperation(modelVersion, legacyComposite);
            if (transformedComposite.rejectOperation(successResult())) {
                Assert.fail(transformedComposite.getFailureDescription());
            }
            ModelTestUtils.checkOutcome(mainServices.executeOperation(modelVersion, transformedComposite));
        } else {
            //The composite already contains the transformed operations
            ModelTestKernelServices<?> legacyServices = mainServices.getLegacyServices(modelVersion);
            ModelTestUtils.checkOutcome(legacyServices.executeOperation(legacyComposite));
        }

        //Check all the write ops
        for (ModelNode writeOp : writeOps) {
            checkFailedTransformedWriteAttributeOperation(mainServices, modelVersion, writeOp, config);
        }
    }

    private static void checkFailedTransformedAddOperation(
            ModelTestKernelServices<?> mainServices, ModelVersion modelVersion,
            ModelNode operation, FailedOperationTransformationConfig config, ModelNode legacySteps) throws OperationFailedException {
        TransformedOperation transformedOperation = mainServices.transformOperation(modelVersion, operation.clone());
        if (config.expectFailed(operation)) {
            Assert.assertTrue("Expected transformation to get rejected " + operation + " for version " + modelVersion, transformedOperation.rejectOperation(successResult()));
            Assert.assertNotNull("Expected transformation to get rejected " + operation + " for version " + modelVersion , transformedOperation.getFailureDescription());
            if (config.canCorrectMore(operation)) {
                checkFailedTransformedAddOperation(mainServices, modelVersion, config.correctOperation(operation), config, legacySteps);
            }
        } else if (config.expectDiscarded(operation)) {
            Assert.assertNull("Expected null transformed operation for discarded " + operation, transformedOperation.getTransformedOperation());
            Assert.assertFalse("Expected transformation to not be rejected for discarded " + operation, transformedOperation.rejectOperation(successResult()));
        } else {
            if (transformedOperation.rejectOperation(successResult())) {
                Assert.fail(operation + " should not have been rejected " + transformedOperation.getFailureDescription());
            }
            Assert.assertFalse(config.canCorrectMore(operation));

            config.operationDone(operation);

            if (config.isTransformComposite()) {
                //Add the original operation here since the legacy composite as a whole
                // will be transformed by checkFailedTransformedBootOperations()
                legacySteps.add(operation);
            } else {
                ModelNode transformed = transformedOperation.getTransformedOperation();
                if (transformed != null) {
                    legacySteps.add(transformed);
                }
            }
        }
    }

    private static void checkFailedTransformedWriteAttributeOperation(ModelTestKernelServices<?> mainServices, ModelVersion modelVersion, ModelNode operation, FailedOperationTransformationConfig config) throws OperationFailedException {
        TransformedOperation transformedOperation = mainServices.transformOperation(modelVersion, operation.clone());
        if (config.expectFailedWriteAttributeOperation(operation)) {
            Assert.assertNotNull("Expected transformation to get rejected " + operation, transformedOperation.getFailureDescription());
            //For write-attribute we currently only correct once, all in one go
            checkFailedTransformedWriteAttributeOperation(mainServices, modelVersion, config.correctWriteAttributeOperation(operation), config);
        } else {
            ModelNode result = mainServices.executeOperation(modelVersion, transformedOperation);
            Assert.assertEquals("Failed: " + operation + "\n: " + result, SUCCESS, result.get(OUTCOME).asString());
        }
    }

    private static ModelNode successResult() {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        result.get(ModelDescriptionConstants.RESULT);
        return result;
    }

    public static List<PathElement> moveWildcardChildrenToEnd(Set<PathElement> children) {
        Set<String> childKeys = new LinkedHashSet<>();
        Map<String, List<PathElement>> childMap = new HashMap<>();
        Map<String, PathElement> childWildcards = new HashMap<>();

        for (PathElement child : children) {
            String key = child.getKey();
            childKeys.add(key);

            if (child.isWildcard()) {
                childWildcards.putIfAbsent(key, child);
            } else {
                List<PathElement> childrenForKey = childMap.get(key);
                if (childrenForKey == null) {
                    childrenForKey = new ArrayList<>();
                    childMap.put(key, childrenForKey);
                }
                childrenForKey.add(child);
            }
        }

        List<PathElement> result = new ArrayList<>();
        for (String key : childKeys) {
            List<PathElement> childrenForKey = childMap.get(key);
            if (childrenForKey != null) {
                result.addAll(childrenForKey);
            }
            PathElement wildcardForKey = childWildcards.get(key);
            if (wildcardForKey != null) {
                result.add(wildcardForKey);
            }
        }

        return result;
    }
}
