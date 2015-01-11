package org.ita.maven.plugins;

import java.util.HashMap;
import java.util.Map;

/**
 * This exception type contains a map of errors returned from a failed Couchbase
 * REST API request.
 */
public class CouchbaseException extends RuntimeException {

	private static final long serialVersionUID = 237823940142390823L;

	private final Map<String, String> errors;

	public CouchbaseException(String message, Map<String, String> errors) {
		super(message);
		this.errors = new HashMap<String, String>(errors);
	}

	public Map<String, String> getErrors() {
		return errors;
	}
}
