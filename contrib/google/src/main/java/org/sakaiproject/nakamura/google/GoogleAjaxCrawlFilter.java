package org.sakaiproject.nakamura.google;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

@Service(value = Filter.class)
@Component(immediate = true, metatype = false)
@Properties(value = {
    @Property(name = "service.description", value = "Runs the requested page trough a headless browser so we can display proper content to the Google Crawler."),
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "filter.scope", value = "request", propertyPrivate = true),
    @Property(name = "filter.order", intValue = { 1000 }, propertyPrivate = true) })
public class GoogleAjaxCrawlFilter implements Filter {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(GoogleAjaxCrawlFilter.class);

  @Property(description = "The path to the binary where phantomjs lives.", value = "/opt/sakai/oae/phantomjs/bin/phantomjs")
  public static final String PHANTOMJS_PATH = "sakai.googlecrawlfilter.phantomJSPath";
  private String phantomJSPath;
  private String tempPath;

  /**
   * Will run the requested page trough a headless browser if the page is requested by
   * Google.
   * 
   * @see https://developers.google.com/webmasters/ajax-crawling/docs/getting-started
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Check if we really need to do some work.
    if (request.getParameter("_escaped_fragment_") == null) {
      chain.doFilter(request, response);
      return;
    }

    try {
      runInHeadlessBrowser(request, response);
    } catch (CrawlerException e) {
      // If we can't provide an AJAX-free page to the googlebot we dump the requested one.
      LOGGER.error(e.getMessage(), e);
      chain.doFilter(request, response);
    }
  }

  /**
   * Loads the requested page in a headless browser, runs all the javascript present on
   * the page and returns the updated DOM.
   * 
   * @param request
   * @param response
   * @throws CrawlerException
   *           If something went wrong.
   */
  private void runInHeadlessBrowser(ServletRequest request, ServletResponse response)
      throws CrawlerException {
    String url = getUrl(request);
    String cmd = getCommand(url);

    InputStream in = null;
    try {
      // Run the request trough a headless browser.
      Process p = Runtime.getRuntime().exec(cmd);

      // Although it might seem strange to read the output before we wait
      // it's required as doing the other way around may end up in a deadlock.
      // That does mean we can't just write directly to the response outputstream.
      in = p.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      String line = reader.readLine();
      StringBuilder content = new StringBuilder();
      while (line != null) {
        content.append(line);
        line = reader.readLine();
      }

      int status = p.waitFor();
      if (status == 0) {
        // Success, Send it too the Googlebot.
        response.getWriter().write(content.toString());
      } else {
        throw new CrawlerException("Couldn't run phantomjs.");
      }
    } catch (IOException e) {
      throw new CrawlerException("Something went wrong when trying to run phantomjs.", e);
    } catch (InterruptedException e) {
      throw new CrawlerException("Something went wrong when trying to run phantomjs.", e);
    }
  }

  /**
   * Returns the actual URL that the GoogleBot requested.
   * 
   * @param request
   * @return
   */
  protected String getUrl(ServletRequest request) {
    String fragment = request.getParameter("_escaped_fragment_");
    StringBuilder sb = new StringBuilder("http://");
    sb.append(request.getServerName()).append(":").append(request.getServerPort())
        .append("/");
    if (fragment != null && fragment != "") {
      sb.append("#!").append(fragment);
    }
    String url = sb.toString();
    LOGGER.info(url);
    return url;
  }

  /**
   * @param url
   *          The URL of the page that should be fetched.
   * @return The commando that needs to be executed.
   */
  protected String getCommand(String url) {
    return phantomJSPath + " " + tempPath + " " + url;
  }

  /**
   * 
   * {@inheritDoc}
   * 
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(FilterConfig config) throws ServletException {
  }

  /**
   * 
   * {@inheritDoc}
   * 
   * @see javax.servlet.Filter#destroy()
   */
  public void destroy() {
  }

  @Activate
  public void activate(Map<String, Object> properties) {
    modify(properties);

    // Create a temp path where the content.js file can be copied to.
    InputStream in = null;
    OutputStream os = null;
    try {
      File f = File.createTempFile("content", "js");
      in = getClass().getResourceAsStream("/content.js");

      os = new FileOutputStream(f);
      IOUtils.stream(in, os);

      tempPath = f.getAbsolutePath();

    } catch (IOException e) {
      LOGGER.error("Couldn't copy content.js!", e);

      throw new RuntimeException("Can't copy the content.js file. Not activating.");

    } finally {
      try {
        in.close();
      } catch (IOException ex) {
        LOGGER.error("Couldn't close inputstream", ex);
      }
      try {
        os.close();
      } catch (IOException ex) {
        LOGGER.error("Couldn't close inputstream", ex);
      }
    }
  }

  @Modified
  public void modify(Map<String, Object> properties) {
    phantomJSPath = properties.get(PHANTOMJS_PATH).toString();

    // Make sure that it exists!
    File f = new File(phantomJSPath);
    if (!f.exists()) {
      throw new RuntimeException(
          "Couldn't find the phantomJS binary at the provided path: " + phantomJSPath);
    }
  }
}
