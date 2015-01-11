package org.ita.maven.plugins;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.maven.plugin.logging.Log;
import org.fest.assertions.data.MapEntry;
import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

public class CouchbaseRestClientTest {

	private static final String BUCKET_NAME = "test_design";
	private static final String HOST = "localhost";
	private static final String USERNAME = "9999";
	private static final String PASSWORD = "pass";
	private static final Log MAVEN_LOG = mock(Log.class);

	private static final String TEST_PATH = CouchbaseRestClientTest.class.getClassLoader().getResource(BUCKET_NAME).getFile();
	private static final String TEST_DESIGN_DIR_NAME = "test_designDir";
	private static final File TEST_DESIGN_DIR = new File(TEST_PATH, TEST_DESIGN_DIR_NAME);

	private static final File FILE_ABC = mockFile("abc.txt");
	private static final File FILE_LKM = mockFile("lkm.txt");
	private static final File FILE_RWN = mockFile("rwn.txt");
	private static final File FILE_DEV_OPR = mockFile("dev_opr.txt");
	private static final File FILE_DEV_ERG = mockFile("dev_erg.txt");

	private CouchbaseRestClient restClient = new CouchbaseRestClient(HOST, USERNAME, PASSWORD, MAVEN_LOG);

	@Test
	public void itShouldFindTestDocumentDesignFile() {
		// when
		File[] result = restClient.findDocumentDesignFiles(TEST_PATH);

		// then
		assertThat(result).containsExactly(new File(TEST_PATH, "testDocumentDesign.ddoc"));
	}

	@Test
	public void itShouldFindTestDesignDirectory() {
		// when
		File[] result = restClient.findDocumentDesignPaths(TEST_PATH);

		// then
		assertThat(result).containsExactly(TEST_DESIGN_DIR);
	}

	@Test
	public void itShouldPrepareDocumentDesignFromPath() {
		// given
		File[] documentDesignPaths = { TEST_DESIGN_DIR }; 

		// when
		Map<String, String> result = restClient.prepareDocumentDesignsFromPaths(documentDesignPaths);

		// then
		assertThat(result).contains(MapEntry.entry(TEST_DESIGN_DIR_NAME, "{\"views\":{"
				+ "\"findByAnotherField\":{\"map\":\"function (doc, meta) {\n\temit(null, \\\"1\\\");\n}\"},"
				+ "\"findByTestField\":{\"map\":\"function (doc, meta) {\n\temit(null, null);\n}\",\"reduce\":\"_count\"}"
				+ "}}"));
	}

	@Test
	public void itShouldSendRequestTwice() {
		// given
		Builder builder = prepareRequestBuilder(Status.OK);

		// when
		restClient.ensureDesignDocuments(BUCKET_NAME, TEST_PATH.replace(BUCKET_NAME, CouchbaseRestClient.BUCKET_NAME_PATTERN));
		
		// then
		verify(builder, times(2)).put(eq(ClientResponse.class), anyString());
	}

	@Test(expected = CouchbaseException.class)
	public void itShouldStopProcessWhenCouchbaseResponseStatusIsNotCorrect() {
		prepareRequestBuilder(Status.NOT_ACCEPTABLE);
		restClient.ensureDesignDocuments(BUCKET_NAME, TEST_PATH.replace(BUCKET_NAME, CouchbaseRestClient.BUCKET_NAME_PATTERN));
	}

	@Test
	public void itShouldSortFilesByName() {
		// given
		File[] fileNames = { FILE_RWN, FILE_ABC, FILE_LKM };
		
		// when
		Arrays.sort(fileNames, new CouchbaseRestClient.DevPriorityComparator());
		
		// then
		assertThat(fileNames).containsExactly(FILE_ABC, FILE_LKM, FILE_RWN);
	}

	@Test
	public void itShouldSortTwoDevFilesByName() {
		// given
		File[] fileNames = { FILE_DEV_ERG, FILE_DEV_OPR };
		
		// when
		Arrays.sort(fileNames, new CouchbaseRestClient.DevPriorityComparator());
		
		// then
		assertThat(fileNames).containsExactly(FILE_DEV_ERG, FILE_DEV_OPR);
	}


	@Test
	public void itShouldMoveDevFilesOnBegining() {
		// given
		File[] fileNames = { FILE_ABC, FILE_DEV_OPR, FILE_DEV_ERG };
		
		// when
		Arrays.sort(fileNames, new CouchbaseRestClient.DevPriorityComparator());
		
		// then
		assertThat(fileNames).containsExactly(FILE_DEV_ERG, FILE_DEV_OPR, FILE_ABC);
	}

	private Builder prepareRequestBuilder(Status status) {
		ClientResponse response = mock(ClientResponse.class);
		when(response.getClientResponseStatus()).thenReturn(status);
		Builder builder = mock(Builder.class);
		when(builder.type(MediaType.APPLICATION_JSON)).thenReturn(builder);
		when(builder.put(eq(ClientResponse.class), anyString())).thenReturn(response);
		WebResource webResource = mock(WebResource.class);
		when(webResource.path("/" + BUCKET_NAME + "/_design/testDocumentDesign")).thenReturn(webResource);
		when(webResource.path("/" + BUCKET_NAME + "/_design/test_designDir")).thenReturn(webResource);
		when(webResource.header(anyString(), anyString())).thenReturn(builder);
		restClient.restClient = mock(Client.class);
		when(restClient.restClient.resource(HOST)).thenReturn(webResource);
		return builder;
	}


	private static File mockFile(String fileName) {
		File fileMock = mock(File.class);
		when(fileMock.getName()).thenReturn(fileName);
		return fileMock;
	}
}
