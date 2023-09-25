/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.scanner;

import java.io.File;
import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ListFileHelper extends Helper {
    private static int count = 0;

    public ListFileHelper(Rule rule) {
        super(rule);
        openTrace("ListFileHelper", "target" + File.separatorChar + " byteman.log");
        traceln("ListFileHelper", "ListFileHelper loaded.");
        System.out.println("ListFileHelper loaded.");
    }

    public boolean shouldThrowIOException(Object path) {
        System.out.println("Should we throw an IOException ?");
        traceln("ListFileHelper", "Should we throw an IOException for " + path.toString());
        return 2 == count++;
    }
}
