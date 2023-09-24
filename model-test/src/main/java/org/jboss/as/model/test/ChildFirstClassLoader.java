/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.modules.filter.ClassFilter;

/**
 * Internal use only.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChildFirstClassLoader extends URLClassLoader {

    private final ClassLoader parent;
    private final Set<Pattern> parentFirst;
    private final Set<Pattern> childFirst;
    private final ClassFilter parentExclusionFilter;
    private final Pattern parentResourceExclusionFilter;


    ChildFirstClassLoader(ClassLoader parent, Set<Pattern> parentFirst, Set<Pattern> childFirst, ClassFilter parentExclusionFilter, Pattern parentResourceExclusionFilter, URL... urls) {
        super(urls, parent);
        assert parent != null : "Null parent";
        assert parentFirst != null : "Null parent first";
        assert childFirst != null : "Null child first";
        this.parent = parent;
        this.childFirst = Collections.unmodifiableSet(childFirst);
        this.parentFirst = Collections.unmodifiableSet(parentFirst);
        this.parentExclusionFilter = parentExclusionFilter;
        this.parentResourceExclusionFilter = parentResourceExclusionFilter;
//        System.out.println("---------->");
//        for (URL url : urls) {
//            System.out.println(url);
//        }
         registerAsParallelCapable();
    }

    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        if (loadFromParentOnly(name)) {
            return parent.loadClass(name);
        }

        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                if (parentExclusionFilter != null && parentExclusionFilter.accept(name)) {
                    throw e;
                }
            }
            if (c == null) {
                c = parent.loadClass(name);
            }
            if (c == null) {
                findClass(name);
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }


    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        return excludeResourceFromParent(name) ? null : super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (excludeResourceFromParent(name)) {
            return findResources(name);
        }
        return super.getResources(name);
    }

    private boolean excludeResourceFromParent(String name) {
        return parentResourceExclusionFilter != null && parentResourceExclusionFilter.matcher(name).matches();
    }

    private boolean loadFromParentOnly(String className) {
        boolean parent = false;
        for (Pattern pattern : parentFirst) {
            if (pattern.matcher(className).matches()) {
                parent = true;
                break;
            }
        }

        if (parent) {
            for (Pattern pattern : childFirst) {
                if (pattern.matcher(className).matches()) {
                    return false;
                }
            }
        }
        return parent;
    }
}




