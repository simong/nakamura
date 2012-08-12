package org.sakaiproject.nakamura.vivo.rdf;

public class NonResolvableResource implements Resource {

	private String value;
	public NonResolvableResource(String value) {
		this.value = value;
	}
	public boolean isReference() {
		return true;
	}
	@Override
	public String toString() {
		return value;
	}


}
