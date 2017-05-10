/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.subsystem.test.transformers.subsystem.similarity;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.registry.LegacyResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.subsystem.test.SubsystemDescriptionDump;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This code is a test stub for idea around using {@link SimilarityIndex} to automatically generate transformation rules
 * Implementation was never completed to the point of using it for subsystem transformation but still exists here if it might be needed in future.
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class ModelDescriptionTestCase {

    private static ManagementResourceRegistration registration;

    @BeforeClass
    public static void setup() {
        registration = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(RootSubsystemResource.INSTANCE);
        registration.registerSubModel(SessionDefinition.INSTANCE);

    }

    @AfterClass
    public static void tearDown() {
        registration = null;
    }

    @Test
    public void testManagementResourceSerialization() {
        ModelNode model = SubsystemDescriptionDump.readFullModelDescription(PathAddress.EMPTY_ADDRESS, registration);
        ResourceDefinition definition = new LegacyResourceDefinition(model);
        ManagementResourceRegistration loaded = ManagementResourceRegistration.Factory.forProcessType(ProcessType.EMBEDDED_SERVER).createRegistration(definition);
        validate(registration, loaded);


        List<TransformRule> rules1 = ModelMatcher.getRules(registration, loaded);
        List<TransformRule> rules2 = ModelMatcher.getRules(loaded, loaded);
        Assert.assertEquals(rules1, rules2);

    }

    private static void validate(ManagementResourceRegistration orig, ManagementResourceRegistration loaded) {
        Assert.assertEquals(orig.getChildAddresses(PathAddress.EMPTY_ADDRESS).size(), loaded.getChildAddresses(PathAddress.EMPTY_ADDRESS).size());

        Assert.assertEquals(orig.getAttributeNames(PathAddress.EMPTY_ADDRESS).size(), loaded.getAttributeNames(PathAddress.EMPTY_ADDRESS).size());
        for (String name : orig.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
            AttributeDefinition attr1 = orig.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name).getAttributeDefinition();
            AttributeDefinition attr2 = loaded.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name).getAttributeDefinition();
            Assert.assertEquals(1d, SimilarityIndex.compareAttributes(attr1, attr2), 0.0d);
        }
        for (PathElement pe : orig.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
            ManagementResourceRegistration origSub = orig.getSubModel(PathAddress.pathAddress(pe));
            ManagementResourceRegistration loadedSub = loaded.getSubModel(PathAddress.pathAddress(pe));
            validate(origSub, loadedSub);
        }
    }

    @Test
    public void testSimilarity() {
        double factor = SimilarityIndex.compareStrings("test", "testa");
        double factor2 = SimilarityIndex.compareStrings("modelController", "model-controller");

    }
}
