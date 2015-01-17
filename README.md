# couchbase-design-maven-plugin
===============================

This maven plugin ensures synchronization of design documents from source code and **Couchbase Server**.

## How does it works

Plugin scanes for source directory (default _src/main/resources/couchbase/_) and searches subfolders (design names).
Inside this subfolders plugin lists all _\*.js_ and _\*.reduce_ files.
_\*.js_ files are treated as **MAP** function and **optional** _\*.reduce_ files as **REDUCE** function.
Plugin joins _\*.js_ and _\*.reduce_ inside subfolder and creates design document and sends it to couchbase.
All development designs are send first, followed by production ones.

## How to use it?

Configure your pom.xml file. Add plugin to your build/plugins with goal **ensure-design-documents**:

	<properties>
		<username>___couchbase-db-username___</username>
		<password>___cuuchbase-db-password___</password>
	</properties>
  
	<build>
		<plugins>
			<plugin>
				<groupId>org.ita.maven</groupId>
				<artifactId>couchbase-design-maven-plugin</artifactId>
				<version>1.0</version>
				<configuration>
					<username>${username}</username>
					<password>${password}</password>
					<bucketName>sampleBucket</bucketName>
				</configuration>
				<executions>
					<execution>
						<phase>install</phase>
						<goals>
							<goal>ensure-design-documents</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

Ensure to write correct username, password and bucketName. Also change maven phase to your requirements.

Create directory: *src/main/resources/couchbase/sampleBucket/dev_sampleDesign*

Create bucket named _sampleBucket_ using couchbase console or use [couchbase-maven-plugin](http://www.stuartgunter.org/couchbase-maven-plugin/) to create bucket using maven.

Create file: *src/main/resources/couchbase/sampleBucket/dev_sampleDesign/dev_sampleView.js* with content:
```
function (doc, meta) {
  emit(null, null);
}
```
And file: *src/main/resources/couchbase/sampleBucket/dev_sampleDesign/dev_sampleView.reduce* with content:
```
_count
```

run `mvn install` to install views on couchbase server.

## Configuration

Plugin configuration params:

* host - the Couchbase Server host to connect to (e.g. "http://localhost:8092" - default)
* username - the username used to connect to Couchbase Server
* password - the password used to connect to Couchbase Server
* bucketName - the name of the bucket to operate on
* documentDesignsPath - document designs source path (defaults to _${basedir}/src/main/resources/couchbase/${bucketName}/_)
* failOnError - indicates whether the build should fail in the event of an error (defaults to _true_)

## Advanced usage

It's possible to use prepared design documents as json file.
To use it simply create file _designName_.ddoc and put it inside src/main/resources/couchbase/${bucketName}/ dir.
Sample design file should looks like:
```
{"views":{"sampleView1":{"map":"
function (doc, meta) {
  emit(null, null);
}","reduce":"_count"},"sampleView2":{"map":"
function (doc, meta) {
  emit(meta.id, null);
}"},"sampleView3":{"map":"
function (doc, meta) {
  emit(doc.field.subfield, doc);
}"}}}
```
It is very important not to use new lines in \*.ddoc file (excluding functions).

## License
Couchbase Design Documents Maven Plugin is released under the GNU GENERAL PUBLIC LICENSE
