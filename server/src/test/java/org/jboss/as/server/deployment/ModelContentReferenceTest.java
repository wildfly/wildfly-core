/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.repository.ContentReference;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ModelContentReferenceTest {

    public ModelContentReferenceTest() {
    }

    /**
     * Test of fromDeploymentName method, of class ModelContentReference.
     */
    @Test
    public void testFromDeploymentName_String_String() {
        String name = "wildfly-ejb-in-war.war";
        String hash = "48d7b49e084860769d5ce03dc2223466aa46be3a";
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("deployment", "wildfly-ejb-in-war.war"));
        ContentReference result = ModelContentReference.fromDeploymentName(name, hash);
        ContentReference expResult = new ContentReference(address.toCLIStyleString(), "48d7b49e084860769d5ce03dc2223466aa46be3a");
        assertThat(result, is(expResult));
    }

    /**
     * Test of fromDeploymentName method, of class ModelContentReference.
     */
    @Test
    public void testFromDeploymentName_String_byteArr() {
        String name = "wildfly-ejb-in-war.war";
        byte[] hash = HashUtil.hexStringToByteArray("48d7b49e084860769d5ce03dc2223466aa46be3a");
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("deployment", "wildfly-ejb-in-war.war"));
        ContentReference result = ModelContentReference.fromDeploymentName(name, hash);
        ContentReference expResult = new ContentReference(address.toCLIStyleString(), "48d7b49e084860769d5ce03dc2223466aa46be3a");
        assertThat(result, is(expResult));
    }

     /**
     * Test of fromDeploymentName method, of class ModelContentReference.
     */
    @Test
    public void testFromDeploymentAddress() {
        byte[] hash = HashUtil.hexStringToByteArray("48d7b49e084860769d5ce03dc2223466aa46be3a");
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("deployment", "wildfly-ejb-in-war.war"));
        ContentReference result = ModelContentReference.fromModelAddress(address, hash);
        ContentReference expResult = new ContentReference(address.toCLIStyleString(), "48d7b49e084860769d5ce03dc2223466aa46be3a");
        assertThat(result, is(expResult));
    }

}
