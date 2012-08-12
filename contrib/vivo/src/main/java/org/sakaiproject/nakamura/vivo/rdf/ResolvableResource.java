package org.sakaiproject.nakamura.vivo.rdf;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class ResolvableResource implements Resource {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResolvableResource.class);
	private String value;
	private String label;
	private Map<String, Object> resolverConfig;
	private Map<String, Object> target;
	public ResolvableResource(String value, Map<String, Object> resolverConfig) {
		this.value = value;
		this.resolverConfig = resolverConfig;
		LOGGER.info("Resolvable Resource Created for {} ",value);
	}
	
	
	public synchronized String getLabel() throws IOException {
		if ( label == null ) {
			Map<String, Object> tm = getTarget();
			if ( tm != null ) {
				label = (String) tm.get("rdfs_label");
			}
		}
		LOGGER.info("Got Lable as {} ",label);
		return label;
	}
	
	public synchronized Map<String, Object> getTarget() throws IOException {
		if ( target == null ) {
			Resolver resolver = ResolverHolder.get();
			if ( resolver == null ) {
				LOGGER.info("Resolver was null");
				return ImmutableMap.of("rdfs_label",(Object)(" No Resolver for  "+value));
			} else {
				target = resolver.get(value, resolverConfig);
			}
		}
		return target;
	}
	
	public String getKey() {
		return value;
	}

	public boolean hasLabelAndKey() {
		LOGGER.info("Checking Label and Key on {} ",value);
		return true;
	}

	public boolean isReference() {
		LOGGER.info("Checking Reference and Key on {} ",value);
		return true;
	}
	
	@Override
	public String toString() {
		return value;
	}

}
