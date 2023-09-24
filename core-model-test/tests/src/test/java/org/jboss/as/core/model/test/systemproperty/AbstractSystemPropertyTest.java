/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.systemproperty;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.ModelInitializer;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractSystemPropertyTest extends AbstractCoreModelTest {

    static final String PROP_ONE = "sys.prop.test.one";
    static final String PROP_TWO = "sys.prop.test.two";
    static final String PROP_THREE = "sys.prop.test.three";
    static final String PROP_FOUR = "sys.prop.test.four";
    static final String PROP_FIVE = "sys.prop.test.five";
    static final String PROP_SIX = "sys.prop.test.six";

    final boolean standalone;
    final boolean domain;

    public AbstractSystemPropertyTest(boolean standalone, boolean domain) {
        this.standalone = standalone;
        this.domain = domain;
    }

    @Before
    public void clearAllProperties() {
        System.clearProperty(PROP_ONE);
        System.clearProperty(PROP_TWO);
        System.clearProperty(PROP_THREE);
        System.clearProperty(PROP_FOUR);
        System.clearProperty(PROP_FIVE);
        System.clearProperty(PROP_SIX);
    }

    @Test
    public void testAddRemoveSystemProperties() throws Exception {
        KernelServices kernelServices = createEmptyRoot();

        ModelNode props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertFalse(props.isDefined());

        ModelNode add = Util.createAddOperation(getSystemPropertyAddress(PROP_ONE));
        add.get(VALUE).set("one");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertTrue(props.isDefined());
        Assert.assertEquals(1, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        }

        add = Util.createAddOperation(getSystemPropertyAddress(PROP_TWO));
        add.get(VALUE).set("two");
        if (!standalone) {
            add.get(BOOT_TIME).set(false);
        }
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertTrue(props.isDefined());
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("two", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertFalse(props.get(PROP_ONE, BOOT_TIME).isDefined());
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("two", System.getProperty(PROP_TWO));
        }

        ModelNode remove = Util.createRemoveOperation(getSystemPropertyAddress(PROP_TWO));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(remove));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertTrue(props.isDefined());
        Assert.assertEquals(1, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());

        add = Util.createAddOperation(getSystemPropertyAddress(PROP_TWO));
        add.get(VALUE).set(new ValueExpression("${" + PROP_ONE + "}"));
        if (!standalone) {
            add.get(BOOT_TIME).set(true);
        }
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("${" + PROP_ONE + "}", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertTrue(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("one", System.getProperty(PROP_TWO));
        }


        remove = Util.createRemoveOperation(getSystemPropertyAddress(PROP_TWO));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(remove));

        add = Util.createAddOperation(getSystemPropertyAddress(PROP_TWO));
        add.get(VALUE).set("");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertTrue(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("", System.getProperty(PROP_TWO));
        }

        remove = Util.createRemoveOperation(getSystemPropertyAddress(PROP_TWO));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(remove));

        add = Util.createAddOperation(getSystemPropertyAddress(PROP_TWO));
        add.get(VALUE); // undefined
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertTrue(props.keys().contains(PROP_TWO));
        Assert.assertFalse(props.get(PROP_TWO, VALUE).isDefined());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertTrue(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertNull("", System.getProperty(PROP_TWO));
        }


        remove = Util.createRemoveOperation(getSystemPropertyAddress(PROP_TWO));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(remove));

        remove = Util.createRemoveOperation(getSystemPropertyAddress(PROP_ONE));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(remove));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertFalse(props.isDefined());
    }

    @Test
    public void testWriteSystemProperty() throws Exception {
        KernelServices kernelServices = createEmptyRoot();

        ModelNode props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertFalse(props.isDefined());

        ModelNode add = Util.createAddOperation(getSystemPropertyAddress(PROP_ONE));
        add.get(VALUE).set("one");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        add = Util.createAddOperation(getSystemPropertyAddress(PROP_TWO));
        add.get(VALUE).set("two");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(add));

        ModelNode write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
        write.get(NAME).set(VALUE);
        write.get(VALUE).set("dos");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(write));

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("dos", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertTrue(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("dos", System.getProperty(PROP_TWO));
        }

        if (!standalone) {
            write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
            write.get(NAME).set(BOOT_TIME);
            write.get(VALUE).set(false);
            ModelTestUtils.checkOutcome(kernelServices.executeOperation(write));
        }

        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("dos", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("dos", System.getProperty(PROP_TWO));
        }


        write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
        write.get(NAME).set(VALUE);
        write.get(VALUE).set(new ValueExpression("${" + PROP_ONE + "}"));
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(write));
        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("${" + PROP_ONE + "}", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("one", System.getProperty(PROP_TWO));
        }

        write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
        write.get(NAME).set(VALUE);
        write.get(VALUE).set("");
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(write));
        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("", props.get(PROP_TWO, VALUE).asString());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertEquals("", System.getProperty(PROP_TWO));
        }

        write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
        write.get(NAME).set(VALUE);
        write.get(VALUE); //Undefined
        ModelTestUtils.checkOutcome(kernelServices.executeOperation(write));
        props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(2, props.keys().size());
        Assert.assertEquals("one", props.get(PROP_ONE, VALUE).asString());
        Assert.assertTrue(props.keys().contains(PROP_TWO));
        Assert.assertFalse(props.get(PROP_TWO, VALUE).isDefined());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        } else {
            Assert.assertEquals("one", System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
        }

        write = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, getSystemPropertyAddress(PROP_TWO));
        write.get(NAME).set(BOOT_TIME);
        write.get(VALUE); //Undefined
    }

    @Test
    public void testXml() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(true)
                .setXmlResource(getXmlResource())
                .setModelInitializer(getModelInitializer(), null)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String xmlOriginal = ModelTestUtils.readResource(this.getClass(), getXmlResource());
        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(xmlOriginal, marshalled);

        ModelNode props = readSystemPropertiesParentModel(kernelServices);
        Assert.assertEquals(standalone || domain ? 5 : 6, props.keys().size());

        Assert.assertEquals("1", props.get(PROP_ONE, VALUE).asString());
        Assert.assertEquals("2", props.get(PROP_TWO, VALUE).asString());
        Assert.assertEquals("3", props.get(PROP_THREE, VALUE).asString());
        Assert.assertEquals(" six ", props.get(PROP_SIX, VALUE).asString());
        Assert.assertFalse(props.get(PROP_FOUR, VALUE).isDefined());
        if (!standalone) {
            Assert.assertTrue(props.get(PROP_ONE, BOOT_TIME).asBoolean());
            Assert.assertTrue(props.get(PROP_TWO, BOOT_TIME).asBoolean());
            Assert.assertFalse(props.get(PROP_THREE, BOOT_TIME).asBoolean());
            Assert.assertNull(System.getProperty(PROP_ONE));
            Assert.assertNull(System.getProperty(PROP_TWO));
            Assert.assertNull(System.getProperty(PROP_THREE));
            if (!domain) {
                Assert.assertEquals(ModelType.EXPRESSION, props.get(PROP_FIVE, VALUE).getType());
                Assert.assertEquals("5", ExpressionResolver.TEST_RESOLVER.resolveExpressions(props.get(PROP_FIVE, VALUE)).asString());
                Assert.assertEquals(ModelType.EXPRESSION, props.get(PROP_FIVE, BOOT_TIME).getType());
                Assert.assertFalse(ExpressionResolver.TEST_RESOLVER.resolveExpressions(props.get(PROP_FIVE, BOOT_TIME)).asBoolean());
            }
        } else {
            Assert.assertEquals("1", System.getProperty(PROP_ONE));
            Assert.assertEquals("2", System.getProperty(PROP_TWO));
            Assert.assertEquals("3", System.getProperty(PROP_THREE));
        }
    }

    protected abstract PathAddress getSystemPropertyAddress(String propName);

    protected abstract KernelServicesBuilder createKernelServicesBuilder(boolean xml);

    protected abstract KernelServices createEmptyRoot() throws Exception;

    protected abstract ModelNode readSystemPropertiesParentModel(KernelServices kernelServices);

    protected abstract String getXmlResource();

    protected ModelInitializer getModelInitializer() {
        return ModelInitializer.NO_OP;
    }
}
