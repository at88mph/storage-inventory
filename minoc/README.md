# Storage Inventory file service (minoc)

## configuration
See the [cadc-tomcat](https://github.com/opencadc/docker-base/tree/master/cadc-tomcat) image docs 
for expected deployment and general config requirements.

Runtime configuration must be made available via the `/config` directory.

### catalina.properties
When running minoc.war in tomcat, parameters of the connection pool in META-INF/context.xml need
to be configured in catalina.properties:
```
# database connection pools
org.opencadc.minoc.inventory.maxActive={max connections for inventory admin pool}
org.opencadc.minoc.inventory.username={username for inventory admin pool}
org.opencadc.minoc.inventory.password={password for inventory admin pool}
org.opencadc.minoc.inventory.url=jdbc:postgresql://{server}/{database}
```
The `inventory` account owns and manages (create, alter, drop) inventory database objects and manages
all the content (insert, update, delete). The database is specified in the JDBC URL and the schema name is specified 
in the minoc.properties (below). Failure to connect or initialize the database will show up in logs and in the 
VOSI-availability output.

### minoc.properties
A minoc.properties file in /config is required to run this service.  The following keys are required:
```
# service identity
org.opencadc.minoc.resourceID=ivo://{authority}/{name}

# storage back end
org.opencadc.inventory.storage.StorageAdapter={fully qualified classname of StorageAdapter impl}

# inventory database settings
org.opencadc.inventory.db.SQLGenerator=org.opencadc.inventory.db.SQLGenerator
org.opencadc.minoc.inventory.schema={schema name in the database configured in the JDBC URL}

org.opencadc.minoc.publicKeyFile={public key file from raven}
```
The minoc _resourceID_ is the resourceID of _this_ minoc service.

The _StorageAdapter_ is a plugin implementation to support the back end storage system. These are implemented in separate libraries;
each available implementation is in a library named _cadc-storage-adapter-{*impl*}_ and the fully qualified class name to use is 
documented there.

The _SQLGenerator_ is a plugin implementation to support the database. There is currently only one implementation that is tested 
with PostgeSQL (10+). Making this work  with other database servers in future _may_ require a different implementation.

The inventory _schema_ name is the name of the database schema used for all created database objects (tables, indices, etc). This
currently must be "inventory" due to configuration limitations in <a href="../luskan">luskan</a>.

The _publicKey_ is the the key used to decode pre-authorization information in request URLs generated by <a href="../raven">raven</a>. 
Note: it should be optional (because minoc can be used without raven), but is currently required.

The following optional keys configure minoc to use external service(s) to obtain grant information in order
to perform authorization checks:
```
# permission granting services (optional)
org.opencadc.minoc.readGrantProvider={resourceID of a permission granting service}
org.opencadc.minoc.writeGrantProvider={resourceID of a permission granting service}
```
Multiple values of the permission granting service resourceID(s) may be provided by including multiple property 
settings. All services will be consulted but a single positive result is sufficient to grant permission for an 
action.

**For developer testing only:** To disable authorization checking (via `readGrantProvider` or `writeGrantProvider`
services), add the following configuration entry to minoc.properties:
```
org.opencadc.minoc.authenticateOnly=true
```
With `authenticateOnly=true`, any authenticated user will be able to read/write/delete files and anonymous users
will be able to read files.

Additional configuration may be required by the storage adapter implementation.

### LocalAuthority.properties
The LocalAuthority.properties file specifies which local service is authoritative for various site-wide functions. The keys
are standardID values for the functions and the values are resourceID values for the service that implements that standard 
feature.

Example:
```
ivo://ivoa.net/std/GMS#search-0.1 = ivo://cadc.nrc.ca/gms           
ivo://ivoa.net/std/UMS#users-0.1 = ivo://cadc.nrc.ca/gms    
ivo://ivoa.net/std/UMS#login-0.1 = ivo://cadc.nrc.ca/gms           

ivo://ivoa.net/std/CDP#delegate-1.0 = ivo://cadc.nrc.ca/cred
ivo://ivoa.net/std/CDP#proxy-1.0 = ivo://cadc.nrc.ca/cred
```

### cadcproxy.pem
This client certificate is used to make server-to-server calls for system-level A&A purposes.

## building it
```
gradle clean build
docker build -t minoc -f Dockerfile .
```

## checking it
```
docker run -it minoc:latest /bin/bash
```

## running it
```
docker run --user tomcat:tomcat --volume=/path/to/external/config:/config:ro --name minoc minoc:latest
```

## apply semantic version tags
```bash
. VERSION && echo "tags: $TAGS" 
for t in $TAGS; do
   docker image tag minoc:latest minoc:$t
done
unset TAGS
docker image list minoc
```
