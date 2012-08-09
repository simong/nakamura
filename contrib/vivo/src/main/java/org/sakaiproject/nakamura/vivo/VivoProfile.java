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

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class VivoProfile {

  private static final Logger LOGGER = LoggerFactory.getLogger(VivoProfile.class);

  private Model model;
  private String user;
  private Map<String, Object> profileMap = null;
  private ValueMap valueMap;
  private ValueMap compactValueMap;

  private String[] compactKeys = { "firstName", "lastName", "primaryEmail" };

  public VivoProfile(InputStream in, String user) throws VivoProfileException {
    this.user = user;

    // create an empty model
    model = ModelFactory.createDefaultModel();

    // read the RDF/XML file
    model.read(in, null);

    try {
      in.close();
    } catch (IOException e) {
      throw new VivoProfileException("Couldn't properly close the inputstream.", e);
    }
  }

  /**
   * @return A ValueMap that contains the entire profile (with all publications, ..)
   */
  public ValueMap asValueMap() {
    if (valueMap == null) {
      valueMap = new ValueMapDecorator(getFullProfileMap());
      LOGGER.info("Time vivo " + System.currentTimeMillis());
    }
    return valueMap;
  }

  /**
   * @return A valueMap that only contains the absolute necessary information.
   */
  public ValueMap asCompactValueMap() {
    if (compactValueMap == null) {
      compactValueMap = new ValueMapDecorator(new HashMap<String, Object>());
      Map<String, Object> fullProfile = getFullProfileMap();
      for (String key : compactKeys) {
        compactValueMap.put(key, fullProfile.get(key));
      }
    }
    return compactValueMap;
  }

  private Map<String, Object> getFullProfileMap() {
    if (profileMap == null) {
      profileMap = new HashMap<String, Object>();

      // Get the data out of the RDF stream.
      String uri = "http://localhost:7000/individual/" + user;
      Resource res = model.getResource(uri);

      Property prop = model.getProperty("http://xmlns.com/foaf/0.1/firstName");
      profileMap.put("firstName", res.getProperty(prop).getString());
      prop = model.getProperty("http://xmlns.com/foaf/0.1/lastName");
      profileMap.put("lastName", res.getProperty(prop).getString());
      prop = model.getProperty("http://vivoweb.org/ontology/core#primaryEmail");
      profileMap.put("primaryEmail", res.getProperty(prop).getString());

      JSONArray publications = getPublications(res);

      StringWriter writer = new StringWriter();
      model.write(writer);
      profileMap.put("foo", writer.toString());
    }

    return profileMap;
  }

  private JSONArray getPublications(Resource user) {
    JSONArray arr = new JSONArray();

    String authorShip = "http://vivoweb.org/ontology/core#authorInAuthorship";
    Property prop = model.getProperty(authorShip);

    // Iterate over all the properties where a user has been mentioned as an author.
    StmtIterator it = user.listProperties(prop);
    while (it.hasNext()) {
      Statement stmt = it.nextStatement();
      System.out.println(stmt.toString());
      arr.put(getPublication(stmt.getObject()));
    }
    return arr;
  }

  private JSONObject getPublication(RDFNode node) {
    JSONObject o = new JSONObject();

    // Get the titel.
    Property prop = model
        .getProperty("http://vivoweb.org/ontology/core#linkedInformationResource");
    Statement stmt = ((Resource) node).getProperty(prop);
    String uri = stmt.getObject().toString();
    Resource titleResource = model.getResource(uri);
    prop = model.getProperty("http://www.w3.org/2000/01/rdf-schema#label");
    String title = titleResource.getProperty(prop).getString();


    return o;
  }

}
