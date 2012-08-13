package org.sakaiproject.nakamura.vivo;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.profile.ProfileService;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

public class VivoProfileService implements ProfileService {

  public String getEmailLocation() {
    // TODO Auto-generated method stub
    return null;
  }

  public ValueMap getProfileMap(Authorizable authorizable, Session session)
      throws RepositoryException, StorageClientException, AccessDeniedException {
    // TODO Auto-generated method stub
    return null;
  }

  public ValueMap getProfileMap(
      org.apache.jackrabbit.api.security.user.Authorizable authorizable, Session session)
      throws RepositoryException {
    // TODO Auto-generated method stub
    return null;
  }

  public ValueMap getProfileMap(Content profileContent, Session session)
      throws RepositoryException {
    // TODO Auto-generated method stub
    return null;
  }

  public ValueMap getCompactProfileMap(Authorizable authorizable, Session session)
      throws RepositoryException, StorageClientException, AccessDeniedException {
    // TODO Auto-generated method stub
    return null;
  }

  public ValueMap getCompactProfileMap(
      org.apache.jackrabbit.api.security.user.Authorizable authorizable, Session session)
      throws RepositoryException {
    // TODO Auto-generated method stub
    return null;
  }

  public void update(org.sakaiproject.nakamura.api.lite.Session session,
      String profilePath, JSONObject json, boolean replace, boolean replaceProperties,
      boolean removeTree) throws StorageClientException, AccessDeniedException,
      JSONException {
    // TODO Auto-generated method stub

  }

}
