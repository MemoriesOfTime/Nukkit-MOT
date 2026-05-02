package com.nimbusds.jose.shaded.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public class BasicFiledFilter implements FieldFilter {
	public final static BasicFiledFilter SINGLETON = new BasicFiledFilter();

	@Override
	public boolean canUse(Field field) {
		return true;
	}

	@Override
	public boolean canUse(Field field, Method method) {
		return true;
	}

	@Override
	public boolean canRead(Field field) {
		return true;
	}

	@Override
	public boolean canWrite(Field field) {
		return true;
	}

}
