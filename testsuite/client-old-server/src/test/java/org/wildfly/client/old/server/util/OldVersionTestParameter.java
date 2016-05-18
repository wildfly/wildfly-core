/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
        //These two need a much higher time out (x20)
        //data.add(new OldVersionTestParameter(Version.AsVersion.AS_7_1_2_FINAL));
        //data.add(new OldVersionTestParameter(Version.AsVersion.AS_7_1_3_FINAL));

        data.add(new OldVersionTestParameter(Version.AsVersion.AS_7_2_0_FINAL));


        data.add(new OldVersionTestParameter(Version.AsVersion.WF_8_0_0_FINAL));
        data.add(new OldVersionTestParameter(Version.AsVersion.WF_8_1_0_FINAL));
        data.add(new OldVersionTestParameter(Version.AsVersion.WF_8_2_0_FINAL));
        data.add(new OldVersionTestParameter(Version.AsVersion.WF_9_0_0_FINAL));
        data.add(new OldVersionTestParameter(Version.AsVersion.WF_10_0_0_FINAL));

        data.add(new OldVersionTestParameter(Version.AsVersion.EAP_6_2_0));
        data.add(new OldVersionTestParameter(Version.AsVersion.EAP_6_3_0));
        data.add(new OldVersionTestParameter(Version.AsVersion.EAP_6_4_0));
        data.add(new OldVersionTestParameter(Version.AsVersion.EAP_7_0_0));

        return data;
    }

    @Override
    public String toString() {
        return "OldVersionTestParameter={version=" + version + "}";
    }
}