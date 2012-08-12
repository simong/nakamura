package org.sakaiproject.nakamura;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import org.junit.Test;
import org.sakaiproject.nakamura.vivo.rdf.RDFToMap;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;

public class RDFToMapTest {

  
  
  @Test
  public void testSimon() throws XMLStreamException {
    InputStream in = getClass().getResourceAsStream("/simon.rdf");
    
    /*
    Map<String, String> prefixMap = new HashMap<String, String>();
    prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    prefixMap.put("vivocore", "http://vivoweb.org/ontology/core#");
    prefixMap.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
    prefixMap.put("vitro", "http://vitro.mannlib.cornell.edu/ns/vitro/0.7#");
    prefixMap.put("foaf", "http://xmlns.com/foaf/0.1/");
    prefixMap.put("owl", "http://www.w3.org/2002/07/owl#");
    */
    Builder<String, String> b = ImmutableMap.builder();
    b.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
    b.put("http://vivoweb.org/ontology/core#", "vivocore");
    b.put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
    b.put("http://vitro.mannlib.cornell.edu/ns/vitro/0.7#","vitro");
    b.put("http://xmlns.com/foaf/0.1/","foaf");
    b.put("http://www.w3.org/2002/07/owl#", "owl");
    RDFToMap map = new RDFToMap(b.build());
    map = map.readMap(new InputStreamReader(in)).resolveToFullJson();
    
    System.out.println(map.toJson(true));
    
    Map<String, Object> originJson = map.toMap();
    
    Object d = originJson.get("_default");
    
    System.out.println(originJson.get("foaf_firstName"));
    
    for (Entry<String, Object> entry : originJson.entrySet()) {
      System.out.println(entry.getKey());
    }
    
  }
  
}
