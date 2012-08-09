package org.sakaiproject.nakamura.solr;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.solr.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Dictionary;

import javax.xml.parsers.ParserConfigurationException;

@Component(immediate = true, metatype = true)
@Service(value = SolrClient.class)
public class VivoSolrClient implements SolrClient {

  @Property(value = "vivo")
  public static final String CLIENT_NAME = SolrClient.CLIENT_NAME;

  @Property(value = "http://localhost:8983/solr/VIVO")
  private static final String PROP_SOLR_URL = "remoteurl";

  @Property(intValue = 1)
  private static final String PROP_MAX_RETRIES = "max.retries";

  @Property(boolValue = true)
  private static final String PROP_ALLOW_COMPRESSION = "allow.compression";

  @Property(boolValue = false)
  private static final String PROP_FOLLOW = "follow.redirects";

  @Property(intValue = 100)
  private static final String PROP_MAX_TOTAL_CONNECTONS = "max.total.connections";

  @Property(intValue = 100)
  private static final String PROP_MAX_CONNECTONS_PER_HOST = "max.connections.per.host";

  @Property(intValue = 100)
  private static final String PROP_CONNECTION_TIMEOUT = "connection.timeout";

  @Property(intValue = 1000)
  private static final String PROP_SO_TIMEOUT = "socket.timeout";

  // The following two indexer properties are not currently used, although
  // they could be used as inputs for ConcurrentUpdateSolrServer.
  @Property(intValue = 100)
  private static final String PROP_QUEUE_SIZE = "indexer.queue.size";

  @Property(intValue = 10)
  private static final String PROP_THREAD_COUNT = "indexer.thread.count";

  private static final Logger LOGGER = LoggerFactory.getLogger(VivoSolrClient.class);

  private SolrServer createQueryServer() {
    String url = Utils.toString(properties.get(PROP_SOLR_URL),
        "http://localhost:8983/solr/VIVO");
    HttpSolrServer server = new HttpSolrServer(url);
    server.setSoTimeout(Utils.toInt(properties.get(PROP_SO_TIMEOUT), 1000)); // socket
    server
        .setConnectionTimeout(Utils.toInt(properties.get(PROP_CONNECTION_TIMEOUT), 100));
    server.setDefaultMaxConnectionsPerHost(Utils.toInt(
        properties.get(PROP_MAX_CONNECTONS_PER_HOST), 100));
    server.setMaxTotalConnections(Utils.toInt(properties.get(PROP_MAX_TOTAL_CONNECTONS),
        100));
    server.setFollowRedirects(Utils.toBoolean(properties.get(PROP_FOLLOW), false)); // defaults
    server.setAllowCompression(Utils.toBoolean(properties.get(PROP_ALLOW_COMPRESSION),
        true));
    server.setMaxRetries(Utils.toInt(properties.get(PROP_MAX_RETRIES), 1)); // defaults
    server.setParser(new BinaryResponseParser()); // binary parser is used
    // by default
    return server;
  }

  private String solrHome;

  private Dictionary<String, Object> properties;

  private boolean enabled;

  private SolrClientListener listener;

  private SolrServer queryServer;

  @SuppressWarnings("unchecked")
  @Activate
  public void activate(ComponentContext componentContext) throws IOException {
    BundleContext bundleContext = componentContext.getBundleContext();
    properties = componentContext.getProperties();
    solrHome = Utils.getSolrHome(bundleContext);
  }

  @Deactivate
  public void deactivate(ComponentContext componentContext) {
    disable();
  }

  public String getName() {
    return CLIENT_NAME;
  }

  public SolrServer getServer() {
    return queryServer;
  }

  public SolrServer getUpdateServer() {
    return queryServer;
  }

  public String getSolrHome() {
    return solrHome;
  }

  public void disable() {
    if (!enabled) {
      return;
    }
    enabled = false;
    if (listener != null) {
      listener.disabled();
    }
  }

  public void enable(SolrClientListener listener) throws IOException,
      ParserConfigurationException, SAXException {
    if (enabled) {
      return;
    }
    queryServer = createQueryServer();
    enabled = true;
    this.listener = listener;
  }

}
