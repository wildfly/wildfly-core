/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.service.capture;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Test;

/**
 * Unit test for {@link ReferenceValueRegistry}.
 * @author Paul Ferraro
 */
public class ReferenceValueRegistryTestCase {

    @Test
    public void test() {
        ReferenceValueRegistry<String, UUID> registry = ReferenceValueRegistry.newInstance();
        ReferenceRegistry<String, String> mappedRegistry = registry.map(UUID::toString);
        Set<String> keys = Set.of("foo", "bar", "baz", "qux");

        for (String key : keys) {
            Supplier<UUID> reference = registry.getReference(key);
            assertNotNull(reference);
            assertNull(reference.get());

            Supplier<String> mappedReference = mappedRegistry.getReference(key);
            assertNotNull(mappedReference);
            assertNull(mappedReference.get());

            // Run twice on same key
            for (int i = 0; i < 2; ++i) {
                UUID expected = UUID.randomUUID();

                Consumer<UUID> mutator = registry.add(key);
                mutator.accept(expected);

                assertSame(expected, reference.get());
                assertSame(expected, registry.getReference(key).get());

                assertEquals(expected.toString(), mappedReference.get());
                assertEquals(expected.toString(), mappedRegistry.getReference(key).get());

                // Update value
                expected = UUID.randomUUID();
                mutator.accept(expected);

                assertSame(expected, reference.get());
                assertSame(expected, registry.getReference(key).get());

                assertEquals(expected.toString(), mappedReference.get());
                assertEquals(expected.toString(), mappedRegistry.getReference(key).get());

                // Verify update via redundant consumer
                Consumer<UUID> redundantMutator = registry.add(key);
                expected = UUID.randomUUID();

                redundantMutator.accept(expected);

                assertSame(expected, reference.get());
                assertSame(expected, registry.getReference(key).get());

                assertEquals(expected.toString(), mappedReference.get());
                assertEquals(expected.toString(), mappedRegistry.getReference(key).get());

                // Verify update via original Consumer
                expected = UUID.randomUUID();
                mutator.accept(expected);

                assertSame(expected, reference.get());
                assertSame(expected, registry.getReference(key).get());

                assertEquals(expected.toString(), mappedReference.get());
                assertEquals(expected.toString(), mappedRegistry.getReference(key).get());

                registry.remove(key);

                assertNull(reference.get());
                assertNull(registry.getReference(key).get());

                assertNull(mappedReference.get());
                assertNull(mappedRegistry.getReference(key).get());

                // Verify Consumer no longer modifies reference after removal
                mutator.accept(UUID.randomUUID());

                assertNull(reference.get());
                assertNull(registry.getReference(key).get());

                assertNull(mappedReference.get());
                assertNull(mappedRegistry.getReference(key).get());

                // Verify redundant Consumer no longer modifies reference after removal
                redundantMutator.accept(UUID.randomUUID());

                assertNull(reference.get());
                assertNull(registry.getReference(key).get());

                assertNull(mappedReference.get());
                assertNull(mappedRegistry.getReference(key).get());
            }
        }
    }

    @Test
    public void andThen() {
        ReferenceValueRegistry<String, UUID> registry1 = ReferenceValueRegistry.newInstance();
        ReferenceValueRegistry<String, UUID> registry2 = ReferenceValueRegistry.newInstance();
        ValueRegistry<String, UUID> combined = registry1.andThen(registry2);

        Supplier<UUID> reference1 = registry1.getReference("foo");
        Supplier<UUID> reference2 = registry2.getReference("foo");

        assertNull(reference1.get());
        assertNull(reference2.get());

        Consumer<UUID> reference = combined.add("foo");
        UUID expected = UUID.randomUUID();
        reference.accept(expected);

        assertSame(expected, reference1.get());
        assertSame(expected, reference2.get());

        expected = UUID.randomUUID();
        reference.accept(expected);

        assertSame(expected, reference1.get());
        assertSame(expected, reference2.get());

        combined.remove("foo");

        assertNull(reference1.get());
        assertNull(reference2.get());
    }

    @Test
    public void compose() {
        ReferenceValueRegistry<String, Long> registry = ReferenceValueRegistry.newInstance();
        ValueRegistry<Integer, UUID> composed = registry.compose(String::valueOf, UUID::getMostSignificantBits);

        Supplier<Long> reference = registry.getReference("1");

        assertNull(reference.get());

        Consumer<UUID> composedReference = composed.add(1);
        UUID expected = UUID.randomUUID();
        composedReference.accept(expected);

        assertEquals(expected.getMostSignificantBits(), reference.get().longValue());

        expected = UUID.randomUUID();
        composedReference.accept(expected);

        assertEquals(expected.getMostSignificantBits(), reference.get().longValue());

        composed.remove(1);

        assertNull(reference.get());
    }

    @Test
    public void map() {
        ReferenceValueRegistry<String, UUID> registry = ReferenceValueRegistry.newInstance();

        Supplier<Long> reference = registry.map(UUID::getMostSignificantBits).getReference("foo");

        assertNull(reference.get());

        Consumer<UUID> composedReference = registry.add("foo");
        UUID expected = UUID.randomUUID();
        composedReference.accept(expected);

        assertEquals(expected.getMostSignificantBits(), reference.get().longValue());

        expected = UUID.randomUUID();
        composedReference.accept(expected);

        assertEquals(expected.getMostSignificantBits(), reference.get().longValue());

        registry.remove("foo");

        assertNull(reference.get());
    }
}
