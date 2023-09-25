/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */


/**
 *  Contains interfaces to be used as test tags for additional test grouping,
 *  using org.junit.experimental.categories.Category(...).
 *  These tags do not define the main categories - those are still denoted by java packages.
 *  These groups just help to define certain tests with cross-cutting concerns like security and management.
 *  Before you define a new group, please discuss with the community.
 *
 *  This feature needs JUnit 4.7+, and Surefire/Failsafe 2.12.4+
 *
 *  Examples of categories:
    <code>
        interface AllTests;
        interface ATests extends AllTests;
        interface BTests extends AllTests;
        interface AaTests extends ATests;

        @Category(ATests.class) public void ATest();
        @Category(AaTests.class) public void AaTest();
        @Category(BTests.class) public void BTest();
    </code>

    As is apparent, interfaces inheritance can be leveraged to create a hierarchy.

    A test can belong into multiple categories:
    <code>
        @Categories({Foo.class, Bar.class})
    </code>

    The tagged tests can be run in groups using Surefire's
    <code>&lt;includedGroups></code> and <code>&lt;excludedGroups></code> options in pom.xml,
    or <code>-Dtest.group=...</code> and <code>-Dtest.exgroup=...</code> at command line.

    These parameters support expressions with operators: &amp;&amp; || !  (&amp;amp;&amp;amp; in pom.xml)
    <code>org.jboss.as.test.categories.Security &amp;&amp; !org.jboss.as.test.categories.CommonCriteria</code>

 */
package org.jboss.as.test.categories;
