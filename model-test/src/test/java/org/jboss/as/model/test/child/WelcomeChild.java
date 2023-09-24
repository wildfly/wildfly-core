/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.model.test.child;

import org.jboss.as.model.test.api.Welcome;

/**
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 */
public class WelcomeChild implements Welcome {

  public String hello(String name) {
    return "The child is saying hello " + name;
  }

}