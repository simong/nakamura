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

import static javax.jcr.security.Privilege.JCR_READ;

import static org.mockito.Mockito.verify;

import static javax.jcr.security.Privilege.JCR_ALL;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_MANAGERS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_NODENAME;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_VIEWERS;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.testing.jcr.MockNode;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

/**
 *
 */
public class ManageMembersContentPoolServletTest {

  @Mock
  private SlingRepository slingRepository;
  @Mock
  private SlingHttpServletRequest request;
  @Mock
  private SlingHttpServletResponse response;
  @Mock
  private Resource resource;
  @Mock
  private Node fileNode;
  @Mock
  private JackrabbitSession session;
  @Mock
  private ValueFactory valueFactory;
  @Mock
  private PrincipalManager principalManager;
  @Mock
  private AccessControlManager acm;
  @Mock
  private JackrabbitSession adminSession;
  @Mock
  private Privilege allPrivilege;
  @Mock
  private Privilege readPrivilege;

  private MockNode membersNode;
  private ManageMembersContentPoolServlet servlet;
  private PrintWriter printWriter;
  private StringWriter stringWriter;
  private AccessControlList acl;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    servlet = new ManageMembersContentPoolServlet();
    servlet.slingRepository = slingRepository;

    // Set the values on the members node.
    membersNode = new MockNode("/path/to/pooled/content/file/members");
    membersNode.setProperty(POOLED_CONTENT_MEMBERS_MANAGERS, new String[] { "alice" });
    membersNode.setProperty(POOLED_CONTENT_MEMBERS_VIEWERS, new String[] { "bob" });

    // Mock the request and the filenode.
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Node.class)).thenReturn(fileNode);
    when(fileNode.getNode(POOLED_CONTENT_MEMBERS_NODENAME)).thenReturn(membersNode);
    when(fileNode.getSession()).thenReturn(session);
    when(fileNode.getPath()).thenReturn("/path/to/pooled/content/file");
    when(session.getPrincipalManager()).thenReturn(principalManager);
    when(session.getAccessControlManager()).thenReturn(acm);

    // Handle setting ACLs.
    when(slingRepository.loginAdministrative(null)).thenReturn(adminSession);
    when(adminSession.getPrincipalManager()).thenReturn(principalManager);
    when(adminSession.getAccessControlManager()).thenReturn(acm);
    when(adminSession.getValueFactory()).thenReturn(valueFactory);
    when(acm.privilegeFromName(JCR_ALL)).thenReturn(allPrivilege);
    when(acm.privilegeFromName(JCR_READ)).thenReturn(readPrivilege);
    acl = mock(AccessControlList.class);
    when(acl.getAccessControlEntries()).thenReturn(new AccessControlEntry[0]);
    AccessControlPolicy[] acp = new AccessControlPolicy[] { acl };
    when(acm.getPolicies(Mockito.anyString())).thenReturn(acp);

    // Make sure we can write to something.
    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);
  }

  @Test
  public void testGetMembers() throws Exception {
    servlet.doGet(request, response);
    printWriter.flush();

    // If all went right, we should have 1 manager Alice and one viewer bob.
    JSONObject json = new JSONObject(stringWriter.toString());
    assertEquals(1, json.getJSONArray("managers").length());
    assertEquals(1, json.getJSONArray("viewers").length());
    assertEquals("alice", json.getJSONArray("managers").getJSONObject(0).get("userid"));
    assertEquals("bob", json.getJSONArray("viewers").getJSONObject(0).get("userid"));
  }

  @Test
  public void testAddManager() throws Exception {
    // We want to add a manager called charly.
    // Alice should be ignored because she is already in there.
    when(request.getParameterValues(":manager")).thenReturn(
        new String[] { "charly", "alice" });

    Principal charly = mock(Principal.class);
    when(charly.getName()).thenReturn("charly");
    when(principalManager.getPrincipal("charly")).thenReturn(charly);

    // To be able to make someone a manager, we have to be a manager ourselves.
    when(acm.hasPrivileges(fileNode.getPath(), new Privilege[] { allPrivilege }))
        .thenReturn(true);
    when(adminSession.hasPendingChanges()).thenReturn(true);

    servlet.doPost(request, response);

    // Verify we saved everything and then properly logged out.
    verify(response).setStatus(200);
    verify(adminSession).save();
    verify(adminSession).logout();

    // Make sure that the user was added to the right group
    Value[] managers = membersNode.getProperty(POOLED_CONTENT_MEMBERS_MANAGERS)
        .getValues();
    assertEquals(2, managers.length);
    Value[] viewers = membersNode.getProperty(POOLED_CONTENT_MEMBERS_VIEWERS).getValues();
    assertEquals(1, viewers.length);
  }

  @Test
  public void testDeleteManager() throws Exception {
    // We want to delete alice as a manager
    when(request.getParameterValues(":manager@Delete")).thenReturn(
        new String[] { "alice" });

    Principal alice = mock(Principal.class);
    when(alice.getName()).thenReturn("alice");
    when(principalManager.getPrincipal("alice")).thenReturn(alice);

    // To be able to make someone a manager, we have to be a manager ourselves.
    when(acm.hasPrivileges(fileNode.getPath(), new Privilege[] { allPrivilege }))
        .thenReturn(true);

    // Because we're denying privileges, we intercept the addEntry on the acl object.
    acl = new AccessControlList() {

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
    AccessControlPolicy[] acp = new AccessControlPolicy[] { acl };
    when(acm.getPolicies(Mockito.anyString())).thenReturn(acp);

    when(adminSession.hasPendingChanges()).thenReturn(true);

    servlet.doPost(request, response);

    // Verify we saved everything and then properly logged out.
    verify(response).setStatus(200);
    verify(adminSession).save();
    verify(adminSession).logout();

    // Make sure that the user was added to the right group
    Value[] managers = membersNode.getProperty(POOLED_CONTENT_MEMBERS_MANAGERS)
        .getValues();
    assertEquals(0, managers.length);
    Value[] viewers = membersNode.getProperty(POOLED_CONTENT_MEMBERS_VIEWERS).getValues();
    assertEquals(1, viewers.length);
  }

}
