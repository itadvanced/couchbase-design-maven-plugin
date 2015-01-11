package org.ita.maven.plugins;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.sun.jersey.core.util.Base64;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import javax.ws.rs.core.MediaType;

import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;

/**
 * This class provides a simple API to the Couchbase REST API, abstracting away
 * the details of the HTTP requests that must be made.
 */
public class CouchbaseRestClient {

	static final String BUCKET_NAME_PATTERN = "${bucketName}";

	private static final String AUTHORIZATION = "Authorization";

	private final String host;
	private final String username;
	private final String password;
	Client restClient = null;

	public CouchbaseRestClient(String host, String username, String password, Log mavenLog) {
		this.host = host;
		this.username = username;
		this.password = password;
		this.restClient = Client.create();
		if (mavenLog.isDebugEnabled()) {
			// the LoggingFilter writes INFO messages, but the MavenLoggerAdapter translates these to DEBUG messages
			restClient.addFilter(new LoggingFilter(new MavenLoggerAdapter(getClass().getSimpleName(), mavenLog)));
		}
	}

	/**
	 * Creates a new bucket
	 * 
	 * @param bucketName The name of the bucket to create
	 * @param documentDesignsPath Document designs path
	 */
	public void ensureDesignDocuments(final String bucketName, final String documentDesignsPath) {
		String lookupDir = documentDesignsPath.replace(BUCKET_NAME_PATTERN, bucketName);

		File[] documentDesignFiles = findDocumentDesignFiles(lookupDir);
		Map<String, String> documentDesigns = prepareDocumentDesignsFromFiles(documentDesignFiles);
		for (Entry<String, String> documentDesignEntry: documentDesigns.entrySet()) {
			sendDocumentDesign(bucketName, documentDesignEntry.getKey(), documentDesignEntry.getValue());
		}

		File[] documentDesignPaths = findDocumentDesignPaths(lookupDir);
		documentDesigns = prepareDocumentDesignsFromPaths(documentDesignPaths);
		for (Entry<String, String> documentDesignEntry: documentDesigns.entrySet()) {
			sendDocumentDesign(bucketName, documentDesignEntry.getKey(), documentDesignEntry.getValue());
		}
	}

	File[] findDocumentDesignFiles(String lookupDir) {
		File[] documentDesignFiles = new File(lookupDir).listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return !new File(dir, name).isDirectory() && name.endsWith(".ddoc");
			}
		});
		Arrays.sort(documentDesignFiles, new DevPriorityComparator());
		return documentDesignFiles;
	}

	private Map<String, String> prepareDocumentDesignsFromFiles(File[] documentDesignFiles) {
		Map<String, String> documentDesigns = new HashMap<String, String>(documentDesignFiles.length + 1, 1f);
		for (File documentDesignFile: documentDesignFiles) {
			String designDocName = documentDesignFile.getName().substring(0, documentDesignFile.getName().length() - 5);
			String documentDesign = loadFileContent(documentDesignFile);
			documentDesigns.put(designDocName, documentDesign);
		}
		return documentDesigns;
	}

	File[] findDocumentDesignPaths(String lookupDir) {
		File[] documentDesignPaths = new File(lookupDir).listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.isDirectory() && pathname.list().length > 0;
			}
		});
		Arrays.sort(documentDesignPaths, new DevPriorityComparator());
		return documentDesignPaths;
	}

	Map<String, String> prepareDocumentDesignsFromPaths(File[] documentDesignPaths) {
		Map<String, String> documentDesigns = new HashMap<String, String>(documentDesignPaths.length + 1, 1f);
		for (File documentDesignPath : documentDesignPaths) {
			String documentDesignName = documentDesignPath.getName();
			StringBuilder sb = new StringBuilder();
			sb.append("{\"views\":{");
			File[] jsFiles = documentDesignPath.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".js");
				}
			});
			if (jsFiles != null) {
				for (int i = 0; i < jsFiles.length; i++) {
					File jsFile = jsFiles[i];
					String viewName = FileUtils.basename(jsFile.getName(), ".js");

					if (i > 0) {
						sb.append(',');
					}
					sb.append('"').append(viewName).append("\":{");
					sb.append("\"map\":\"");
					sb.append(loadFileContent(jsFile).replace("\"", "\\\""));
					sb.append('"');
					File reduceFile = new File(documentDesignPath, viewName + ".reduce");
					if (reduceFile.exists()) {
						sb.append(",\"reduce\":\"");
						sb.append(loadFileContent(reduceFile));
						sb.append('"');
					}
					sb.append("}");
				}
			}
			sb.append("}}");
			documentDesigns.put(documentDesignName, sb.toString());
		}
		return documentDesigns;
	}

	private void sendDocumentDesign(String bucketName, String documentDesignName, String documentDesign) {
		String restPath = "/" + bucketName + "/_design/" + documentDesignName;
		String content = documentDesign.replace("\n", "\\n");
		ClientResponse response = createWebResource(restPath).put(ClientResponse.class, content);
		if (response.getClientResponseStatus().getFamily() != SUCCESSFUL) {
			throw new CouchbaseException("Unable to create bucket '" + bucketName + "'",
					getErrors(response.getEntity(String.class)));
		}
	}

	private Map<String, String> getErrors(String responseBody) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
		} catch (Exception ex) {
			return Collections.emptyMap();
		}
	}

	private WebResource.Builder createWebResource(String path) {
		return restClient
				.resource(host)
				.path(path)
				.header(AUTHORIZATION, "Basic " + new String(Base64.encode(username + ":" + password), Charset.forName("US-ASCII")))
				.type(MediaType.APPLICATION_JSON);
	}

	private static String loadFileContent(final File file) {
		Scanner scanner = null;
		try {
			scanner = new Scanner(file); 
			return scanner.useDelimiter("\\Z").next();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
		}
	}

	static final class DevPriorityComparator implements Comparator<File> {
		public int compare(File path1, File path2) {
			if (path1.getName().startsWith("dev_") ^ path2.getName().startsWith("dev_")) {
				return path1.getName().startsWith("dev_") ? -1 : 1;
			}
			return path1.getName().compareTo(path2.getName());
		}
	}
}
