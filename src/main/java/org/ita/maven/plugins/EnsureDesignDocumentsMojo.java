package org.ita.maven.plugins;

import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Ensure document design from resources folder.
 */
@Mojo(name = "ensure-design-documents", requiresProject = false)
public class EnsureDesignDocumentsMojo extends AbstractMojo {

	/**
	 * The Couchbase Server host to connect to (e.g. "http://localhost:8092" - default).
	 */
	@Parameter(property = "couchbase.host", defaultValue = "http://localhost:8092")
	private String host;

	/**
	 * The username to connect to Couchbase Server.
	 */
	@Parameter(property = "couchbase.username", required = true)
	private String username;

	/**
	 * The password to connect to Couchbase Server.
	 */
	@Parameter(property = "couchbase.password", required = true)
	private String password;

	/**
	 * The name of the bucket to operate on.
	 */
	@Parameter(required = true)
	private String bucketName;

	/**
	 * Document designs source path.
	 */
	@Parameter(defaultValue = "${basedir}/src/main/resources/couchbase/${bucketName}/")
	private String documentDesignsPath;

	/**
	 * Indicates whether the build should fail in the event of an error.
	 */
	@Parameter(defaultValue = "true")
	private boolean failOnError;

	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			CouchbaseRestClient restClient = new CouchbaseRestClient(host, username, password, getLog());
			restClient.ensureDesignDocuments(bucketName, documentDesignsPath);
			getLog().info("Ensure document paths for bucket '" + bucketName + "'");
		} catch (CouchbaseException ex) {
			logFailure(ex);
		}
	}

	void setBucketName(String bucketName) {
		this.bucketName = bucketName;
	}

	void setDocumentDesignsPath(String documentDesignsPath) {
		this.documentDesignsPath = documentDesignsPath;
	}

	void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	private void logFailure(CouchbaseException ex) throws MojoExecutionException {
		getLog().error(ex.getMessage());			
		for (Map.Entry<String, String> error : ex.getErrors().entrySet()) {
			getLog().error("\t" + error.getKey() + ":\t" + error.getValue());
		}
		if (failOnError) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
	}
}
