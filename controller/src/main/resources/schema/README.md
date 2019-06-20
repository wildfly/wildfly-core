# Schema for credential reference types

This directory contains the shared schema for the WildFly `credentialReferenceType`. All subsystem schemas in both
WildFly Core and WildFly that make use of credential references import and make use of this schema.

The credential reference attribute itself along with its parsing and marshalling code is defined in
`org.jboss.as.controller.security.CredentialReference`.

## Adding a new version of this schema

When adding a new version of this schema, all subsystem schemas in both WildFly Core and WildFly that import this
schema should be updated to the new version as well. Although a new version of this schema would be backwards
compatible, it is important to update the subsystem schemas that import it to the new version since it would be
confusing for users if only some subsystems are updated and others are not.
