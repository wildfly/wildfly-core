WildFly Core
============
http://wildfly.org

This project provides the core runtime that is used by the Wildfly application server. This includes:

* Modular class loading
* Unified management, including domain mode
* Basic deployment architecture
* CLI for management

Building
-------------------

Ensure you have JDK 7 (or newer) and Maven 3.2.2 installed, then run:

> mvn install


Starting and Stopping WildFly 
------------------------------------------
Change to the bin directory after a successful build

> $ cd core-build/target/wildfly-core-\[version\]/bin

Start the server in domain mode

> $ ./domain.sh

Start the server in standalone mode

> $ ./standalone.sh

To stop the server, press Ctrl + C, or use the admin console

> $ ./jboss-cli.sh --connect command=:shutdown

Note that there is not very much that you can do with the core server, without first adding some extensions to it.
If you are trying to deployment Java EE application then you likely want the full Wildfly distribution, which is located
at https://github.com/wildfly/wildfly

Contributing
------------------
https://community.jboss.org/wiki/HackingOnWildFly

Using Eclipse
-------------
1. Install the latest version of eclipse
2. Make sure Xmx in eclipse.ini is at least 1280M, and it's using java 7
3. Launch eclipse and install the m2e plugin, make sure it uses your repo configs
   (get it from: http://www.eclipse.org/m2e/download/
   or install "Maven Integration for Eclipse" from the Eclipse Marketplace)
4. In eclipse preferences Java->Compiler->Errors/Warnings->Deprecated and restricted
   set forbidden reference to WARNING
5. In eclipse preferences Java->Code Style, import the cleanup, templates, and
   formatter configs in ide-configs/eclipse
6. In eclipse preferences Java->Editor->Save Actions enable "Additional Actions",
   and deselect all actions except for "Remove trailing whitespace"
7. Use import on the root pom, which will pull in all modules
8. Wait (m2e takes awhile on initial import)

License
-------
* [GNU Lesser General Public License Version 2.1](http://www.gnu.org/licenses/lgpl-2.1-standalone.html)

