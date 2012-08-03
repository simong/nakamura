/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.user.search;

import static org.sakaiproject.nakamura.api.lite.StoreListener.ADDED_TOPIC;
import static org.sakaiproject.nakamura.api.lite.StoreListener.DELETE_TOPIC;
import static org.sakaiproject.nakamura.api.lite.StoreListener.TOPIC_BASE;
import static org.sakaiproject.nakamura.api.lite.StoreListener.UPDATED_TOPIC;
import static org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions.CAN_READ;
import static org.sakaiproject.nakamura.api.lite.accesscontrol.Security.ZONE_AUTHORIZABLES;
import static org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.LASTMODIFIED_FIELD;
import static org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.NAME_FIELD;
import static org.sakaiproject.nakamura.api.user.UserConstants.COUNTS_LAST_UPDATE_PROP;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_DESCRIPTION_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_TITLE_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_GROUP_MANAGERS;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_MANAGED_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.SAKAI_CATEGORY;
import static org.sakaiproject.nakamura.api.user.UserConstants.SAKAI_EXCLUDE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_EMAIL_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_FIRSTNAME_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_LASTNAME_PROPERTY;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.Set;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.TopicIndexer;
import org.sakaiproject.nakamura.api.user.AuthorizableUtil;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.indexing.AuthorizableIndexingException;
import org.sakaiproject.nakamura.api.user.indexing.AuthorizableIndexingWorker;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
@Component(immediate = true)
@Reference(name="authorizableIndexingWorker",
		cardinality= ReferenceCardinality.OPTIONAL_MULTIPLE,
		policy= ReferencePolicy.DYNAMIC,
		strategy= ReferenceStrategy.EVENT,
		referenceInterface= AuthorizableIndexingWorker.class,
		bind="bindAuthorizableIndexingWorker",
		unbind="unbindAuthorizableIndexingWorker")
public class AuthorizableIndexingHandler implements IndexingHandler {
  private static final String[] DEFAULT_TOPICS = {
      TOPIC_BASE + "authorizables/" + ADDED_TOPIC,
      TOPIC_BASE + "authorizables/" + DELETE_TOPIC,
      TOPIC_BASE + "authorizables/" + UPDATED_TOPIC };


  // list of properties to be indexed
  private static final Map<String, String> USER_WHITELISTED_PROPS;
  static {
    Builder<String, String> builder = ImmutableMap.builder();
    builder.put(NAME_FIELD, NAME_FIELD);
    builder.put(USER_FIRSTNAME_PROPERTY, "firstName");
    builder.put(USER_LASTNAME_PROPERTY, "lastName");
    builder.put(USER_EMAIL_PROPERTY, "email");
    builder.put("type", "type");
    builder.put("sakai:tags", "tag");
    builder.put(LASTMODIFIED_FIELD, Content.LASTMODIFIED_FIELD);
    builder.put(COUNTS_LAST_UPDATE_PROP, "countLastUpdate");
    USER_WHITELISTED_PROPS = builder.build();
  }

  private final static Map<String, String> GROUP_WHITELISTED_PROPS;
  static {
    Builder<String, String> builder = ImmutableMap.builder();
    builder.put(NAME_FIELD, NAME_FIELD);
    builder.put("type", "type");
    builder.put(GROUP_TITLE_PROPERTY, "title");
    builder.put(GROUP_DESCRIPTION_PROPERTY, "description");
    builder.put("sakai:tags", "tag");
    builder.put(SAKAI_CATEGORY, "category");
    builder.put(LASTMODIFIED_FIELD, Content.LASTMODIFIED_FIELD);    
    builder.put(COUNTS_LAST_UPDATE_PROP, "countLastUpdate");
    builder.put(PROP_GROUP_MANAGERS, "manager");
    builder.put(SAKAI_EXCLUDE, "exclude");
    GROUP_WHITELISTED_PROPS = builder.build();
  }

