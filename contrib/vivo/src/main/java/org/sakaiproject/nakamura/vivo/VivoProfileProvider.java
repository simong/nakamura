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
package org.sakaiproject.nakamura.vivo;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.profile.ProfileProvider;
import org.sakaiproject.nakamura.api.profile.ProviderSettings;
import org.sakaiproject.nakamura.util.ImmediateFuture;
import org.sakaiproject.nakamura.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Component(immediate = true, description = "A Profile Provider that connects to a VIVO instance.", name = "VIVO Profile Provider")
@Service()
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "A Profile Provider that connects to a VIVO instance."),
    @Property(name = ProfileProvider.PROVIDER_NAME, value = "vivo") })
public class VivoProfileProvider implements ProfileProvider {

  @Property(value = "http://localhost:7000/vivo", description = "The base url (http included) where vivo is running.")
  public static final String PROP_VIVO_URL = "sakai.vivo.url";
  private String vivoURL;

  private static final Logger LOGGER = LoggerFactory.getLogger(VivoProfileProvider.class);
  private HttpClient client = new HttpClient();

  /**
   * {@inheritDoc}
   * 
   * @see org.sakaiproject.nakamura.api.profile.ProfileProvider#getProvidedMap(java.util.List)
   */
  public Map<String, ? extends Future<Map<String, Object>>> getProvidedMap(
      List<ProviderSettings> list) {
    Map<String, Future<Map<String, Object>>> resultMap = new HashMap<String, Future<Map<String, Object>>>();
    for (ProviderSettings s : list) {
      Content userNode = s.getNode();

      try {
        // Get internal user id.
        String userID = getUserID(userNode);

        // Get the external vivo profile.
        VivoProfile profile = getVivoProfile(userID);

        // Add our data in.
        Map<String, Object> data = profile.asValueMap();
        resultMap.put(userNode.getPath(), new ImmediateFuture<Map<String, Object>>(data));

      } catch (VivoProfileException e) {
        LOGGER.error("Couldn't retrieve a VIVO profile.", e);
        Map<String, Object> profileError = new HashMap<String, Object>();
        profileError.put("error", e.getMessage());
        resultMap.put(userNode.getPath(), new ImmediateFuture<Map<String, Object>>(
            profileError));
      }
    }
    return resultMap;
  }

  @Activate
  public void activate(Map<String, Object> properties) {
    vivoURL = properties.get(PROP_VIVO_URL).toString();
  }

  /**
   * Gets the user id out of a profile content node. This assumes that the required
   * rep:userID field is present. If not, it throws an error.
   * 
   * @param content
   *          The sparsemap content node.
   * @return The userid
   * @throws VivoProfileException
   *           If no userid could be found,
   */
  private String getUserID(Content content) throws VivoProfileException {
    if (content.hasProperty("homePath")) {
      String homePath = (String) content.getProperty("homePath");
      return StringUtils.split(homePath, '/')[0].substring(1);
    } else {
      throw new VivoProfileException("Didn't find a user id on this node.");
    }
  }

  /**
   * Contacts the VIVO Service and retrieves the profile.
   * 
   * @param userID
   * @return
   * @throws VivoProfileException
   */
  public VivoProfile getVivoProfile(String userID) throws VivoProfileException {
    try {

      LOGGER.info("Time vivo " + System.currentTimeMillis());
      // String id = userID;
      String id = "n516";
      HttpMethod method = new GetMethod(vivoURL + "/individual/" + id + "/" + id + ".rdf");
      int status = client.executeMethod(method);
      LOGGER.info("Time vivo " + System.currentTimeMillis());
      if (status != HttpStatus.SC_OK) {
        throw new VivoProfileException("The VIVO service failed to respond");
      }

      return new VivoProfile(method.getResponseBodyAsStream(), id);

    } catch (HttpException e) {
      throw new VivoProfileException("Couldn't contact the VIVO service", e);
    } catch (IOException e) {
      throw new VivoProfileException("Couldn't parse the VIVO response", e);
    }
  }

}
