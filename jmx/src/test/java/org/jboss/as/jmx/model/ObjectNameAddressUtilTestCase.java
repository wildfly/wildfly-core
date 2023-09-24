/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import static org.jboss.as.controller.PathElement.pathElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceBuilder;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ObjectNameAddressUtilTestCase {

    static final String TOP = "top";
    static final String ONE = "one";
    static final String BOTTOM = "bottom";
    static final String TWO = "two";
    static final String COMPLEX_KEY = "*";
    static final String COMPLEX_VALUE = "\":=*?\n {}[] \":=*?\n";
    static final PathElement TOP_ONE = pathElement(TOP, ONE);
    static final PathElement BOTTOM_TWO = pathElement(BOTTOM, TWO);
    static final PathElement TOP_COMPLEX_VALUE = pathElement(TOP, COMPLEX_VALUE);
    static final PathElement COMPLEX_KEY_ONE = pathElement(COMPLEX_KEY, ONE);
    static final ResourceDefinition rootResourceDef = ResourceBuilder.Factory.create(PathElement.pathElement("test"), NonResolvingResourceDescriptionResolver.INSTANCE).build();

    static final Resource rootResource;
    static{
        Resource root = new TestResource();
        Resource topOne = new TestResource();
        root.registerChild(TOP_ONE, topOne);
        topOne.registerChild(BOTTOM_TWO, new TestResource());

        root.registerChild(TOP_COMPLEX_VALUE, new TestResource());
        root.registerChild(COMPLEX_KEY_ONE, new TestResource());
        rootResource = root;
    }


    @Test
    public void testSimpleAddress() throws Exception {
        checkObjectName(TOP_ONE, BOTTOM_TWO);
    }

    @Test
    public void testComplexValueAddress() throws Exception {
        checkObjectName(TOP_COMPLEX_VALUE);
    }

    @Test
    public void testComplexKeyAddress() throws Exception {
        checkObjectName(COMPLEX_KEY_ONE);
    }

    private void checkObjectName(PathElement...elements) {
        PathAddress pathAddress = PathAddress.pathAddress(elements);
        ObjectName on = ObjectNameAddressUtil.createObjectName("jboss.as", pathAddress);
        Assert.assertNotNull(on);
        PathAddress resolved = ObjectNameAddressUtil.resolvePathAddress("jboss.as", rootResource, on);
        Assert.assertEquals(pathAddress, resolved);
    }

    @Test
    public void testToPathAddress() {

        NonResolvingResourceDescriptionResolver resolver = NonResolvingResourceDescriptionResolver.INSTANCE;

        ManagementResourceRegistration rootRegistration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(rootResourceDef);
        ManagementResourceRegistration subsystemRegistration = rootRegistration.registerSubModel(new SimpleResourceDefinition(pathElement("subsystem", "foo"), resolver));
        ManagementResourceRegistration resourceRegistration = subsystemRegistration.registerSubModel(new SimpleResourceDefinition(pathElement("resource", "resourceA"), resolver));
        ManagementResourceRegistration subresourceRegistration = resourceRegistration.registerSubModel(new SimpleResourceDefinition(pathElement("subresource", "resourceB"), resolver));

        PathAddress pathAddress = PathAddress.pathAddress("subsystem", "foo")
                .append("resource", "resourceA")
                .append("subresource", "resourceB");
        ObjectName on = ObjectNameAddressUtil.createObjectName("jboss.as", pathAddress);

        PathAddress convertedAddress = ObjectNameAddressUtil.toPathAddress("jboss.as", rootRegistration, on);
        Assert.assertNotNull(convertedAddress);
        Assert.assertEquals(pathAddress, convertedAddress);
    }

    private static class TestResource implements Resource {

        private Map<String, Map<String, Resource>> children = new HashMap<String, Map<String,Resource>>();

        @Override
        public Resource getChild(PathElement element) {
            Map<String, Resource> resources = children.get(element.getKey());
            if (resources != null) {
                return resources.get(element.getValue());
            }
            return null;
        }

        @Override
        public void registerChild(PathElement address, Resource resource) {
            Map<String, Resource> resources = children.get(address.getKey());
            if (resources == null) {
                resources = new HashMap<String, Resource>();
                children.put(address.getKey(), resources);
            }
            resources.put(address.getValue(), resource);
        }

        @Override
        public void registerChild(PathElement address, int index, Resource resource) {
            throw new UnsupportedOperationException();
        }

        //THe rest of these don't currently get called
        @Override
        public void writeModel(ModelNode newModel) {
        }

        @Override
        public Resource requireChild(PathElement element) {
            return null;
        }

        @Override
        public Resource removeChild(PathElement address) {
            return null;
        }

        @Override
        public Resource navigate(PathAddress address) {
            return null;
        }

        @Override
        public boolean isRuntime() {
            return false;
        }

        @Override
        public boolean isProxy() {
            return false;
        }

        @Override
        public Set<String> getOrderedChildTypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isModelDefined() {
            return false;
        }

        @Override
        public boolean hasChildren(String childType) {
            return false;
        }

        @Override
        public boolean hasChild(PathElement element) {
            return false;
        }

        @Override
        public ModelNode getModel() {
            return null;
        }

        @Override
        public Set<String> getChildrenNames(String childType) {
            return null;
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            return null;
        }

        @Override
        public Set<String> getChildTypes() {
            return null;
        }

        public Resource clone() {
            return this;
        }

    };
}
