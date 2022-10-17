# Storage Inventory file-validate process (tantar)

Process to ensure validity of the information stored in the inventory database and back end storage at a storage site is
correct. This process is intended to be run periodically at a storage site to keep the site in a valid state.

## configuration
See the [cadc-java](https://github.com/opencadc/docker-base/tree/master/cadc-java) image docs for general config requirements.

Runtime configuration must be made available via the `/config` directory.
<!--  -->
### tantar.properties
```
org.opencadc.tantar.logging = {info|debug}

# set whether to report all activity or to perform any actions required.
org.opencadc.tantar.reportOnly = {true|false}

# set the bucket prefix(es) that tantar will validate
org.opencadc.tantar.buckets = {bucket prefix or range of bucket prefixes}

# set the policy to resolve conflicts of files
org.opencadc.tantar.policy.ResolutionPolicy = {resolution policy}

## inventory database settings
org.opencadc.inventory.db.SQLGenerator=org.opencadc.inventory.db.SQLGenerator
org.opencadc.tantar.inventory.schema={schema for inventory database objects}
org.opencadc.tantar.inventory.username={username for inventory admin pool}
org.opencadc.tantar.inventory.password={password for inventory admin pool}
org.opencadc.tantar.inventory.url=jdbc:postgresql://{server}/{database}

## storage adapter settings
org.opencadc.inventory.storage.StorageAdapter={fully-qualified-classname of implementation}
```
The `inventory` database account owns and manages (create, alter, drop) inventory database objects and modifies the content. 
The database is specified in the JDBC URL. Failure to connect or initialize the database will show up in logs and cause
the application to exit.

The _buckets_ value indicates a subset of inventory database and back end storage to validate. This uses the 
Artifact.storageLocation.storageBucket values so the exact usage and behaviour depends on the StorageAdapter being used for the
site. There are two kinds of buckets in use: some StorageAdapter(s) use a short string of hex characters as the bucket and one can
prefix it to denote fewer but larger buckets (e.g. bucket prefix "0" denotes ~1/16 of the storage locations; the prefix range "0-f"
denotes the entire storage range). 

The _StorageAdapter_ is a plugin implementation to support the back end storage system. These are implemented in separate libraries; 
each available implementation is in a library named cadc-storage-adapter-{impl} and the fully qualified class name to use is documented 
there.

The _SQLGenerator_ is a plugin implementation to support the database. There is currently only one implementation that is tested with 
PostgeSQL (10+). Making this work with other database servers in future may require a different implementation.

The inventory _schema name_ is the name of the database schema used for all created database objects (tables, indices, etc). This 
currently must be "inventory" due to configuration limitations in luskan.

The _ResolutionPolicy_ is a plugin implementation that controls how discrepancies between the inventory database and the back end storage 
are resolved. The policy specified that one is the definitive source of information about the existence of an Artifact or file and fixes 
the discrepancy accordingly. Since these policies are all implemented within `tantar`, policies can be identified by the simple class name
(use of fully qualified class name is deprecated but still works). 

The standard policy one would normally use is _InventoryIsAlwaysRight_: an Artifact in the database indicates the correct state and a 
file without an Artifact should be deleted.

The _StorageIsAlwaysRight_ policy is used for a site where files are added to back end storage and "ingested" into inventory (a read-only 
storage site); this is suitable when using a StorageAdapter to migrate content from an old system. This policy will never delete stored files 
but it will delete Artifact(s) from the inventory database that do not match a stored file (and generate a DeletedArtifactEvent that will 
propagate to other sites), create new Artifact(s) for files that do not match an existing one, and may modify Artifact metadata in the 
inventory database to match values from storage. This policy makes the back end storage of this site the definitive source for the existence 
of artifacts/files.

The _RecoverFromStorage_ policy is currently in development and not yet usable; it will be useful to recover from losing the entire 
inventory database as well from lesser disasters if the StorageAdapter supports it. Additional config may be needed when this is ready.

The following StorageAdapter and ResolutionPolicy combinations are considered well tested:
```
OpaqueFilesystemStorageAdapter + InventoryIsAlwaysRight
SwiftStorageAdapter + InventoryIsAlwaysRight
AdStorageAdapter + StorageIsAlwaysRight (CADC archive migration to SI)
```

Additional java system properties and/or configuration files may be required to configure the storage adapter.

### cadcproxy.pem
This client certificate may be used by the StorageAdapter implementation.

## building it
```
gradle clean build
docker build -t tantar -f Dockerfile .
```

## checking it
```
docker run -t tantar:latest /bin/bash
```

## running it
```
docker run -r --user opencadc:opencadc -v /path/to/external/config:/config:ro --name tantar tantar:latest
```

## apply version tags
```bash
. VERSION && echo "tags: $TAGS" 
for t in $TAGS; do
   docker image tag tantar:latest tantar:$t
done
unset TAGS
docker image list tantar
```
