/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.mockito.Mockito;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.QuaternaryServiceDescriptor;
import org.wildfly.service.descriptor.TernaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * Unit test for {@link CapabilityReferenceAttributeDefinition}.
 */
public class CapabilityReferenceAttributeDefinitionTestCase {

    @Test
    public void testUnary() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        UnaryServiceDescriptor<Object> unaryRequirement = UnaryServiceDescriptor.of("unary", Object.class);
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, unaryRequirement).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();

        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        ModelNode value = new ModelNode("foo");
        model.get(attribute.getName()).set(value);

        Mockito.doReturn(value).when(context).resolveExpressions(value);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("unary", Object.class, "foo");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testUnaryWithDefault() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        NullaryServiceDescriptor<Object> nullaryRequirement = NullaryServiceDescriptor.of("nullary", Object.class);
        UnaryServiceDescriptor<Object> unaryRequirement = UnaryServiceDescriptor.of("unary", nullaryRequirement);
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, unaryRequirement).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();

        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("nullary", Object.class);
        Mockito.verifyNoMoreInteractions(builder);

        ModelNode value = new ModelNode("foo");
        model.get(attribute.getName()).set(value);

        Mockito.doReturn(value).when(context).resolveExpressions(value);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("unary", Object.class, "foo");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testBinary() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        UnaryServiceDescriptor<Object> unaryRequirement = UnaryServiceDescriptor.of("unary", Object.class);
        BinaryServiceDescriptor<Object> binaryRequirement = BinaryServiceDescriptor.of("binary", Object.class);
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, unaryRequirement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, binaryRequirement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode foo = new ModelNode("foo");
        ModelNode bar = new ModelNode("bar");
        model.get(parentAttribute.getName()).set(foo);

        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(foo).when(context).resolveExpressions(foo);
        Mockito.doReturn(bar).when(context).resolveExpressions(bar);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        model.get(attribute.getName()).set(bar);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("binary", Object.class, "foo", "bar");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testBinaryWithDefault() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        UnaryServiceDescriptor<Object> unaryRequirement = UnaryServiceDescriptor.of("unary", Object.class);
        BinaryServiceDescriptor<Object> binaryRequirement = BinaryServiceDescriptor.of("binary", unaryRequirement);
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, unaryRequirement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, binaryRequirement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode foo = new ModelNode("foo");
        ModelNode bar = new ModelNode("bar");
        model.get(parentAttribute.getName()).set(foo);

        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(foo).when(context).resolveExpressions(foo);
        Mockito.doReturn(bar).when(context).resolveExpressions(bar);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("unary", Object.class, "foo");
        Mockito.verifyNoMoreInteractions(builder);

        model.get(attribute.getName()).set(bar);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("binary", Object.class, "foo", "bar");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testTernary() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        BinaryServiceDescriptor<Object> binaryRequirement = BinaryServiceDescriptor.of("binary", Object.class);
        TernaryServiceDescriptor<Object> ternaryRequirement = TernaryServiceDescriptor.of("ternary", Object.class);
        Function<PathAddress, PathElement> lastElement = PathAddress::getLastElement;
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, binaryRequirement).withParentPath(PathElement.pathElement("component"), lastElement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, ternaryRequirement).withGrandparentPath(PathElement.pathElement("component"), lastElement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode bar = new ModelNode("bar");
        ModelNode baz = new ModelNode("baz");

        Mockito.doReturn(PathAddress.pathAddress(List.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "test"), PathElement.pathElement("component", "foo")))).when(context).getCurrentAddress();
        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(bar).when(context).resolveExpressions(bar);
        Mockito.doReturn(baz).when(context).resolveExpressions(baz);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        model.get(parentAttribute.getName()).set(bar);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        model.get(attribute.getName()).set(baz);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("ternary", Object.class, "foo", "bar", "baz");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testTernaryWithDefault() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        UnaryServiceDescriptor<Object> unaryRequirement = UnaryServiceDescriptor.of("unary", Object.class);
        BinaryServiceDescriptor<Object> binaryRequirement = BinaryServiceDescriptor.of("binary", unaryRequirement);
        TernaryServiceDescriptor<Object> ternaryRequirement = TernaryServiceDescriptor.of("ternary", binaryRequirement);
        Function<PathAddress, PathElement> lastElement = PathAddress::getLastElement;
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, binaryRequirement).withParentPath(PathElement.pathElement("component"), lastElement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, ternaryRequirement).withGrandparentPath(PathElement.pathElement("component"), lastElement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode bar = new ModelNode("bar");
        ModelNode baz = new ModelNode("baz");

        Mockito.doReturn(PathAddress.pathAddress(List.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "test"), PathElement.pathElement("component", "foo")))).when(context).getCurrentAddress();
        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(bar).when(context).resolveExpressions(bar);
        Mockito.doReturn(baz).when(context).resolveExpressions(baz);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("unary", Object.class, "foo");
        Mockito.verifyNoMoreInteractions(builder);

        model.get(parentAttribute.getName()).set(bar);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("binary", Object.class, "foo", "bar");
        Mockito.verifyNoMoreInteractions(builder);

        model.get(attribute.getName()).set(baz);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("ternary", Object.class, "foo", "bar", "baz");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testQuaternary() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        TernaryServiceDescriptor<Object> ternaryRequirement = TernaryServiceDescriptor.of("ternary", Object.class);
        QuaternaryServiceDescriptor<Object> quaternaryRequirement = QuaternaryServiceDescriptor.of("quaternary", Object.class);
        UnaryOperator<PathAddress> parent = PathAddress::getParent;
        Function<PathAddress, PathElement> lastElement = PathAddress::getLastElement;
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, ternaryRequirement).withGrandparentPath(PathElement.pathElement("component"), parent.andThen(lastElement)).withParentPath(PathElement.pathElement("child"), PathAddress::getLastElement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, quaternaryRequirement).withGreatGrandparentPath(PathElement.pathElement("component"), parent.andThen(lastElement)).withGrandparentPath(PathElement.pathElement("child"), PathAddress::getLastElement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode baz = new ModelNode("baz");
        ModelNode qux = new ModelNode("qux");

        Mockito.doReturn(PathAddress.pathAddress(List.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "test"), PathElement.pathElement("component", "foo"), PathElement.pathElement("child", "bar")))).when(context).getCurrentAddress();
        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(baz).when(context).resolveExpressions(baz);
        Mockito.doReturn(qux).when(context).resolveExpressions(qux);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        model.get(parentAttribute.getName()).set(baz);

        attribute.resolve(context, model).accept(builder);

        Mockito.verifyNoInteractions(builder);

        model.get(attribute.getName()).set(qux);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("quaternary", Object.class, "foo", "bar", "baz", "qux");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    public void testQuaternaryWithDefault() throws OperationFailedException {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of("capability").build();
        BinaryServiceDescriptor<Object> binaryRequirement = BinaryServiceDescriptor.of("binary", Object.class);
        TernaryServiceDescriptor<Object> ternaryRequirement = TernaryServiceDescriptor.of("ternary", binaryRequirement);
        QuaternaryServiceDescriptor<Object> quaternaryRequirement = QuaternaryServiceDescriptor.of("quaternary", ternaryRequirement);
        UnaryOperator<PathAddress> parent = PathAddress::getParent;
        Function<PathAddress, PathElement> lastElement = PathAddress::getLastElement;
        CapabilityReferenceAttributeDefinition<Object> parentAttribute = new CapabilityReferenceAttributeDefinition.Builder<>("parent-attribute", CapabilityReference.builder(capability, ternaryRequirement).withGrandparentPath(PathElement.pathElement("component"), parent.andThen(lastElement)).withParentPath(PathElement.pathElement("child"), PathAddress::getLastElement).build()).setRequired(false).build();
        CapabilityReferenceAttributeDefinition<Object> attribute = new CapabilityReferenceAttributeDefinition.Builder<>("attribute", CapabilityReference.builder(capability, quaternaryRequirement).withGreatGrandparentPath(PathElement.pathElement("component"), parent.andThen(lastElement)).withGrandparentPath(PathElement.pathElement("child"), PathAddress::getLastElement).withParentAttribute(parentAttribute).build()).setRequired(false).build();

        OperationContext context = Mockito.mock(OperationContext.class);
        Resource resource = Mockito.mock(Resource.class);
        RequirementServiceBuilder<?> builder = Mockito.mock(RequirementServiceBuilder.class);
        ModelNode model = new ModelNode();
        ModelNode undefined = new ModelNode();
        ModelNode baz = new ModelNode("baz");
        ModelNode qux = new ModelNode("qux");

        Mockito.doReturn(PathAddress.pathAddress(List.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "test"), PathElement.pathElement("component", "foo"), PathElement.pathElement("child", "bar")))).when(context).getCurrentAddress();
        Mockito.doReturn(resource).when(context).readResource(PathAddress.EMPTY_ADDRESS, false);
        Mockito.doReturn(model).when(resource).getModel();
        Mockito.doReturn(baz).when(context).resolveExpressions(baz);
        Mockito.doReturn(qux).when(context).resolveExpressions(qux);
        Mockito.doReturn(undefined).when(context).resolveExpressions(undefined);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("binary", Object.class, "foo", "bar");
        Mockito.verifyNoMoreInteractions(builder);

        model.get(parentAttribute.getName()).set(baz);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("ternary", Object.class, "foo", "bar", "baz");
        Mockito.verifyNoMoreInteractions(builder);

        model.get(attribute.getName()).set(qux);

        attribute.resolve(context, model).accept(builder);

        Mockito.verify(builder).requiresCapability("quaternary", Object.class, "foo", "bar", "baz", "qux");
        Mockito.verifyNoMoreInteractions(builder);
    }
}
