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
package org.sakaiproject.nakamura.user;

import static org.sakaiproject.nakamura.api.user.UserConstants.AUTHZ_COUNTS_PROPS;
import static org.sakaiproject.nakamura.api.user.UserConstants.COUNTS_PROP;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_COUNTS_PROPS;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_DESCRIPTION_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.GROUP_TITLE_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.PREFERRED_NAME;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_PARENT_GROUP_ID;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_PARENT_GROUP_TITLE;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_PSEUDO_GROUP;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_ROLE_TITLE;
import static org.sakaiproject.nakamura.api.user.UserConstants.PROP_ROLE_TITLE_PLURAL;
import static org.sakaiproject.nakamura.api.user.UserConstants.SAKAI_CATEGORY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_BASIC;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_BASIC_ACCESS;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_COLLEGE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_COUNTS_PROPS;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_DATEOFBIRTH;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_DEPARTMENT;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_EMAIL_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_FIRSTNAME_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_HOME_PATH;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_LASTNAME_PROPERTY;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_PICTURE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_ROLE;
import static org.sakaiproject.nakamura.api.user.UserConstants.USER_TAGS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Maps;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.counts.CountProvider;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

@Component(immediate=true, metatype=true)
@Service(value=BasicUserInfoService.class)
public class BasicUserInfoServiceImpl implements BasicUserInfoService {



  @Property(value={USER_FIRSTNAME_PROPERTY, USER_LASTNAME_PROPERTY,
      USER_EMAIL_PROPERTY, USER_PICTURE, PREFERRED_NAME, USER_ROLE, USER_DEPARTMENT, USER_COLLEGE, USER_DATEOFBIRTH, USER_TAGS})
  public final static String BASIC_PROFILE_ELEMENTS = "basicUserInfoElements";

  private final static String[] DEFAULT_BASIC_USER_INFO_ELEMENTS = new String[] {USER_FIRSTNAME_PROPERTY, USER_LASTNAME_PROPERTY,
    USER_EMAIL_PROPERTY, USER_PICTURE, PREFERRED_NAME, USER_ROLE, USER_DEPARTMENT, USER_COLLEGE, USER_DATEOFBIRTH, USER_TAGS};


