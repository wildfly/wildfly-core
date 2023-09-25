/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.model.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.wildfly.common.Assert;

/**
 * An element representing a list of jvm options.
 *
 * @author <a href="mailto:kkhan@redhat.com">Kabir Khan</a>
 */
public final class JvmOptionsElement {

    private final List<String> options = new ArrayList<String>();

    /**
     * Construct a new instance.
     *
     */
    public JvmOptionsElement() {
    }

    /**
     * Adds an option to the Jvm options
     *
     * @param value the option to add
     */
    void addOption(final String value) {
        Assert.checkNotNullParam("value", value);
        synchronized (options) {
            options.add(value);
        }
    }

    public int size() {
        return options.size();
    }

    /**
     * Get a copy of the options.
     *
     * @return the copy of the options
     */
    public List<String> getOptions() {
        return new ArrayList<String>(options);
    }

    /**
     * Uses regex on each option to check if the option matches the pattern.
     *
     * @param regex the regex pattern
     *
     * @return {@code true} if one of the options matches the patter, otherwise {@code false}
     */
    public boolean contains(final String regex) {
        final Pattern pattern = Pattern.compile(regex);
        synchronized (options) {
            for (String opt : options) {
                if (pattern.matcher(opt).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

}
