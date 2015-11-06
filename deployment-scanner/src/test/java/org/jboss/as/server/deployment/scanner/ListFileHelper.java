/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
