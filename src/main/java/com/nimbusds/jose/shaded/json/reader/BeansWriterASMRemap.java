package com.nimbusds.jose.shaded.json.reader;

import com.nimbusds.jose.shaded.asm.Accessor;
import com.nimbusds.jose.shaded.asm.BeansAccess;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.shaded.json.JSONStyle;
import com.nimbusds.jose.shaded.json.JSONUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BeansWriterASMRemap implements JsonWriterI<Object> {
	private Map<String, String> rename = new HashMap<String, String>();

	public void renameField(String source, String dest) {
		rename.put(source, dest);
	}

	private String rename(String key) {
		String k2 = rename.get(key);
		if (k2 != null)
			return k2;
		return key;
	}

	public <E> void writeJSONString(E value, Appendable out, JSONStyle compression) throws IOException {
		try {
			Class<?> cls = value.getClass();
			boolean needSep = false;
			@SuppressWarnings("rawtypes")
			BeansAccess fields = BeansAccess.get(cls, JSONUtil.JSON_SMART_FIELD_FILTER);
			out.append('{');
			for (Accessor field : fields.getAccessors()) {
				@SuppressWarnings("unchecked")
				Object v = fields.get(value, field.getIndex());
				if (v == null && compression.ignoreNull())
					continue;
				if (needSep)
					out.append(',');
				else
					needSep = true;
				String key = field.getName();
				key = rename(key);
				JSONObject.writeJSONKV(key, v, out, compression);
			}
			out.append('}');
		} catch (IOException e) {
			throw e;
		}
	}

}
