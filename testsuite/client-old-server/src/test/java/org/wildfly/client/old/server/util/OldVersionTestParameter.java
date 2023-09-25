/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.client.old.server.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kabir Khan
 */
public class OldVersionTestParameter {
    private final Version.AsVersion version;

    public OldVersionTestParameter(Version.AsVersion version) {
        this.version = version;
    }

    public Version.AsVersion getAsVersion() {
        return version;
    }

    public static List<OldVersionTestParameter> setupVersions() {
        List<OldVersionTestParameter> data = new ArrayList<>();

        data.add(new OldVersionTestParameter(Version.AsVersion.EAP_7_4_0));

        return data;
    }

    @Override
    public String toString() {
        return "OldVersionTestParameter={version=" + version + "}";
    }
}