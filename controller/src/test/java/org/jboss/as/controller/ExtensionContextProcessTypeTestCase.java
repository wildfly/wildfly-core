/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author David Bosschaert
 */
public class ExtensionContextProcessTypeTestCase {
    @Test
    public void testIsServer() {
        Assert.assertTrue(ProcessType.DOMAIN_SERVER.isServer());
        Assert.assertTrue(ProcessType.EMBEDDED_SERVER.isServer());
        Assert.assertTrue(ProcessType.STANDALONE_SERVER.isServer());
        Assert.assertFalse(ProcessType.HOST_CONTROLLER.isServer());
        Assert.assertFalse(ProcessType.EMBEDDED_HOST_CONTROLLER.isServer());
        Assert.assertTrue(ProcessType.APPLICATION_CLIENT.isServer());
        Assert.assertTrue(ProcessType.SELF_CONTAINED.isServer());
    }

    @Test
    public void testIsHostController() {
        Assert.assertTrue(ProcessType.HOST_CONTROLLER.isHostController());
        Assert.assertTrue(ProcessType.EMBEDDED_HOST_CONTROLLER.isHostController());
        Assert.assertFalse(ProcessType.DOMAIN_SERVER.isHostController());
        Assert.assertFalse(ProcessType.EMBEDDED_SERVER.isHostController());
        Assert.assertFalse(ProcessType.STANDALONE_SERVER.isHostController());
        Assert.assertFalse(ProcessType.APPLICATION_CLIENT.isHostController());
        Assert.assertFalse(ProcessType.SELF_CONTAINED.isHostController());
    }
}