  private static String[] basicUserInfoElements = DEFAULT_BASIC_USER_INFO_ELEMENTS;

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicUserInfoServiceImpl.class);

  @Reference
  protected CountProvider countProvider;

  @Reference
  protected Repository repository;

  @Activate
  protected void activated(Map<String, Object> properties ) {
    modified(properties);
  }

  @Modified
  protected void modified(Map<String, Object> properties ) {
    basicUserInfoElements = PropertiesUtil.toStringArray(properties.get(BASIC_PROFILE_ELEMENTS), DEFAULT_BASIC_USER_INFO_ELEMENTS);
  }

  

  
  
  public Map<String, Object> getProperties(Authorizable authorizable) {
    if (authorizable == null || User.ANON_USER.equals(authorizable.getId())) {
      return anonymousBasicInfo();
    }
    
    Map<String, Object> basicUserInfo = Maps.newHashMap();
    basicUserInfo.put(USER_BASIC, basicProfileMapForAuthorizable(authorizable));
    basicUserInfo.put(COUNTS_PROP, countsMapforAuthorizable(authorizable));
    basicUserInfo.put(USER_HOME_PATH, LitePersonalUtils.getHomeResourcePath(authorizable.getId()));
    if ( authorizable.hasProperty(UserConstants.SAKAI_EXCLUDE)) {
      basicUserInfo.put(UserConstants.SAKAI_EXCLUDE, authorizable.getProperty(UserConstants.SAKAI_EXCLUDE));
    } else {
      basicUserInfo.put(UserConstants.SAKAI_EXCLUDE, false);
    }

    if (authorizable.isGroup()) {
      addGroupProperties(authorizable, basicUserInfo);
    } else {
      addUserProperties(authorizable, basicUserInfo);
    }
    return basicUserInfo;
  }
  
  

  private Map<String, Object> countsMapforAuthorizable(Authorizable authorizable) {
    Builder<String, Object> propertyBuilder = ImmutableMap.builder();
    Session adminSession = null;
    try {
      adminSession = this.repository.loginAdministrative();
      buildCountsMap(AUTHZ_COUNTS_PROPS, authorizable, propertyBuilder, adminSession);
      if (authorizable instanceof User) {
        buildCountsMap(USER_COUNTS_PROPS, authorizable, propertyBuilder, adminSession);
      }
      if (authorizable instanceof Group) {
        buildCountsMap(GROUP_COUNTS_PROPS, authorizable, propertyBuilder, adminSession);
      }
    } catch (AccessDeniedException e) {
      LOGGER.error("Error getting map of authorizable's counts", e);
    } catch (ClientPoolException e) {
      LOGGER.error("Error getting map of authorizable's counts", e);
    } catch (StorageClientException e) {
      LOGGER.error("Error getting map of authorizable's counts", e);
    } finally {
      if ( adminSession != null ) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("Error logging out of admin session", e);
        }
      }
    }

    Map<String, Object> allCounts = propertyBuilder.build();
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("counts map: {} for authorizableId: {}", new Object[]{allCounts, authorizable.getId()});
    }
    return allCounts;
  }

  private void buildCountsMap(String[] properties, Authorizable authorizable, Builder<String,
      Object> propertyBuilder, Session session) throws StorageClientException, AccessDeniedException {
    for (String countPropName : properties) {
      if (!authorizable.hasProperty(countPropName)) {
        countProvider.updateCountProperty(authorizable, countPropName, session);
      }
      if ( authorizable.getProperty(countPropName) != null ) {
        propertyBuilder.put(countPropName, authorizable.getProperty(countPropName));
      }
    }
  }

  private void addUserProperties(Authorizable user, Map<String, Object> basicInfo) {
    // Backward compatible reasons.
    basicInfo.put("rep:userId", user.getId());
    basicInfo.put("userid", user.getId());
    basicInfo.put("hash", user.getId());
  }

  private void addGroupProperties(Authorizable group, Map<String, Object> basicInfo) {
    // For a group we just dump it's title and description.
    basicInfo.put("groupid", group.getId());
    basicInfo.put("sakai:group-id", group.getId());
    basicInfo.put(GROUP_TITLE_PROPERTY, group.getProperty(GROUP_TITLE_PROPERTY));
    basicInfo.put(GROUP_DESCRIPTION_PROPERTY, group
        .getProperty(GROUP_DESCRIPTION_PROPERTY));

    // KERN-1859 add sakai:category, created, lastModified, createdBy, and lastModifiedBy
    basicInfo.put(SAKAI_CATEGORY, group.getProperty(SAKAI_CATEGORY));
    basicInfo.put("created", group.getProperty("created"));
    basicInfo.put("lastModified", group.getProperty("lastModified"));
    basicInfo.put("createdBy", group.getProperty("createdBy"));
    basicInfo.put("lastModifiedBy", group.getProperty("lastModifiedBy"));
    basicInfo.put("sakai:group-joinable", group.getProperty("sakai:group-joinable"));

    // Add some extra fields if it is a pseudo-group
    if (group.hasProperty(PROP_PSEUDO_GROUP)) {
      basicInfo.put(PROP_PSEUDO_GROUP, group.getProperty(PROP_PSEUDO_GROUP));
      basicInfo.put(PROP_PARENT_GROUP_ID, group.getProperty(PROP_PARENT_GROUP_ID));
      basicInfo.put(PROP_PARENT_GROUP_TITLE, group.getProperty(PROP_PARENT_GROUP_TITLE));
      basicInfo.put(PROP_ROLE_TITLE, group.getProperty(PROP_ROLE_TITLE));
      basicInfo.put(PROP_ROLE_TITLE_PLURAL, group.getProperty(PROP_ROLE_TITLE_PLURAL));
    }
  }

  private  Map<String, Object> basicProfileMapForAuthorizable(Authorizable authorizable) {
    Builder<String, Object> propertyBuilder = ImmutableMap.builder();
    for (String basicUserInfoElementName : basicUserInfoElements ) {
      if (authorizable.hasProperty(basicUserInfoElementName)) {
        propertyBuilder.put(basicUserInfoElementName, authorizable.getProperty(basicUserInfoElementName));
      }
    }
    // The map were we will stick the compact information in.
    Map<String, Object> basicInfo = basicInfo(propertyBuilder.build());
    if ( authorizable.hasProperty(USER_BASIC_ACCESS)) {
      basicInfo.put(USER_BASIC_ACCESS, authorizable.getProperty(USER_BASIC_ACCESS));
    } else {
      basicInfo.put(USER_BASIC_ACCESS, UserConstants.EVERYBODY_ACCESS_VALUE);
    }
    return basicInfo;
  }
  
  
  private  Map<String, Object> basicInfo(Map<String, Object> elementsMap) {
    Map<String, Object> basic = Maps.newHashMap();
    Map<String, Object> elements = Maps.newHashMap();
    for (Entry<String, Object> entry : elementsMap.entrySet()) {
      elements.put(entry.getKey(), ImmutableMap.of("value", entry.getValue()));
    }
    basic.put("elements", elements);
    return basic;
  }
  
  private Map<String, Object> anonymousBasicInfo() {
    Map<String, Object> rv = Maps.newHashMap();
    rv.put("rep:userId", User.ANON_USER);
    Map<String, Object> basicProfile =  basicInfo(
        ImmutableMap.of(USER_FIRSTNAME_PROPERTY, (Object)"Anonymous", USER_LASTNAME_PROPERTY, "User", USER_EMAIL_PROPERTY, "anon@sakai.invalid"));
    basicProfile.put(UserConstants.USER_BASIC_ACCESS, UserConstants.EVERYBODY_ACCESS_VALUE);
    rv.put(USER_BASIC,basicProfile);
    return rv;
  }

  public String[] getBasicProfileElements() {
    return basicUserInfoElements;
  }



}
