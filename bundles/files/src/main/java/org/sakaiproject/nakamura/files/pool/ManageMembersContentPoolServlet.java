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

import static javax.servlet.http.HttpServletResponse.SC_OK;

import static javax.jcr.security.Privilege.JCR_ALL;
import static javax.jcr.security.Privilege.JCR_READ;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.apache.sling.jcr.base.util.AccessControlUtil.replaceAccessControlEntry;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_MEMBERS_NODENAME;

import com.google.common.collect.Lists;

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
import java.util.List;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlManager;
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
      Session session = node.getSession();

      // Get hold of the members node that is under the file.
      // This node contains a list of managers and viewers.
      Node membersNode = node.getNode(POOLED_CONTENT_MEMBERS_NODENAME);
      Value[] managers = membersNode.getProperty("sakai:managers").getValues();
      Value[] viewers = membersNode.getProperty("sakai:viewers").getValues();

      // Loop over the sets and output it.
      ExtendedJSONWriter writer = new ExtendedJSONWriter(response.getWriter());
      writer.object();
      writer.key("managers");
      writer.array();
      for (Value manager : managers) {
        PersonalUtils.writeCompactUserInfo(session, manager.getString(), writer);
      }
      writer.endArray();
      writer.key("viewers");
      writer.array();
      for (Value viewer : viewers) {
        PersonalUtils.writeCompactUserInfo(session, viewer.getString(), writer);
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
      // Get the node.
      Node node = request.getResource().adaptTo(Node.class);

      // Grab the principal manager for THIS user his session.
      // It is possible we can't look up certain groups.
      Session session = node.getSession();
      PrincipalManager pm = AccessControlUtil.getPrincipalManager(session);

      // The privileges for the managers and viewers.
      // Viewers only get READ, managers get ALL
      String[] managerPrivs = new String[] { JCR_ALL };
      String[] viewerPrivs = new String[] { JCR_READ };

      // We need an admin session because we might only have READ access on this node.
      // Yes, that is sufficient to share a file with somebody else.
      adminSession = slingRepository.loginAdministrative(null);

      // If you have READ access than you can give other people read as well.
      manipulateACL(request, adminSession, node, viewerPrivs, pm, ":viewer",
          "sakai:viewers");

      // Only managers can make other people managers, so we need to do some checks.
      AccessControlManager acm = AccessControlUtil.getAccessControlManager(session);
      Privilege allPriv = acm.privilegeFromName(JCR_ALL);
      if (acm.hasPrivileges(node.getPath(), new Privilege[] { allPriv })) {
        manipulateACL(request, adminSession, node, managerPrivs, pm, ":manager",
            "sakai:managers");
      }

      // Persist any changes.
      if (adminSession.hasPendingChanges()) {
        adminSession.save();
      }
      response.setStatus(SC_OK);
    } catch (RepositoryException e) {
      LOGGER
          .error("Could not set some permissions on '" + request.getPathInfo() + "'", e);
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Could not set permissions.");
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
      Node fileNode, String[] privilege, PrincipalManager pm, String key, String property)
      throws RepositoryException {

    // Get all the IDs of the principals that should be added and removed from the
    // request.
    String[] toAdd = request.getParameterValues(key);
    Set<Principal> toAddSet = new HashSet<Principal>();
    String[] toDelete = request.getParameterValues(key + "@Delete");
    Set<Principal> toDeleteSet = new HashSet<Principal>();
    String path = fileNode.getPath();

    // Get the IDs that are set on the members node.
    Node membersNode = fileNode.getNode(POOLED_CONTENT_MEMBERS_NODENAME);
    Value[] vals = membersNode.getProperty(property).getValues();
    List<Value> newValues = Lists.newArrayList(vals);

    // Resolve the principals to IDs.
    resolveNames(pm, toAdd, toAddSet);
    resolveNames(pm, toDelete, toDeleteSet);

    // Give the privileges to the set that should be added.
    for (Principal principal : toAddSet) {
      replaceAccessControlEntry(session, path, principal, privilege, null, null, null);
      addValue(principal.getName(), newValues, session.getValueFactory());
    }

    // Remove the privileges for the people that should be
    // TODO Maybe remove the entire entry instead of just denying the privilege?
    for (Principal principal : toDeleteSet) {
      replaceAccessControlEntry(session, path, principal, null, privilege, null, null);
      removeValue(principal.getName(), newValues);
    }

    // Set the property value under file/members to the new ACL situation.
    membersNode.setProperty(property, newValues.toArray(new Value[newValues.size()]));
  }

  /**
   * Remove a string value from a list of values.
   * 
   * @param name
   *          The string to remove.
   * @param values
   *          The list of values.
   * @throws RepositoryException
   */
  protected void removeValue(String name, List<Value> values) throws RepositoryException {
    Value toRemove = null;
    for (Value value : values) {
      if (value.getString().equals(name)) {
        toRemove = value;
      }
    }
    if (toRemove != null) {
      values.remove(toRemove);
    }
  }

  /**
   * Adds a string to a list of values only if that string isn't in the list of values
   * already.
   * 
   * @param name
   *          The string to add.
   * @param values
   *          A list of values.
   * @param valueFactory
   *          A ValueFactory to create the jcr {@link Value}.
   * @throws RepositoryException
   */
  protected void addValue(String name, List<Value> values, ValueFactory valueFactory)
      throws RepositoryException {
    boolean add = true;
    for (Value v : values) {
      if (v.getString().equals(name)) {
        add = false;
        break;
      }
    }
    if (add) {
      values.add(valueFactory.createValue(name));
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
