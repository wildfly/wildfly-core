# A set of credential stores for use by the ExpressionResolutionTestCase.
#
# The stores were initialised using the following commands.

# credential-store-three.cs - No Password

java -jar wildfly-elytron-tool.jar credential-store --generate-secret-key testkey \
  --create --type PropertiesCredentialStore --location credential-store-three.cs

# credential-store-two.cs - Password = CSTwoPassword
# Set secret to CSOnePassword

java -jar wildfly-elytron-tool.jar credential-store --add csone \
  --create --location credential-store-two.cs

# credential-store-one.cs - Password = CSOnePassword
# Set secret to KSOnePassword

java -jar wildfly-elytron-tool.jar credential-store --add ksone \
  --create --location credential-store-one.cs
java -jar wildfly-elytron-tool.jar credential-store --generate-secret-key securekey \
   --location credential-store-one.cs

# Two passwords used in the configuration can be encrypted using the two generated secret keys.

# Password = CSTwoPassword, using 'testkey' in credential-store-three.cs

java -jar wildfly-elytron-tool.jar credential-store --encrypt testkey \
  --type PropertiesCredentialStore --location credential-store-three.cs

 # RUxZAUMQXUj3qP1hbXyO5PpmsbgkepjoscIf3tKXvGiPDXYqNAc=
 
# Password = KSTwoPassword, using 'securekey' in credential-store-one.cs

java -jar wildfly-elytron-tool.jar credential-store --encrypt securekey \
  --location credential-store-one.cs

 # RUxZAUMQAR0sjDUg2IrglcxXWT9MLa+HPmfopnnwkzbgsepmXd8=
 
# NOTE: We don't need commands to create the key stores used by the test as the subsystem
#       configuration dynamically creates them.
 