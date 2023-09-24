/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.api;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

import java.util.concurrent.TimeoutException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SPEC;
import static org.jboss.dmr.ModelType.LIST;
import static org.jboss.dmr.ModelType.OBJECT;

/**
 * @author <a href="mailto:rjanik@redhat.com">Richard Janík</a>
 */
public abstract class ReadConfigAsFeaturesTestBase {

    @Before
    public void setUp() throws UnsuccessfulOperationException {
        saveDefaultConfig();
        saveDefaultResult();
        saveNonNestedResult();
    }

    @After
    public void tearDown() throws TimeoutException, InterruptedException {
        restoreDefaultConfig();
    }

    protected abstract void saveDefaultConfig() throws UnsuccessfulOperationException;

    protected abstract void saveDefaultResult() throws UnsuccessfulOperationException;

    protected abstract void saveNonNestedResult() throws UnsuccessfulOperationException;

    protected abstract void restoreDefaultConfig() throws TimeoutException, InterruptedException;

    protected ModelNode getFeatureNodeChild(ModelNode node, String spec) {
        return getListElement(node.get(CHILDREN), spec);
    }

    protected ModelNode getFeatureNodeChild(ModelNode node, String spec, ModelNode id) {
        return getListElement(node.get(CHILDREN), spec, id);
    }

    protected int getFeatureNodeChildIndex(ModelNode node, String spec) {
        return getListElementIndex(node.get(CHILDREN), spec);
    }

    protected int getFeatureNodeChildIndex(ModelNode node, String spec, ModelNode id) {
        return getListElementIndex(node.get(CHILDREN), spec, id);
    }

    protected ModelNode getListElement(ModelNode list, String spec) {
        return getListElement(list, spec, null);
    }

    protected ModelNode getListElement(ModelNode list, String spec, ModelNode id) {
        for (ModelNode element : list.asList()) {
            if (element.get(SPEC).asString().equals(spec) &&
                    (id == null || id.equals(element.get(ID)))) {
                return element;
            }
        }

        throw new IllegalArgumentException("no element for spec " + spec + " and id " + ((id == null) ? "null" : id.toJSONString(true)));
    }

    protected int getListElementIndex(ModelNode list, String spec) {
        return getListElementIndex(list, spec, null);
    }

    protected int getListElementIndex(ModelNode list, String spec, ModelNode id) {
        for (int i = 0; i < list.asList().size(); i++) {
            if (list.get(i).get(SPEC).asString().equals(spec) &&
                    (id == null || id.equals(list.get(i).get(ID)))) {
                return i;
            }
        }
        throw new IllegalArgumentException("no element for spec " + spec + " and id " + ((id == null) ? "null" : id.toJSONString(true)));
    }

    private String getProgrammingErrorMessage(String expectedType) {
        return "model types are expected to be " + expectedType + "LIST, likely a programming error";
    }

    protected boolean isFeatureNode(ModelNode node) {
        return ModelType.OBJECT.equals(node.getType()) &&
                node.hasDefined(SPEC);
    }

    protected boolean containsFeatureNodes(ModelNode node) {
        switch (node.getType()) {
            case OBJECT:
                if (isFeatureNode(node)) return true;
                for (String key : node.keys()) {
                    if (containsFeatureNodes(node.get(key))) return true;
                }
                break;
            case LIST:
                for (ModelNode element : node.asList()) {
                    if (containsFeatureNodes(element)) return true;
                }
                break;
        }
        return false;
    }

    protected void ensureNoNestedSpecs(ModelNode model) {
        for (ModelNode element : model.asList()) {
            if (ModelType.OBJECT.equals(element.getType()) &&
                    isFeatureNode(element)) {
                for (String key : element.keys()) {
                    if (containsFeatureNodes(element.get(key)))
                        Assert.fail("There should be no nested feature nodes when using nested=false");
                }
            }
        }
    }

    /**
     * This is the entry-point for the {@code ModelNode} comparison, eschewing list order.
     */
    protected boolean equalsWithoutListOrder(ModelNode model1, ModelNode model2) {
        if (!model1.getType().equals(model2.getType())) {
            return false;
        }

        switch (model1.getType()) {
            case OBJECT:
                return compareObjects(model1, model2);
            case LIST:
                return compareLists(model1, model2);
            default:
                return model1.equals(model2);
        }
    }

    protected boolean compareLists(ModelNode model1, ModelNode model2) {
        Assert.assertEquals(getProgrammingErrorMessage("LIST"), model1.getType(), model2.getType());
        Assert.assertEquals(getProgrammingErrorMessage("LIST"), LIST, model1.getType());

        if (!(model1.asList().size() == model2.asList().size())) {
            return false;
        }

        for (ModelNode element : model1.asList()) {
            if (isFeatureNode(element)) {
                // if it's a feature node, we don't have to do a complicated comparison with every element of the other model
                try {
                    ModelNode model2Element = element.hasDefined(ID) ?
                            getListElement(model2, element.get(SPEC).asString(), element.get(ID)) :
                            getListElement(model2, element.get(SPEC).asString());
                    if (!equalsWithoutListOrder(element, model2Element)) return false;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else {
                // if it's not a feature node, let's find a matching element the hard way
                boolean foundMatch = false;
                for (ModelNode model2Element : model2.asList()) {
                    foundMatch = equalsWithoutListOrder(element, model2Element);
                    if (foundMatch) break;
                }
                if (!foundMatch) return false;
            }
        }

        return true;
    }

    protected boolean compareObjects(ModelNode model1, ModelNode model2) {
        Assert.assertEquals(getProgrammingErrorMessage("OBJECT"), model1.getType(), model2.getType());
        Assert.assertEquals(getProgrammingErrorMessage("OBJECT"), OBJECT, model1.getType());

        if (!model1.keys().equals(model2.keys())) {
            return false;
        }

        for (String key : model1.keys()) {
            switch (model1.get(key).getType()) {
                case OBJECT:
                    if (!compareObjects(model1.get(key), model2.get(key))) return false;
                    break;
                case LIST:
                    if (!compareLists(model1.get(key), model2.get(key))) return false;
                    break;
                default:
                    if (!model1.get(key).equals(model2.get(key))) return false;
            }
        }

        return true;
    }
}
