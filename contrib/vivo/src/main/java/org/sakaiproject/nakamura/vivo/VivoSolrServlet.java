package org.sakaiproject.nakamura.vivo;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.solr.VivoSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;

@SlingServlet(paths = { "/system/vivo/solr" }, methods = { "GET" })
public class VivoSolrServlet extends SlingSafeMethodsServlet {

  @Reference
  protected SolrServerService solrService;

  private static final Logger LOGGER = LoggerFactory.getLogger(VivoSolrServlet.class);

  /**
   * 
   */
  private static final long serialVersionUID = -2989375672711814204L;

  @SuppressWarnings("unchecked")
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {

    // Get the VIVO solr server.
    SolrServer server = solrService.getServerByName(VivoSolrClient.CLIENT_NAME);

    // Query for all the Person objects in solr.
    String queryStr = "type:\"http://xmlns.com/foaf/0.1/Person\"";
    SolrQuery query = new SolrQuery(queryStr);
    query.setStart(0).setRows(30000).setFields("URI", "classLocalName", "nameRaw");
    QueryResponse solrResponse = null;
    try {
      solrResponse = server.query(query);
    } catch (Throwable t) {
      LOGGER.error("Couldn't query the Solr server.", t);
    }

    // Handle the response
    if (response == null) {
      throw new ServletException("Could not run search");
    }
    SolrDocumentList docs = solrResponse.getResults();

    // Run over the results.
    if (docs == null) {
      throw new ServletException("Could not run search");
    }

    try {
      JSONArray arr = new JSONArray();
      for (SolrDocument doc : docs) {
        String uri = doc.get("URI").toString();
        ArrayList<String> names = (ArrayList<String>) doc.get("nameRaw");
        ArrayList<String> isa = (ArrayList<String>) doc.get("classLocalName");

        JSONObject o = new JSONObject();
        o.put("uri", uri);
        o.put("names", new JSONArray(names));
        o.put("isa", new JSONArray(isa));
        arr.put(o);
      }

      arr.write(response.getWriter());
    } catch (JSONException e) {
      throw new ServletException(e);
    }
  }

}
