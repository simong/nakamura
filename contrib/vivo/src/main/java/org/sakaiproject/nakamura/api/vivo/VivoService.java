package org.sakaiproject.nakamura.api.vivo;

import org.apache.sling.commons.json.JSONArray;
import org.sakaiproject.nakamura.vivo.VivoException;

public interface VivoService {

  public JSONArray search(String query, String type) throws VivoException;

}