  private final static Set<String> NGRAM_PROPS = ImmutableSet.of(
      USER_FIRSTNAME_PROPERTY,
      USER_LASTNAME_PROPERTY,
      GROUP_TITLE_PROPERTY
  );
  private final static Set<String> EDGE_NGRAM_PROPS = ImmutableSet.of(
      USER_FIRSTNAME_PROPERTY,
      USER_LASTNAME_PROPERTY,
      GROUP_TITLE_PROPERTY
  );

  private static final String SAKAI_PSEUDOGROUPPARENT_PROP = "sakai:parent-group-id";

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Reference
  protected TopicIndexer topicIndexer;

  @Reference
  protected BasicUserInfoService basicInfo;

  private List<AuthorizableIndexingWorker> indexingWorkers = new CopyOnWriteArrayList<AuthorizableIndexingWorker>();

  // ---------- SCR integration ------------------------------------------------
  @Activate
  protected void activate(Map<?, ?> props) {
    for (String topic : DEFAULT_TOPICS) {
      topicIndexer.addHandler(topic, this);
    }
  }

  @Deactivate
  protected void deactivate(Map<?, ?> props) {
    for (String topic : DEFAULT_TOPICS) {
      topicIndexer.removeHandler(topic, this);
    }
  }

  // ---------- IndexingHandler interface --------------------------------------
  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    /*
     * This general process can be better generalized so that a group is sent through
     * common processing and only users are indexed in "group" but that's not necessary
     * yet. The processing below just indexes what is found rather than recursing down the
     * hierarchy. This suffices as long as the inherited members are not required to be
     * indexed which will raise the cost of indexing a group.
     */
    logger.debug("GetDocuments for {} ", event);
    List<SolrInputDocument> documents = Lists.newArrayList();
    String topic = PathUtils.lastElement(event.getTopic());

