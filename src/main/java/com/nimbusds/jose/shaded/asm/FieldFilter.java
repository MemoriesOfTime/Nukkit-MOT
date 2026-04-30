package com.nimbusds.jose.shaded.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * allow to control read/write access to field
 * 
 *
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public interface FieldFilter {
	/**
	 * NOT Implemented YET
	 * 
	 * @param field the field
	 * @return boolean
	 */
	public boolean canUse(Field field);

	/**
	 * 
	 * @param field the field
	 * @param method the method
	 * @return boolean
	 */
	public boolean canUse(Field field, Method method);

	/**
	 * NOT Implemented YET
	 * 
	 * @param field the field
	 * @return boolean
	 */
	public boolean canRead(Field field);

	/**
	 * NOT Implemented YET
	 * 
	 * @param field the field
	 * @return boolean
	 */
	public boolean canWrite(Field field);
}
