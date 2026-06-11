package com.nimbusds.jose.shaded.json.reader;

import com.nimbusds.jose.shaded.json.JSONStyle;
import com.nimbusds.jose.shaded.json.JSONUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public class BeansWriter implements JsonWriterI<Object> {
	public <E> void writeJSONString(E value, Appendable out, JSONStyle compression) throws IOException {
		try {
			Class<?> nextClass = value.getClass();
			boolean needSep = false;
			compression.objectStart(out);
			while (nextClass != Object.class) {
				Field[] fields = nextClass.getDeclaredFields();
				for (Field field : fields) {
					int m = field.getModifiers();
					if ((m & (Modifier.STATIC | Modifier.TRANSIENT | Modifier.FINAL)) > 0)
						continue;
					Object v = null;
					if ((m & Modifier.PUBLIC) > 0) {
						v = field.get(value);
					} else {
						String g = JSONUtil.getGetterName(field.getName());
						Method mtd = null;

						try {
							mtd = nextClass.getDeclaredMethod(g);
						} catch (Exception e) {
						}
						if (mtd == null) {
							Class<?> c2 = field.getType();
							if (c2 == Boolean.TYPE || c2 == Boolean.class) {
								g = JSONUtil.getIsName(field.getName());
								mtd = nextClass.getDeclaredMethod(g);
							}
						}
						if (mtd == null)
							continue;
						v = mtd.invoke(value);
					}
					if (v == null && compression.ignoreNull())
						continue;
					if (needSep)
						compression.objectNext(out);
					else
						needSep = true;
					String key = field.getName();

					JsonWriter.writeJSONKV(key, v, out, compression);
					// compression.objectElmStop(out);
				}
				nextClass = nextClass.getSuperclass();
			}
			compression.objectStop(out);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
