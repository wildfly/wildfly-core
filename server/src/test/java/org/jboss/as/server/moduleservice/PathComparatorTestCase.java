/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.moduleservice;

import static org.junit.Assert.assertArrayEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Verifies the oder of path comparator used in ExternalModuleSpecService is the correct.
 *
 * @author Yeray Borges
 */
public class PathComparatorTestCase {

    @Test
    public void pathComparatorOrderCaseOne() {
        Path root = Paths.get("").toAbsolutePath().getRoot();

        List<Path> pathsUnderTests = Arrays.asList(
                root,
                root.resolve(Paths.get("B.txt")),
                root.resolve(Paths.get("A.txt")),
                root.resolve(Paths.get("AB","A.txt"))
        );

        List<Path> pathsExpectedOrder = Arrays.asList(
                root,
                root.resolve(Paths.get("A.txt")),
                root.resolve(Paths.get("B.txt")),
                root.resolve(Paths.get("AB","A.txt"))
        );

        Collections.sort(pathsUnderTests, new ExternalModuleSpecService.PathComparator());
        assertArrayEquals("Unexpected order found", pathsUnderTests.toArray(), pathsExpectedOrder.toArray());
    }

    @Test
    public void pathComparatorOrderCaseTwo() {
        Path root = Paths.get("").toAbsolutePath().getRoot();

        List<Path> pathsUnderTests = Arrays.asList(
                root.resolve(Paths.get("A", "Z.txt")),
                root.resolve(Paths.get("AB", "A.txt"))
        );

        List<Path> pathsExpectedOrder = Arrays.asList(
                root.resolve(Paths.get("A", "Z.txt")),
                root.resolve(Paths.get("AB", "A.txt"))
        );

        Collections.sort(pathsUnderTests, new ExternalModuleSpecService.PathComparator());
        assertArrayEquals("Unexpected order found", pathsUnderTests.toArray(), pathsExpectedOrder.toArray());
    }

    @Test
    public void pathComparatorOrderCaseThree() {
        Path root = Paths.get("").toAbsolutePath().getRoot();

        List<Path> pathsUnderTests = Arrays.asList(
                root.resolve(Paths.get("A","A","C", "A.txt")),
                root.resolve(Paths.get("A","AB","A", "A.txt"))
        );

        List<Path> pathsExpectedOrder = Arrays.asList(
                root.resolve(Paths.get("A","A","C", "A.txt")),
                root.resolve(Paths.get("A","AB","A", "A.txt"))
        );

        Collections.sort(pathsUnderTests, new ExternalModuleSpecService.PathComparator());
        assertArrayEquals("Unexpected order found", pathsUnderTests.toArray(), pathsExpectedOrder.toArray());
    }

    @Test
    public void pathComparatorOrderCaseFour() {
        Path root = Paths.get("").toAbsolutePath().getRoot();

        List<Path> pathsUnderTests = Arrays.asList(
                root,
                root.resolve(Paths.get("B.txt")),
                root.resolve(Paths.get("A.txt")),
                root.resolve(Paths.get("AB", "A.txt")),
                root.resolve(Paths.get("C", "E", "A.txt")),
                root.resolve(Paths.get("C", "B", "F", "A.txt")),
                root.resolve(Paths.get("A", "A", "A.txt")),
                root.resolve(Paths.get("A", "A", "C.txt")),
                root.resolve(Paths.get("A", "B", "B.txt")),
                root.resolve(Paths.get("AB", "C", "A.txt")),
                root.resolve(Paths.get("Z", "A.txt")),
                root.resolve(Paths.get("Z", "B.txt")),
                root.resolve(Paths.get("A", "A.txt")),
                root.resolve(Paths.get("A", "Z.txt"))
        );

        List<Path> pathsExpectedOrder = Arrays.asList(
                root,
                root.resolve(Paths.get("A.txt")),
                root.resolve(Paths.get("B.txt")),
                root.resolve(Paths.get("A", "A.txt")),
                root.resolve(Paths.get("A", "Z.txt")),
                root.resolve(Paths.get("A", "A", "A.txt")),
                root.resolve(Paths.get("A", "A", "C.txt")),
                root.resolve(Paths.get("A", "B", "B.txt")),
                root.resolve(Paths.get("AB", "A.txt")),
                root.resolve(Paths.get("AB", "C", "A.txt")),
                root.resolve(Paths.get("C", "B", "F", "A.txt")),
                root.resolve(Paths.get("C", "E", "A.txt")),
                root.resolve(Paths.get("Z", "A.txt")),
                root.resolve(Paths.get("Z", "B.txt"))
        );

        Collections.sort(pathsUnderTests, new ExternalModuleSpecService.PathComparator());
        assertArrayEquals("Unexpected order found", pathsUnderTests.toArray(), pathsExpectedOrder.toArray());
    }
}
