package org.sakaiproject.nakamura.google;

import junit.framework.Assert;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;

public class GoogleAjaxCrawlFilterTest {

  private GoogleAjaxCrawlFilter filter = new GoogleAjaxCrawlFilter();
  private ServletRequest request;
  private static File phantomjs;

  @BeforeClass
  public static void beforeClass() throws IOException {
    // Fake a file.
    phantomjs = File.createTempFile("phantomjs", "tmp");
    phantomjs.setExecutable(true);
  }

  @AfterClass
  public static void tearDown() {
    phantomjs.delete();
  }

  @Before
  public void setUp() {
    request = Mockito.mock(ServletRequest.class);
    Mockito.when(request.getServerName()).thenReturn("localhost");
    Mockito.when(request.getServerPort()).thenReturn(8080);

    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(GoogleAjaxCrawlFilter.PHANTOMJS_PATH, phantomjs.getAbsolutePath());
    filter.activate(properties);
  }

  @Test
  public void testNonGoogleBotRequest() throws IOException, ServletException {
    // When there is no escaped fragment present, we should move on immediately.
    FilterChain chain = Mockito.mock(FilterChain.class);
    Mockito.when(request.getParameter("_escaped_fragment_")).thenReturn(null);

    filter.doFilter(request, null, chain);
    Mockito.verify(chain, Mockito.atMost(1)).doFilter(request, null);
  }

  @Test
  public void testCommand() {
    String url = "http://localhost:8080/";
    String cmd = filter.getCommand(url);
    Assert.assertTrue(cmd.startsWith(phantomjs.getAbsolutePath()));
    Assert.assertTrue(cmd.endsWith(cmd));
  }

  @Test
  public void testEmptyUrl() {
    Mockito.when(request.getParameter("_escaped_fragment_")).thenReturn("");
    String url = filter.getUrl(request);
    Assert.assertEquals("http://localhost:8080/", url);
  }

  @Test
  public void testFragmentsInUrl() {
    Mockito.when(request.getParameter("_escaped_fragment_")).thenReturn("key=value");
    String url = filter.getUrl(request);
    Assert.assertEquals("http://localhost:8080/#!key=value", url);
  }

}