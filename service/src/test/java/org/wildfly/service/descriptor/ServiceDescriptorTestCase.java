/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service.descriptor;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for service descriptor resolution.
 * @author Paul Ferraro
 */
public class ServiceDescriptorTestCase {

    private final NullaryServiceDescriptor<String> nullaryDescriptor = NullaryServiceDescriptor.of("nullary", String.class);
    private final UnaryServiceDescriptor<String> unaryDescriptor = UnaryServiceDescriptor.of("unary", String.class);
    private final BinaryServiceDescriptor<String> binaryDescriptor = BinaryServiceDescriptor.of("binary", String.class);
    private final TernaryServiceDescriptor<String> ternaryDescriptor = TernaryServiceDescriptor.of("ternary", String.class);
    private final QuaternaryServiceDescriptor<String> quaternaryDescriptor = QuaternaryServiceDescriptor.of("quaternary", String.class);

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

        Assert.assertNull(this.unaryDescriptor.resolve(null));
        Assert.assertNull(this.binaryDescriptor.resolve("foo", null));
        Assert.assertNull(this.binaryDescriptor.resolve(null, "bar"));
        Assert.assertNull(this.ternaryDescriptor.resolve("foo", "bar", null));
        Assert.assertNull(this.ternaryDescriptor.resolve("foo", null, "baz"));
        Assert.assertNull(this.ternaryDescriptor.resolve(null, "bar", "baz"));
        Assert.assertNull(this.quaternaryDescriptor.resolve("foo", "bar", "baz", null));
        Assert.assertNull(this.quaternaryDescriptor.resolve("foo", "bar", null, "qux"));
        Assert.assertNull(this.quaternaryDescriptor.resolve("foo", null, "baz", "qux"));
        Assert.assertNull(this.quaternaryDescriptor.resolve(null, "bar", "baz", "qux"));
    }
}
