/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.xml;

import java.util.List;

import org.jboss.as.controller.LegacySubsystemURN;
import org.jboss.as.controller.SubsystemURN;
import org.jboss.staxmapper.IntVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for {@link VersionedURN}.
 * @author Paul Ferraro
 */
public class VersionedURNTestCase {

    @Test
    public void test() {
        Assert.assertEquals("urn:foo:1.0", new VersionedURN<>("foo", new IntVersion(1)).getUri());
        Assert.assertEquals("urn:foo:bar:2.1", new VersionedURN<>("foo", "bar", new IntVersion(2, 1)).getUri());
        Assert.assertEquals("urn:a:b:c:1.0", new VersionedURN<>(List.of("a", "b", "c"), new IntVersion(1, 0)).getUri());

        Assert.assertEquals("urn:jboss:domain:foo:1.0", new LegacySubsystemURN<>("foo", new IntVersion(1)).getUri());
        Assert.assertEquals("urn:wildfly:foo:2.0", new SubsystemURN<>("foo", new IntVersion(2)).getUri());
    }
}
