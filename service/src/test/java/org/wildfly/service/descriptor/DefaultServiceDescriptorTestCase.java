/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Validates logic for defaultable service capabilities.
 * @author Paul Ferraro
 */
public class DefaultServiceDescriptorTestCase {

    private final NullaryServiceDescriptor<String> nullaryDescriptor = NullaryServiceDescriptor.of("nullary", String.class);
    private final UnaryServiceDescriptor<String> unaryDescriptor = UnaryServiceDescriptor.of("unary", this.nullaryDescriptor);
    private final BinaryServiceDescriptor<String> binaryDescriptor = BinaryServiceDescriptor.of("binary", this.unaryDescriptor);
    private final TernaryServiceDescriptor<String> ternaryDescriptor = TernaryServiceDescriptor.of("ternary", this.binaryDescriptor);
    private final QuaternaryServiceDescriptor<String> quaternaryDescriptor = QuaternaryServiceDescriptor.of("quaternary", this.ternaryDescriptor);

    @Test
    public void test() {
        String[] quaternary = new String[] { "foo", "bar", "baz", "qux" };
        String[] ternary = new String[] { "foo", "bar", "baz" };
        String[] binary = new String[] { "foo", "bar" };
        String[] unary = new String[] { "foo" };
        String[] nullary = new String[0];

        Map.Entry<String, String[]> resolved = this.nullaryDescriptor.resolve();
        Assert.assertSame(this.nullaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(nullary, resolved.getValue());

        resolved = this.unaryDescriptor.resolve("foo");
        Assert.assertSame(this.unaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(unary, resolved.getValue());

        resolved = this.binaryDescriptor.resolve("foo", "bar");
        Assert.assertSame(this.binaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(binary, resolved.getValue());

        resolved = this.ternaryDescriptor.resolve("foo", "bar", "baz");
        Assert.assertSame(this.ternaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(ternary, resolved.getValue());

        resolved = this.quaternaryDescriptor.resolve("foo", "bar", "baz", "qux");
        Assert.assertSame(this.quaternaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(quaternary, resolved.getValue());

        resolved = this.unaryDescriptor.resolve(null);
        Assert.assertSame(this.nullaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(nullary, resolved.getValue());

        resolved = this.binaryDescriptor.resolve("foo", null);
        Assert.assertSame(this.unaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(unary, resolved.getValue());

        resolved = this.ternaryDescriptor.resolve("foo", "bar", null);
        Assert.assertSame(this.binaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(binary, resolved.getValue());

        resolved = this.quaternaryDescriptor.resolve("foo", "bar", "baz", null);
        Assert.assertSame(this.ternaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(ternary, resolved.getValue());

        resolved = this.binaryDescriptor.resolve(null, null);
        Assert.assertSame(this.nullaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(nullary, resolved.getValue());

        resolved = this.ternaryDescriptor.resolve("foo", null, null);
        Assert.assertSame(this.unaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(unary, resolved.getValue());

        resolved = this.quaternaryDescriptor.resolve("foo", "bar", null, null);
        Assert.assertSame(this.binaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(binary, resolved.getValue());

        resolved = this.ternaryDescriptor.resolve(null, null, null);
        Assert.assertSame(this.nullaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(nullary, resolved.getValue());

        resolved = this.quaternaryDescriptor.resolve("foo", null, null, null);
        Assert.assertSame(this.unaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(unary, resolved.getValue());

        resolved = this.quaternaryDescriptor.resolve(null, null, null, null);
        Assert.assertSame(this.nullaryDescriptor.getName(), resolved.getKey());
        Assert.assertArrayEquals(nullary, resolved.getValue());
    }
}
