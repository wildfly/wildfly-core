/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

/**
 * A test routine every subsystem should go through.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="stefano.maestri@redhat.com>Stefano Maestri</a>
 */
public abstract class AbstractSubsystemBaseTest extends AbstractSubsystemTest {

    public AbstractSubsystemBaseTest(final String mainSubsystemName, final Extension mainExtension) {
        super(mainSubsystemName, mainExtension);
    }

    public AbstractSubsystemBaseTest(final String mainSubsystemName, final Extension mainExtension, final Comparator<PathAddress> removeOrderComparator) {
        super(mainSubsystemName, mainExtension, removeOrderComparator);
    }

    /**
     * Get the subsystem xml as string.
     *
     * @return the subsystem xml
     * @throws IOException
     */
    protected abstract String getSubsystemXml() throws IOException;

    /**
     * Get the pathc of the subsystem XML Schema.
     *
     * If the returned value is not null, the subsystem's XML will be validated against these schemas in #testSchema
     *
     * By default, this method returns an null (thus disabling the #testSchema and #testSchemaOfSubsystemTemplates tests).
     *
     * Note that the XSD validation may fail if the XML contains attributes or text that uses expressions.
     * In that case, you will have to make sure that the corresponding expressions have resolved properties
     * returned by #getResolvedProperties.
     */
    protected String getSubsystemXsdPath() throws Exception {
        return null;
    }

    /**
     * Get the paths of the subsystem XML templates (such as <code>/subsystem-templates/io.xml</code> file for the IO subsystem).
     *
     * If the returned value is not null, the template &lt;subsystem&gt; element will be validated against this schema
     * returned by #getSubsystemXsdPaths in #testSchemaOfSubsystemTemplates.
     *
     * Note that the XSD validation may fail if the XML contains attributes or text that uses expressions.
     * In that case, you will have to make sure that the corresponding expressions have resolved properties
     * returned by #getResolvedProperties.
     */
    @Deprecated
    protected String[] getSubsystemTemplatePaths() throws IOException {
        return new String[0];
    }

    /**
     * Return the properties to use to resolve any expressions in the subsystem's XML prior to its XSD validation.
     *
     * If the XML contains an expression instead of a valid type (e.g. a boolean or a int), the XSD validation will
     * fail. To make sure the XML is valid (except for those expressions), this method can be used to resolve any such
     * expressions in the XML before the validation process.
     */
    protected Properties getResolvedProperties() {
        return new Properties();
    }

    /**
     * Get the subsystem xml with the given id as a string.
     * <p>
     * This default implementation returns the result of a call to {@link #readResource(String)}.
     * </p>
     *
     * @param configId the id of the xml configuration
     *
     * @return the subsystem xml
     * @throws IOException
     */
    protected String getSubsystemXml(String configId) throws IOException {
        return readResource(configId);

    }

    /**
     * Get the comparison subsystem xml to compare marshalled output
     * <p>
     * This default implementation returns null, causing the original source to be used
     * </p>
     *
     * @return the comparison xml or null to compare with the source
     * @throws IOException
     */
    protected String getComparisonXml() throws IOException {
        return null;
    }

    /**
     * Get the comparison subsystem xml to compare marshalled output using the given id as a string.
     * <p>
     * This default implementation returns null, causing the original source to be used
     * </p>
     *
     * @param configId the id of the xml configuration
     *
     * @return the comparison xml or null to compare with the source
     * @throws IOException
     */
    protected String getComparisonXml(String configId) throws IOException {
        return null;
    }

    @Test
    public void testSubsystem() throws Exception {
        standardSubsystemTest(null);
    }

    @Test
    public void testSchema() throws Exception {
        String schemaPath = getSubsystemXsdPath();
        Assume.assumeTrue("getSubsystemXsdPath() has been overridden to disable the validation of the subsystem templates",
                schemaPath != null);
        SchemaValidator.validateXML(getSubsystemXml(), schemaPath, getResolvedProperties());
    }

    /**
     * To enable a test for validation of the subsystem templates, override both {@link #getSubsystemXsdPath()} and {@link #getSubsystemTemplatePaths()}
     * then add a new test just calling this method.
     */
    @Deprecated
    protected void testSchemaOfSubsystemTemplates() throws Exception {
        throw new AssumptionViolatedException("testSchemaOfSubsystemTemplates is deprecated");
    }

    /**
     * Tests the ability to create a model from an xml configuration, marshal the model back to xml,
     * re-read that marshalled model into a new model that matches the first one, execute a "describe"
     * operation for the model, create yet another model from executing the results of that describe
     * operation, and compare that model to first model.
     *
     * @param configId  id to pass to {@link #getSubsystemXml(String)} to get the configuration; if {@code null}
     *                  {@link #getSubsystemXml()} will be called
     *
     * @throws Exception
     */
    protected void standardSubsystemTest(final String configId) throws Exception {
        standardSubsystemTest(configId, true);
    }

    protected KernelServices standardSubsystemTest(final String configId, boolean compareXml) throws Exception {
        return standardSubsystemTest(configId, null, compareXml);
    }

    protected void standardSubsystemTest(final String configId, final String configIdResolvedModel) throws Exception {
        standardSubsystemTest(configId, configIdResolvedModel, true);
    }

    protected KernelServices standardSubsystemTest(final String configId, final String configIdResolvedModel, boolean compareXml) throws Exception {
        return standardSubsystemTest(configId, configIdResolvedModel, compareXml, createAdditionalInitialization());
    }


