package org.sakaiproject.nakamura.vivo.rdf;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ResourceSerializer implements JsonSerializer<Resource> {

	public JsonElement serialize(Resource resource, Type tupeOfSrc,
			JsonSerializationContext context) {
		return new JsonPrimitive(resource.toString());
	}

}
