package org.sakaiproject.nakamura.vivo;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.vivo.VivoService;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(paths = { "/system/vivo/solr" }, methods = { "GET" })
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Queries the VIVO Solr endpoint.") })
public class VivoSolrServlet extends SlingSafeMethodsServlet {

  private static final long serialVersionUID = -2989375672711814204L;

  @Reference
  protected VivoService vivoService;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {

    try {
      String queryStr = request.getParameter("q");
      if (queryStr == null || queryStr.equals("")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No search term was provided.");
        return;
      }

      String type = request.getParameter("type");
      JSONArray arr = vivoService.search(queryStr, type);
      arr.write(response.getWriter());
    } catch (VivoException e) {
      throw new ServletException(e);
    } catch (JSONException e) {
      throw new ServletException(e);
    }
  }

}
