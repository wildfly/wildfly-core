/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.model.test.parent;

import org.jboss.as.model.test.api.Welcome;

/**
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 */

public class WelcomeParent implements Welcome {

  public String hello(String name) {
    return "The parent is saying hello " + name;
  }

}