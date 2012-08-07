package org.sakaiproject.nakamura;

import org.apache.sling.api.resource.ValueMap;
import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.vivo.VivoProfile;
import org.sakaiproject.nakamura.vivo.VivoProfileException;

import java.io.InputStream;

public class VivoProfileTest {

  @Test
  public void testProfile() throws VivoProfileException {
    // Get and parse our profile
    InputStream in = getClass().getResourceAsStream("/simon.rdf");
    VivoProfile vp = new VivoProfile(in, "n516");
    ValueMap profile = vp.asCompactValueMap();

    Assert.assertEquals("Simon", profile.get("firstName"));
    Assert.assertEquals("Gaeremynck", profile.get("lastName"));
    Assert.assertEquals("gaeremyncks@gmail.com", profile.get("primaryEmail"));
  }
}
