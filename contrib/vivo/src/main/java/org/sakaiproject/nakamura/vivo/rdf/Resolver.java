package org.sakaiproject.nakamura.vivo.rdf;

import java.io.IOException;
import java.util.Map;

public interface Resolver {

	Map<String, Object> get(String key, Map<String, Object> resolverConfig) throws IOException;

}
