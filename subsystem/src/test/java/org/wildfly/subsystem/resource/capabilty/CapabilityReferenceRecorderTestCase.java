/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capabilty;

import static org.mockito.Mockito.*;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceRecorder;

/**
 * Unit test for {@link CapabilityReferenceRecorder}.
 */
public class CapabilityReferenceRecorderTestCase {

    @Test
    public void testUnary() {
        String attributeName = "attribute";
        NullaryServiceDescriptor<Object> descriptor = NullaryServiceDescriptor.of("capability", Object.class);
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(descriptor).build();
        UnaryServiceDescriptor<Object> requirement = UnaryServiceDescriptor.of("requirement", Object.class);
        CapabilityReferenceRecorder<Object> recorder = CapabilityReferenceRecorder.builder(capability, requirement).build();

        Assert.assertSame(capability, recorder.getDependent());
        Assert.assertEquals(capability.getName(), recorder.getBaseDependentName());
        Assert.assertSame(requirement, recorder.getRequirement());
        Assert.assertEquals(requirement.getName(), recorder.getBaseRequirementName());

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "test"), PathElement.pathElement("component", "foo"));
        OperationContext context = mock(OperationContext.class);
        Resource resource = mock(Resource.class);

        doReturn(address).when(context).getCurrentAddress();

        Assert.assertArrayEquals(new String[] { attributeName }, recorder.getRequirementPatternSegments(attributeName, address));

        ArgumentCaptor<String> capturedRequirement = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedDependent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedAttribute = ArgumentCaptor.forClass(String.class);

        doNothing().when(context).registerAdditionalCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.addCapabilityRequirements(context, resource, attributeName, "bar");

        List<String> requirements = capturedRequirement.getAllValues();
        List<String> dependents = capturedDependent.getAllValues();
        List<String> attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(1, requirements.size());
        Assert.assertEquals(1, dependents.size());
        Assert.assertEquals(1, attributes.size());

        Assert.assertEquals("requirement.bar", requirements.get(0));
        Assert.assertSame(capability.getName(), dependents.get(0));
        Assert.assertSame(attributeName, attributes.get(0));

        doNothing().when(context).deregisterCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.removeCapabilityRequirements(context, resource, attributeName, "bar");

        requirements = capturedRequirement.getAllValues();
        dependents = capturedDependent.getAllValues();
        attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(2, requirements.size());
        Assert.assertEquals(2, dependents.size());
        Assert.assertEquals(2, attributes.size());

        Assert.assertEquals("requirement.bar", requirements.get(1));
        Assert.assertSame(capability.getName(), dependents.get(1));
        Assert.assertSame(attributeName, attributes.get(1));
    }

    @Test
    public void testUnaryDynamic() {
        String attributeName = "attribute";
        UnaryServiceDescriptor<Object> descriptor = UnaryServiceDescriptor.of("capability", Object.class);
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(descriptor).build();
        UnaryServiceDescriptor<Object> requirement = UnaryServiceDescriptor.of("requirement", Object.class);
        CapabilityReferenceRecorder<Object> recorder = CapabilityReferenceRecorder.builder(capability, requirement).build();

        Assert.assertSame(capability, recorder.getDependent());
        Assert.assertEquals(capability.getName(), recorder.getBaseDependentName());
        Assert.assertSame(requirement, recorder.getRequirement());
        Assert.assertEquals(requirement.getName(), recorder.getBaseRequirementName());

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "test"), PathElement.pathElement("component", "foo"));
        ModelNode model = new ModelNode();
        model.get(attributeName).set("bar");
        OperationContext context = mock(OperationContext.class);
        Resource resource = mock(Resource.class);

        doReturn(address).when(context).getCurrentAddress();

        Assert.assertArrayEquals(new String[] { attributeName }, recorder.getRequirementPatternSegments(attributeName, address));

        ArgumentCaptor<String> capturedRequirement = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedDependent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedAttribute = ArgumentCaptor.forClass(String.class);

        doNothing().when(context).registerAdditionalCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.addCapabilityRequirements(context, resource, attributeName, "bar");

        List<String> requirements = capturedRequirement.getAllValues();
        List<String> dependents = capturedDependent.getAllValues();
        List<String> attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(1, requirements.size());
        Assert.assertEquals(1, dependents.size());
        Assert.assertEquals(1, attributes.size());

        Assert.assertEquals("requirement.bar", requirements.get(0));
        Assert.assertEquals("capability.foo", dependents.get(0));
        Assert.assertSame(attributeName, attributes.get(0));

        doNothing().when(context).deregisterCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.removeCapabilityRequirements(context, resource, attributeName, "bar");

        requirements = capturedRequirement.getAllValues();
        dependents = capturedDependent.getAllValues();
        attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(2, requirements.size());
        Assert.assertEquals(2, dependents.size());
        Assert.assertEquals(2, attributes.size());

        Assert.assertEquals("requirement.bar", requirements.get(1));
        Assert.assertEquals("capability.foo", dependents.get(1));
        Assert.assertSame(attributeName, attributes.get(1));
    }

    @Test
    public void testBinaryWithParentAttribute() throws OperationFailedException {
        String parentAttributeName = "parent-attribute";
        AttributeDefinition parentAttribute = SimpleAttributeDefinitionBuilder.create(parentAttributeName, ModelType.STRING).build();
        String attributeName = "attribute";
        NullaryServiceDescriptor<Object> descriptor = NullaryServiceDescriptor.of("capability", Object.class);
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(descriptor).build();
        BinaryServiceDescriptor<Object> requirement = BinaryServiceDescriptor.of("requirement", Object.class);
        CapabilityReferenceRecorder<Object> recorder = CapabilityReferenceRecorder.builder(capability, requirement).withParentAttribute(parentAttribute).build();

        Assert.assertSame(capability, recorder.getDependent());
        Assert.assertEquals(capability.getName(), recorder.getBaseDependentName());
        Assert.assertSame(requirement, recorder.getRequirement());
        Assert.assertEquals(requirement.getName(), recorder.getBaseRequirementName());

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "test"), PathElement.pathElement("component", "foo"));
        ModelNode model = new ModelNode();
        model.get(attributeName).set("bar");
        model.get(parentAttributeName).set("baz");
        OperationContext context = mock(OperationContext.class);
        Resource resource = mock(Resource.class);

        doReturn(address).when(context).getCurrentAddress();
        doReturn(model).when(resource).getModel();
        doAnswer(invocation -> invocation.getArgument(0)).when(context).resolveExpressions(any());

        Assert.assertArrayEquals(new String[] { parentAttributeName, attributeName }, recorder.getRequirementPatternSegments(attributeName, address));

        ArgumentCaptor<String> capturedRequirement = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedDependent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedAttribute = ArgumentCaptor.forClass(String.class);

        doNothing().when(context).registerAdditionalCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.addCapabilityRequirements(context, resource, attributeName, "bar");

        List<String> requirements = capturedRequirement.getAllValues();
        List<String> dependents = capturedDependent.getAllValues();
        List<String> attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(1, requirements.size());
        Assert.assertEquals(1, dependents.size());
        Assert.assertEquals(1, attributes.size());

        Assert.assertEquals("requirement.baz.bar", requirements.get(0));
        Assert.assertSame(capability.getName(), dependents.get(0));
        Assert.assertSame(attributeName, attributes.get(0));

        doNothing().when(context).deregisterCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.removeCapabilityRequirements(context, resource, attributeName, "bar");

        requirements = capturedRequirement.getAllValues();
        dependents = capturedDependent.getAllValues();
        attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(2, requirements.size());
        Assert.assertEquals(2, dependents.size());
        Assert.assertEquals(2, attributes.size());

        Assert.assertEquals("requirement.baz.bar", requirements.get(1));
        Assert.assertSame(capability.getName(), dependents.get(1));
        Assert.assertSame(attributeName, attributes.get(1));
    }

    @Test
    public void testBinaryWithParentPath() {
        String attributeName = "attribute";
        NullaryServiceDescriptor<Object> descriptor = NullaryServiceDescriptor.of("capability", Object.class);
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(descriptor).build();
        BinaryServiceDescriptor<Object> requirement = BinaryServiceDescriptor.of("requirement", Object.class);
        CapabilityReferenceRecorder<Object> recorder = CapabilityReferenceRecorder.builder(capability, requirement).withParentPath(PathElement.pathElement("component")).build();

        Assert.assertSame(capability, recorder.getDependent());
        Assert.assertEquals(capability.getName(), recorder.getBaseDependentName());
        Assert.assertSame(requirement, recorder.getRequirement());
        Assert.assertEquals(requirement.getName(), recorder.getBaseRequirementName());

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "test"), PathElement.pathElement("component", "foo"));
        ModelNode model = new ModelNode();
        model.get(attributeName).set("bar");
        OperationContext context = mock(OperationContext.class);
        Resource resource = mock(Resource.class);

        doReturn(address).when(context).getCurrentAddress();

        Assert.assertArrayEquals(new String[] { "component", attributeName }, recorder.getRequirementPatternSegments(attributeName, address));

        ArgumentCaptor<String> capturedRequirement = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedDependent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedAttribute = ArgumentCaptor.forClass(String.class);

        doNothing().when(context).registerAdditionalCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.addCapabilityRequirements(context, resource, attributeName, "bar");

        List<String> requirements = capturedRequirement.getAllValues();
        List<String> dependents = capturedDependent.getAllValues();
        List<String> attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(1, requirements.size());
        Assert.assertEquals(1, dependents.size());
        Assert.assertEquals(1, attributes.size());

        Assert.assertEquals("requirement.foo.bar", requirements.get(0));
        Assert.assertSame(capability.getName(), dependents.get(0));
        Assert.assertSame(attributeName, attributes.get(0));

        doNothing().when(context).deregisterCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.removeCapabilityRequirements(context, resource, attributeName, "bar");

        requirements = capturedRequirement.getAllValues();
        dependents = capturedDependent.getAllValues();
        attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(2, requirements.size());
        Assert.assertEquals(2, dependents.size());
        Assert.assertEquals(2, attributes.size());

        Assert.assertEquals("requirement.foo.bar", requirements.get(1));
        Assert.assertSame(capability.getName(), dependents.get(1));
        Assert.assertSame(attributeName, attributes.get(1));
    }

    @Test
    public void testTernary() throws OperationFailedException {
        String parentAttributeName = "parent-attribute";
        AttributeDefinition parentAttribute = SimpleAttributeDefinitionBuilder.create(parentAttributeName, ModelType.STRING).build();
        String attributeName = "attribute";
        NullaryServiceDescriptor<Object> descriptor = NullaryServiceDescriptor.of("capability", Object.class);
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(descriptor).build();
        TernaryServiceDescriptor<Object> requirement = TernaryServiceDescriptor.of("requirement", Object.class);
        CapabilityReferenceRecorder<Object> recorder = CapabilityReferenceRecorder.builder(capability, requirement).withGrandparentPath(PathElement.pathElement("component"), PathAddress::getLastElement).withParentAttribute(parentAttribute).build();

        Assert.assertSame(capability, recorder.getDependent());
        Assert.assertEquals(capability.getName(), recorder.getBaseDependentName());
        Assert.assertSame(requirement, recorder.getRequirement());
        Assert.assertEquals(requirement.getName(), recorder.getBaseRequirementName());

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("subsystem", "test"), PathElement.pathElement("component", "foo"));
        ModelNode model = new ModelNode();
        model.get(attributeName).set("bar");
        model.get(parentAttributeName).set("baz");
        OperationContext context = mock(OperationContext.class);
        Resource resource = mock(Resource.class);

        doReturn(address).when(context).getCurrentAddress();
        doAnswer(invocation -> invocation.getArgument(0)).when(context).resolveExpressions(any());
        doReturn(model).when(resource).getModel();

        Assert.assertArrayEquals(new String[] { "component", parentAttributeName, attributeName }, recorder.getRequirementPatternSegments(attributeName, address));

        ArgumentCaptor<String> capturedRequirement = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedDependent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> capturedAttribute = ArgumentCaptor.forClass(String.class);

        doNothing().when(context).registerAdditionalCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.addCapabilityRequirements(context, resource, attributeName, "bar");

        List<String> requirements = capturedRequirement.getAllValues();
        List<String> dependents = capturedDependent.getAllValues();
        List<String> attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(1, requirements.size());
        Assert.assertEquals(1, dependents.size());
        Assert.assertEquals(1, attributes.size());

        Assert.assertEquals("requirement.foo.baz.bar", requirements.get(0));
        Assert.assertSame(capability.getName(), dependents.get(0));
        Assert.assertSame(attributeName, attributes.get(0));

        doNothing().when(context).deregisterCapabilityRequirement(capturedRequirement.capture(), capturedDependent.capture(), capturedAttribute.capture());

        recorder.removeCapabilityRequirements(context, resource, attributeName, "bar");

        requirements = capturedRequirement.getAllValues();
        dependents = capturedDependent.getAllValues();
        attributes = capturedAttribute.getAllValues();

        Assert.assertEquals(2, requirements.size());
        Assert.assertEquals(2, dependents.size());
        Assert.assertEquals(2, attributes.size());

        Assert.assertEquals("requirement.foo.baz.bar", requirements.get(1));
        Assert.assertSame(capability.getName(), dependents.get(1));
        Assert.assertSame(attributeName, attributes.get(1));
    }
}
