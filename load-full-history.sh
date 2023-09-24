#!/bin/sh
#
# Copyright The WildFly Authors
# SPDX-License-Identifier: Apache-2.0
#

#script to download the full history from the wildfly repository
#and graft it into the correct position
#most users should not need this, but it may be useful for backporting
git remote add --no-tags -f  original-wildfly-repository https://github.com/wildfly/wildfly.git
echo '835a62dfa8261022153dc0e02563f0a9980a14e0 4ecae52eefc9b17710bc961d08775da3e50c1b18'>>.git/info/grafts
