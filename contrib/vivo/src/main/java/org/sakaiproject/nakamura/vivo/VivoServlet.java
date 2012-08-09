package org.sakaiproject.nakamura.vivo;

import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import java.io.IOException;

import javax.servlet.ServletException;

@SlingServlet(paths = { "/system/vivo/test" }, methods = { "GET" })
public class VivoServlet extends SlingSafeMethodsServlet {

  /**
   * 
   */
  private static final long serialVersionUID = -8730787128409164766L;
  
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    
    
    VitroRequest vreq = new VitroRequest(request);
    
    
  }

}
