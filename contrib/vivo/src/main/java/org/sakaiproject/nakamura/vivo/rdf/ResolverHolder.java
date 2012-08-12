package org.sakaiproject.nakamura.vivo.rdf;

public class ResolverHolder {

	private static ThreadLocal<Resolver> holder = new ThreadLocal<Resolver>();

	public static Resolver get() {
		return holder.get();
	}
	
	public static void set(Resolver resolver) {
		holder.set(resolver);
	}

	public static void clear() {
		holder.set(null);
	}

}
