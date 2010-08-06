/*
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

package org.sakaiproject.nakamura.files.pool;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;
import static org.apache.jackrabbit.JcrConstants.NT_RESOURCE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_MANAGERS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_NODENAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_VIEWERS;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.kahadb.util.ByteArrayInputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;
import javax.servlet.ServletException;

public class CreateContentPoolServletTest {

  @Mock
  private SlingRepository slingRepository;
  @Mock
  private JackrabbitSession adminSession;
  @Mock
  private PrincipalManager principalManager;
  @Mock
  private Principal iebPrincipal;
  @Mock
  private Node parentNode;
  @Mock
  private Node resourceNode;
  @Mock
  private Node membersNode;
  @Mock
  private AccessControlManager accessControlManager;
  @Mock
  private Privilege allPrivilege;

  private AccessControlList accessControlList;
  @Mock
  private ValueFactory valueFactory;
  @Mock
  private Binary binary;
  @Mock
  private ClusterTrackingService clusterTrackingService;
  @Mock
  private ComponentContext componentContext;
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private SlingHttpServletResponse response;
  @Mock
  private RequestParameterMap requestParameterMap;
  @Mock
  private RequestParameter requestParameter1;
  @Mock
  private RequestParameter requestParameter2;
  @Mock
  private RequestParameter requestParameterNot;

  public CreateContentPoolServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testCreate() throws RepositoryException, ServletException, IOException,
      JSONException {

    // activate
    when(clusterTrackingService.getCurrentServerId()).thenReturn("serverID");
    when(slingRepository.loginAdministrative(null)).thenReturn(adminSession);

    when(adminSession.getPrincipalManager()).thenReturn(principalManager);
    when(adminSession.getAccessControlManager()).thenReturn(accessControlManager);
    when(request.getRemoteUser()).thenReturn("ieb");
    when(principalManager.getPrincipal("ieb")).thenReturn(iebPrincipal);

    when(request.getRequestParameterMap()).thenReturn(requestParameterMap);
    Map<String, RequestParameter[]> map = new HashMap<String, RequestParameter[]>();

    RequestParameter[] requestParameters = new RequestParameter[] { requestParameter1,
        requestParameterNot, requestParameter2, };
    map.put("files", requestParameters);

    when(requestParameterMap.entrySet()).thenReturn(map.entrySet());

    when(requestParameter1.isFormField()).thenReturn(false);
    when(requestParameter1.getContentType()).thenReturn("application/pdf");
    when(requestParameter1.getFileName()).thenReturn("testfilename.pdf");
    InputStream input1 = new ByteArrayInputStream(new byte[10]);
    when(requestParameter1.getInputStream()).thenReturn(input1);

    when(requestParameter2.isFormField()).thenReturn(false);
    when(requestParameter2.getContentType()).thenReturn("text/html");
    when(requestParameter2.getFileName()).thenReturn("index.html");
    InputStream input2 = new ByteArrayInputStream(new byte[10]);
    when(requestParameter2.getInputStream()).thenReturn(input2);

    when(requestParameterNot.isFormField()).thenReturn(true);

    // deep create
    when(adminSession.itemExists(Mockito.anyString())).thenReturn(true);
    when(adminSession.getItem(Mockito.anyString())).thenReturn(parentNode);
    when(parentNode.addNode(JCR_CONTENT, NT_RESOURCE)).thenReturn(resourceNode);
    when(parentNode.getPath()).thenReturn("/_p/hashedpath/id");
    when(resourceNode.getPath()).thenReturn(
        "/_p/hashedpath/id/" + JcrConstants.JCR_CONTENT);
    when(resourceNode.getPath()).thenReturn(
        "/_p/hashedpath/id/" + JcrConstants.JCR_CONTENT);
    when(adminSession.getValueFactory()).thenReturn(valueFactory);
    when(valueFactory.createBinary(Mockito.any(InputStream.class))).thenReturn(binary);

    // access control utils
    accessControlList = new AccessControlList() {

      // Add an "addEntry" method so AccessControlUtil can execute something.
      // This method doesn't do anything useful.
      @SuppressWarnings("unused")
      public boolean addEntry(Principal principal, Privilege[] privileges, boolean isAllow)
          throws AccessControlException {
        return true;
      }

      public void removeAccessControlEntry(AccessControlEntry ace)
          throws AccessControlException, RepositoryException {
      }

      public AccessControlEntry[] getAccessControlEntries() throws RepositoryException {
        return new AccessControlEntry[0];
      }

      public boolean addAccessControlEntry(Principal principal, Privilege[] privileges)
          throws AccessControlException, RepositoryException {
        return false;
      }
    };
    when(accessControlManager.privilegeFromName(Mockito.anyString())).thenReturn(
        allPrivilege);
    AccessControlPolicy[] acp = new AccessControlPolicy[] { accessControlList };
    when(accessControlManager.getPolicies(Mockito.anyString())).thenReturn(acp);

    // Mock the members node behaviour
    when(parentNode.addNode(POOLED_CONTENT_MEMBERS_NODENAME)).thenReturn(membersNode);
    ArgumentCaptor<String[]> managersCaptor = ArgumentCaptor.forClass(String[].class);
    ArgumentCaptor<String[]> viewersCaptor = ArgumentCaptor.forClass(String[].class);
    when(
        membersNode.setProperty(Mockito.eq(POOLED_CONTENT_MEMBERS_MANAGERS),
            managersCaptor.capture())).thenReturn(null);
    when(
        membersNode.setProperty(Mockito.eq(POOLED_CONTENT_MEMBERS_VIEWERS), viewersCaptor
            .capture())).thenReturn(null);

    // saving
    when(adminSession.hasPendingChanges()).thenReturn(true);

    StringWriter stringWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));

    CreateContentPoolServlet cp = new CreateContentPoolServlet();
    cp.clusterTrackingService = clusterTrackingService;
    cp.slingRepository = slingRepository;
    cp.activate(componentContext);

    cp.doPost(request, response);

    // Verify that we created all the nodes.
    assertEquals(1, managersCaptor.getValue().length);
    assertEquals(iebPrincipal.getName(), managersCaptor.getValue()[0]);
    assertEquals(0, viewersCaptor.getValue().length);

    JSONObject jsonObject = new JSONObject(stringWriter.toString());
    Assert.assertNotNull(jsonObject.getString("testfilename.pdf"));
    Assert.assertNotNull(jsonObject.getString("index.html"));
    Assert.assertEquals(2, jsonObject.length());

  }

}