    /**
     * Tests the ability to create a model from an xml configuration, marshal the model back to xml,
     * re-read that marshalled model into a new model that matches the first one, execute a "describe"
     * operation for the model, create yet another model from executing the results of that describe
     * operation, and compare that model to first model.
     * if configIdResolvedModel is not null compare the model from configId and one from configIdResolvedModel
     * after all expression have been reolved.
     *
     * @param configId  id to pass to {@link #getSubsystemXml(String)} to get the configuration; if {@code null}
     *                  {@link #getSubsystemXml()} will be called
     * @param configIdResolvedModel  id to pass to {@link #getSubsystemXml(String)} to get the configuration;
     *                               it is the expected result of resolve() on configId if {@code null}
     ]                               this step is skipped
     *
     * @param compareXml if {@code true} a comparison of xml output to original input is performed. This can be
     *                   set to {@code false} if the original input is from an earlier xsd and the current
     *                   schema has a different output
     * @param additionalInit service container and model initialization
     *
     * @throws Exception
     */
    protected KernelServices standardSubsystemTest(final String configId, final String configIdResolvedModel,
                                                   boolean compareXml, final AdditionalInitialization additionalInit) throws Exception {

        // Parse the subsystem xml and install into the first controller
        final String subsystemXml = configId == null ? getSubsystemXml() : getSubsystemXml(configId);
        final KernelServices servicesA = super.createKernelServicesBuilder(additionalInit).setSubsystemXml(subsystemXml).build();
        Assert.assertTrue("Subsystem boot failed!", servicesA.isSuccessfulBoot());
        //Get the model and the persisted xml from the first controller
        final ModelNode modelA = servicesA.readWholeModel();
        validateModel(modelA);
        ModelTestUtils.scanForExpressionFormattedStrings(modelA);

        // Test marshaling
        final String marshalled = servicesA.getPersistedSubsystemXml();
        servicesA.shutdown();


        // validate the the normalized xmls, validate without comparison as well
        String normalizedSubsystem = normalizeXML(subsystemXml);

        if (compareXml) {
            String comparisonXml = configId == null ? getComparisonXml() : getComparisonXml(configId);
            comparisonXml = comparisonXml != null ? normalizeXML(comparisonXml) : normalizedSubsystem;

            compareXml(configId, comparisonXml, normalizeXML(marshalled));
        }

        //Install the persisted xml from the first controller into a second controller
        final KernelServices servicesB = super.createKernelServicesBuilder(additionalInit).setSubsystemXml(marshalled).build();
        Assert.assertTrue("Subsystem boot failed!", servicesB.isSuccessfulBoot());
        final ModelNode modelB = servicesB.readWholeModel();

        //Make sure the models from the two controllers are identical
        compare(modelA, modelB);

        // Test the describe operation
        validateDescribeOperation(servicesB, additionalInit, modelA);

        assertRemoveSubsystemResources(servicesB, getIgnoredChildResourcesForRemovalTest());
        servicesB.shutdown();

        if (configIdResolvedModel != null) {
            final String subsystemResolvedXml = getSubsystemXml(configIdResolvedModel);
            final KernelServices servicesD = super.createKernelServicesBuilder(additionalInit).setSubsystemXml(subsystemResolvedXml).build();
            Assert.assertTrue("Subsystem w/ resolved xml boot failed!", servicesD.isSuccessfulBoot());
            final ModelNode modelD = servicesD.readWholeModel();
            validateModel(modelD);
            resolveandCompareModel(modelA, modelD);
        }
        return servicesA;

    }

    protected void validateDescribeOperation(KernelServices hc, AdditionalInitialization serverInit, ModelNode expectedModel) throws Exception {
        final ModelNode operation = createDescribeOperation();
        final ModelNode result = hc.executeOperation(operation);
        Assert.assertTrue("the subsystem describe operation has to generate a list of operations to recreate the subsystem: " + result.asString(),
                !result.hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION));
        final List<ModelNode> operations = result.get(ModelDescriptionConstants.RESULT).asList();

        final KernelServices servicesC = super.createKernelServicesBuilder(serverInit).setBootOperations(operations).build();
        Assert.assertTrue("Subsystem boot failed!", servicesC.isSuccessfulBoot());
        final ModelNode serverModel = servicesC.readWholeModel();

        compare(expectedModel, serverModel);

        servicesC.shutdown();

    }

    protected void validateModel(ModelNode model) {
        Assert.assertNotNull(model);
    }

    protected ModelNode createDescribeOperation() {
        final ModelNode address = new ModelNode();
        address.add(ModelDescriptionConstants.SUBSYSTEM, getMainSubsystemName());

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.DESCRIBE);
        operation.get(ModelDescriptionConstants.OP_ADDR).set(address);
        return operation;
    }

    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.MANAGEMENT;
    }

    /**
     * Returns a set of child resources addresses that should not be removed directly. Rather they should be managed
     * by their parent resource.
     * <p/>
     * The last element of each address is allowed to be a wildcard address.
     *
     * @return the set of child resource addresses
     * @see AbstractSubsystemTest#assertRemoveSubsystemResources(KernelServices, Set)
     */
    protected Set<PathAddress> getIgnoredChildResourcesForRemovalTest() {
        return Collections.emptySet();
    }
}
