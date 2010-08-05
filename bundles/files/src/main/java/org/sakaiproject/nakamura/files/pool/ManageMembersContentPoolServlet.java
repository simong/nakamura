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

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import static javax.jcr.security.Privilege.JCR_ALL;
import static javax.jcr.security.Privilege.JCR_READ;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.apache.sling.jcr.base.util.AccessControlUtil.replaceAccessControlEntry;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.personal.PersonalUtils;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;
import javax.servlet.ServletException;

@SlingServlet(methods = { "GET", "POST" }, resourceTypes = { "sakai/pooled-content" }, selectors = { "members" })
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Manages the Managers and Viewers for pooled content.") })
public class ManageMembersContentPoolServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = 3385014961034481906L;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ManageMembersContentPoolServlet.class);

  @Reference
  protected transient SlingRepository slingRepository;

  /**
   * Retrieves the list of members.
   * 
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      // Get hold of the actual file.
      Node node = request.getResource().adaptTo(Node.class);

      // Get hold of an access manager to retrieve the list of ACLs with.
      Session session = request.getResourceResolver().adaptTo(Session.class);
      AccessControlManager acm = AccessControlUtil.getAccessControlManager(session);

      // Get a list of all the entries.
      AccessControlPolicy[] policies = acm.getEffectivePolicies(node.getPath());
      AccessControlEntry[] entries = null;
      for (AccessControlPolicy policy : policies) {
        if (policy instanceof AccessControlList) {
          entries = ((AccessControlList) policy).getAccessControlEntries();

          // We're only interested on the entries on this node.
          // These are always in the first ACL
          break;
        }
      }

      if (entries == null) {
        response.sendError(SC_INTERNAL_SERVER_ERROR, "Could not lookup ACL list.");
        return;
      }

      Set<String> managers = new HashSet<String>();
      Set<String> viewers = new HashSet<String>();

      // Loop over all the entries.
      for (AccessControlEntry ace : entries) {

        // We only need to check granted entries.
        if (AccessControlUtil.isAllow(ace)) {
          boolean isManager = false;
          boolean isViewer = false;

          // We were granted "something". Figure out which one and depending on that
          // privilege this user is a manager or a viewer.
          for (Privilege p : ace.getPrivileges()) {
            if (p.getName().equals("jcr:all")) {
              isManager = true;
            } else if (p.getName().equals("jcr:read")) {
              isViewer = true;
            }
          }

          // Add the user to one of the sets.
          if (isManager) {
            managers.add(ace.getPrincipal().getName());
          } else if (isViewer) {
            viewers.add(ace.getPrincipal().getName());
          }
        }
      }

      // Loop over the sets and output it.
      ExtendedJSONWriter writer = new ExtendedJSONWriter(response.getWriter());
      writer.object();
      writer.key("managers");
      writer.array();
      for (String manager : managers) {
        PersonalUtils.writeCompactUserInfo(session, manager, writer);
      }
      writer.endArray();
      writer.key("viewers");
      writer.array();
      for (String viewer : viewers) {
        PersonalUtils.writeCompactUserInfo(session, viewer, writer);
      }
      writer.endArray();
      writer.endObject();
    } catch (RepositoryException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Could not lookup ACL list.");
    } catch (JSONException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
    }

  }

  /**
   * Manipulate the member list for this file.
   * 
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // Anonymous users cannot do anything.
    // This is just a safety check really, they SHOULD NOT even be able to get to this
    // point.
    if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
      response.sendError(SC_UNAUTHORIZED, "Anonymous users cannot manipulate content.");
      return;
    }

    Session adminSession = null;
    try {
      // Grab the principal manager for THIS user his session.
      // It is possible we can't look up certain groups.
      Session session = request.getResourceResolver().adaptTo(Session.class);
      PrincipalManager pm = AccessControlUtil.getPrincipalManager(session);

      // The privileges for the managers and viewers.
      // Viewers only get READ, managers get ALL
      String[] managerPrivs = new String[] { JCR_ALL };
      String[] viewerPrivs = new String[] { JCR_READ };

      // Get the node.
      Node node = request.getResource().adaptTo(Node.class);

      // We need an admin session because we might only have READ access on this node.
      // Yes, that is sufficient to share a file with somebody else.
      adminSession = slingRepository.loginAdministrative(null);

      // If you have READ access than you can give other people read as well.
      manipulateACL(request, adminSession, node.getPath(), viewerPrivs, pm, "viewer");

      // Only managers can make other people managers, so we need to do some checks.
      AccessControlManager acm = AccessControlUtil.getAccessControlManager(session);
      Privilege allPriv = acm.privilegeFromName(JCR_ALL);
      if (acm.hasPrivileges(node.getPath(), new Privilege[] { allPriv })) {
        manipulateACL(request, adminSession, node.getPath(), managerPrivs, pm, "manager");
      }

      // Persist any changes.
      if (adminSession.hasPendingChanges()) {
        adminSession.save();
      }
    } catch (RepositoryException e) {
      LOGGER
          .error("Could not set some permissions on '" + request.getPathInfo() + "'", e);
    } finally {
      if (adminSession != null) {
        adminSession.logout();
      }
    }
  }

  /**
   * Looks at the values of some request parameters (specified by the key) and sets some
   * ACLs.
   * 
   * @param request
   *          The request that contains the request parameters.
   * @param session
   *          A session that can change permissions on the specified path.
   * @param path
   *          The path for which the permissions should be changed.
   * @param privilege
   *          Which privileges should be granted (and removed for those specied in the
   *          "key@Delete" request parameter.
   * @param pm
   *          A PrincipalManager to retrieve the correct principals. Make sure that this
   *          uses the correct session to look up groups/users.
   * @param key
   *          The key that should be used to look for the request parameters. A key of
   *          'manager' will result in 2 parameters to be looked up.
   *          <ul>
   *          <li>manager : A multi-valued request parameter that contains the IDs of the
   *          principals that should be granted the specified privileges</li>
   *          <li>manager@Delete : A multi-valued request parameter that contains the IDs
   *          of the princpals whose privileges should be revoked.</li>
   *          </ul>
   * @throws RepositoryException
   */
  protected void manipulateACL(SlingHttpServletRequest request, Session session,
      String path, String[] privilege, PrincipalManager pm, String key)
      throws RepositoryException {

    // Get all the IDs of the principals that should be added and removed from the
    // request.
    String[] toAdd = request.getParameterValues(key);
    Set<Principal> toAddSet = new HashSet<Principal>();
    String[] toDelete = request.getParameterValues(key + "@Delete");
    Set<Principal> toDeleteSet = new HashSet<Principal>();

    // Resolve the principals to IDs.
    resolveNames(pm, toAdd, toAddSet);
    resolveNames(pm, toDelete, toDeleteSet);

    // Give the privileges to the set that should be added.
    for (Principal principal : toAddSet) {
      replaceAccessControlEntry(session, path, principal, privilege, null, null, null);
    }

    // Remove the privileges for the people that should be
    // TODO Maybe remove the entire entry instead of just denying the privilege?
    for (Principal principal : toDeleteSet) {
      replaceAccessControlEntry(session, path, principal, null, privilege, null, null);
    }

  }

  /**
   * Resolves each string in the array of names and adds them to the set of principals.
   * Principals that cannot be found, will not be added to the set.
   * 
   * @param pm
   *          A PrincipalManager that can be used to find principals.
   * @param names
   *          An array of strings that contain the names.
   * @param principals
   *          A Set of Principals where each principal can be added to.
   */
  protected void resolveNames(PrincipalManager pm, String[] names,
      Set<Principal> principals) {
    if (names != null) {
      for (String principalName : names) {
        Principal principal = pm.getPrincipal(principalName);
        if (principal != null) {
          principals.add(principal);
        }
      }
    }
  }
}
