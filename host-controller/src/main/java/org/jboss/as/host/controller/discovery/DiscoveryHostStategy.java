/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
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
package org.jboss.as.host.controller.discovery;

import static org.jboss.as.remoting.Protocol.HTTPS_REMOTING;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jboss.as.remoting.Protocol;

/**
 * Strategy on how to sort / organize DiscoverOption for Host.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public interface DiscoveryHostStategy {

    DiscoveryHostStategy DEFAULT_STRATEGY = new DiscoveryHostStategyImpl();

    void organize(List<? extends DiscoveryOption> options);

    static class DiscoveryHostStategyImpl implements DiscoveryHostStategy {

        private static final DiscoveryOptionComparator INSTANCE = new DiscoveryOptionComparator();

        @Override
        public void organize(List<? extends DiscoveryOption> options) {
            Collections.sort(options, INSTANCE);
        }

        private static class DiscoveryOptionComparator implements Comparator<DiscoveryOption> {

            @Override
            public int compare(DiscoveryOption option, DiscoveryOption otherOption) {
                Protocol currentProtocol = Protocol.forName(option.getRemoteDomainControllerProtocol());
                Protocol otherProtocol = Protocol.forName(otherOption.getRemoteDomainControllerProtocol());
                if (otherProtocol != currentProtocol) {
                    switch (currentProtocol) {
                        case HTTPS_REMOTING:
                            return 1;
                        case REMOTE:
                            if (otherProtocol == HTTPS_REMOTING) {
                                return -1;
                            }
                            return 1;
                        case HTTP_REMOTING:
                            return -1;
                    }
                } else if (option.getRemoteDomainControllerHost().equals(otherOption.getRemoteDomainControllerHost())) {
                    return option.getRemoteDomainControllerPort() - otherOption.getRemoteDomainControllerPort();
                }
                return option.getRemoteDomainControllerHost().compareTo(otherOption.getRemoteDomainControllerHost());
            }

        }
    }
}