    if (UPDATED_TOPIC.equals(topic) || ADDED_TOPIC.equals(topic)) {
      // get the name of the authorizable (user,group)
      String authName = String.valueOf(event.getProperty(FIELD_PATH));
      Authorizable authorizable = getAuthorizable(authName, repositorySession);
      SolrInputDocument doc = createAuthDoc(authorizable, repositorySession);
      if (doc != null) {
        for (AuthorizableIndexingWorker worker : indexingWorkers) {
          try {
            worker.decorateSolrInputDocument(doc, event, authorizable, repositorySession);
          } catch (AuthorizableIndexingException e) {
            logger.error("indexing worker [{}] failed to decorate Solr index for [{}]",
              worker.getClass().getName(), authName);
          }
        }
        documents.add(doc);

        logger.info("{} authorizable for searching: {}", topic, authName);
      }
    }
    logger.debug("Got documents {} ", documents);
    return documents;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession,
   *      org.osgi.service.event.Event)
   */
  public Collection<String> getDeleteQueries(RepositorySession repositorySession,
      Event event) {
    Collection<String> retval = Collections.emptyList();
    String topic = event.getTopic();
    String authName = String.valueOf(event.getProperty(FIELD_PATH));
    if (topic.endsWith(DELETE_TOPIC)) {
      logger.debug("GetDelete for {} ", event);
      retval = ImmutableList.of("id:" + ClientUtils.escapeQueryChars(authName));
    }
    return retval;

  }

  // ---------- internal methods ----------
  /**
   * Create the SolrInputDocument for an authorizable.
   *
   * @param authorizable
   * @param repositorySession
   * @return The SolrInputDocument or null if authorizable shouldn't be indexed.
   */
  protected SolrInputDocument createAuthDoc(Authorizable authorizable, RepositorySession repositorySession) {
    if (authorizable == null) {
      return null;
    }

    boolean isAnonymous = User.ANON_USER.equals(authorizable.getId());
    boolean isUserFacingGroup = AuthorizableUtil.isUserFacing(authorizable, true);
    boolean hasManagedGroup = authorizable.hasProperty(PROP_MANAGED_GROUP);

    if (isAnonymous || !isUserFacingGroup || hasManagedGroup) {
      return null;
    }

    // add user properties
    String authName = authorizable.getId();

    SolrInputDocument doc = new SolrInputDocument();
    Map<String, String> fields = (authorizable.isGroup()) ? GROUP_WHITELISTED_PROPS : USER_WHITELISTED_PROPS;

    // collect specific fields separately
    Map<String, Object> properties = authorizable.getSafeProperties();
    for (Entry<String, Object> p : properties.entrySet()) {
      if (fields.containsKey(p.getKey())) {
        String solrField = fields.get(p.getKey());
        doc.addField(solrField, p.getValue());
      }
      if (NGRAM_PROPS.contains(p.getKey())) {
        doc.addField("ngram", p.getValue());
      }
      if (EDGE_NGRAM_PROPS.contains(p.getKey())) {
        doc.addField("edgengram", p.getValue());
      }
    }

    if (!authorizable.isGroup()) {
      // add 'basic info' fields as profile data for users
      String[] basicFields = basicInfo.getBasicProfileElements();
      for (String basicField : basicFields) {
        Object prop = authorizable.getProperty(basicField);
        if (prop != null) {
          doc.addField("profile", prop);
        }
      }
      doc.setField("general_sort", authorizable.getProperty(USER_LASTNAME_PROPERTY));
    } else {
      // add users to the group doc so we can find the group by its member users
      // (and recommend similar groups based on group membership too)
      Group group = (Group) authorizable;
      for (String member : group.getMembers()) {
        doc.addField(FIELD_READERS, member);
      }
      doc.setField("general_sort", authorizable.getProperty(GROUP_TITLE_PROPERTY));
    }

    // add groups to the auth doc so we can find the auth as a group member
    for (String principal : authorizable.getPrincipals()) {
      Authorizable gauth = getAuthorizable(principal, repositorySession);
      if (gauth != null) {
        if (gauth.isGroup() && gauth.hasProperty(SAKAI_PSEUDOGROUPPARENT_PROP)) {
          doc.addField("group", gauth.getProperty(SAKAI_PSEUDOGROUPPARENT_PROP));
        }
        doc.addField("group", principal);
      }
    }

    // add readers
    try {
      Session session = repositorySession.adaptTo(Session.class);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      String[] principals = accessControlManager.findPrincipals(ZONE_AUTHORIZABLES,
          authName, CAN_READ.getPermission(), true);
      for (String principal : principals) {
        doc.addField(FIELD_READERS, principal);
      }
    } catch (StorageClientException e) {
      logger.error(e.getMessage(), e);
    }

    // add the name as the return path so we can group on it later when we search
    // for widgetdata
    doc.setField(FIELD_PATH, authName);
    doc.setField("returnpath", authName);
    // set the resource type and ID
    doc.setField(FIELD_RESOURCE_TYPE, "authorizable");
    doc.setField(FIELD_ID, authName);

    return doc;
  }

  /**
   * Get an authorizable. Convenience method to handle exceptions and processing.
   *
   * @param authName ID of the authorizable to get.
   * @param repositorySession a solr RepositorySession
   * @return Authorizable found or null if none found.
   */
  protected Authorizable getAuthorizable(String authName, RepositorySession repositorySession) {
    Authorizable authorizable = null;
    try {
      Session session = repositorySession.adaptTo(Session.class);
      AuthorizableManager authzMgr = session.getAuthorizableManager();

      // get the name of the authorizable (user,group)
      authorizable = authzMgr.findAuthorizable(authName);
    } catch (StorageClientException e) {
      logger.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      logger.error(e.getMessage(), e);
    }
    return authorizable;
  }

  protected void bindAuthorizableIndexingWorker(AuthorizableIndexingWorker worker,
      Map<String, Object> properties) {
    logger.debug("About to add indexing worker " + worker);
    indexingWorkers.add(worker);
  }

  protected void unbindAuthorizableIndexingWorker(AuthorizableIndexingWorker worker,
      Map<String, Object> properties) {
    indexingWorkers.remove(worker);
  }


}
