package com.nimbusds.jose.shaded.json.reader;

import com.nimbusds.jose.shaded.json.JSONStyle;
import com.nimbusds.jose.shaded.json.JSONValue;

import java.io.IOException;

/**
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public class ArrayWriter implements JsonWriterI<Object> {
	public <E> void writeJSONString(E value, Appendable out, JSONStyle compression) throws IOException {
		compression.arrayStart(out);
		boolean needSep = false;
		for (Object o : ((Object[]) value)) {
			if (needSep)
				compression.objectNext(out);
			else
				needSep = true;
			JSONValue.writeJSONString(o, out, compression);
		}
		compression.arrayStop(out);
	}
}
