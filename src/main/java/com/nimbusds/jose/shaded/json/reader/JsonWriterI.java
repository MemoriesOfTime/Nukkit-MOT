package com.nimbusds.jose.shaded.json.reader;

import com.nimbusds.jose.shaded.json.JSONStyle;

import java.io.IOException;

/**
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public interface JsonWriterI<T> {
	public <E extends T> void writeJSONString(E value, Appendable out, JSONStyle compression) throws IOException;
}
