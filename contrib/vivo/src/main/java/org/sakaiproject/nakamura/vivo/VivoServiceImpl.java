package org.sakaiproject.nakamura.vivo;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.vivo.VivoService;
import org.sakaiproject.nakamura.solr.VivoSolrClient;
import org.sakaiproject.nakamura.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@Component(immediate = true, enabled = true, metatype = true)
@Properties(value = { @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Queries the VIVO Solr endpoint.") })
public class VivoServiceImpl implements VivoService {

  @Reference
  protected SolrServerService solrService;

  @Property(cardinality = Integer.MIN_VALUE, description = "Allows for further refinements by providing the key in the type request parameter. Each line should follow the format: type=URI", value = {
      "person=http://vivoweb.org/ontology#vitroClassGrouppeople",
      "research=http://vivoweb.org/ontology#vitroClassGrouppublications" })
  static final String VIVO_URIS = "sakai.vivo.uris";
  private Map<String, String> typeToURI;

  @SuppressWarnings("unchecked")
  public JSONArray search(String queryStr, String type) throws VivoException {
    // Get the VIVO solr server.
    SolrServer server = solrService.getServerByName(VivoSolrClient.CLIENT_NAME);

    // Query with a facetted count.
    SolrQuery query = new SolrQuery(queryStr);
    String classGroup = typeToURI.get(type);
    if (classGroup != null) {
      query.addFilterQuery("classgroup:\"" + classGroup + "\"");
    }
    query.add("facet", "true");
    query.add("facet.limit", "-1");
    query.add("facet.field", "type");
    query.setStart(0).setRows(30000).setFields("URI", "classLocalName", "nameRaw");

    // Execute it.
    QueryResponse solrResponse = null;
    try {
      solrResponse = server.query(query);
      if (solrResponse == null) {
        throw new VivoException("Could not run search.");
      }
    } catch (SolrServerException t) {
      throw new VivoException("Couldn't query the Solr server.", t);
    }

    // Run over the results.
    SolrDocumentList docs = solrResponse.getResults();
    if (docs == null) {
      throw new VivoException("Could not run search.");
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

      return arr;
    } catch (JSONException e) {
      throw new VivoException("Couldn't generate JSON", e);
    }
  }

  @Activate
  @Modified
  protected void activate(Map<String, Object> properties) {
    typeToURI = new HashMap<String, String>();

    String[] uris = (String[]) properties.get(VIVO_URIS);
    for (String uri : uris) {
      if (uri.contains("=")) {
        String[] parts = StringUtils.split(uri, '=');
        typeToURI.put(parts[0], parts[1]);
      }
    }
  }

}
