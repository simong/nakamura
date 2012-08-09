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
package org.sakaiproject.nakamura.api.solr;

import org.apache.solr.client.solrj.SolrServer;

/**
 * A service to manage the SolrServer implementation.
 */
public interface SolrServerService {

  /**
   * @return the Current Solr Server, which might be embedded or might be remote depending
   *         on the implementation of the service.
   */
  SolrServer getServer();

  /**
   * @return the Solr Server used to perform updates.
   */
  SolrServer getUpdateServer();

  /**
   * @return the location of the Solr Home.
   */
  String getSolrHome();
  
  /**
   * In case you need to integrate with multiple solr servers you can use this method to
   * retrieve your own server.
   * 
   * @return the specified solr server.
   * @throws RunTimeException
   *           if no server by that name could be found.
   */
  SolrServer getServerByName(String name);

}
