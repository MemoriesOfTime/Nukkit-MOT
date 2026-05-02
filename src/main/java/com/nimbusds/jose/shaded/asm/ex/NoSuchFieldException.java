package com.nimbusds.jose.shaded.asm.ex;

/**
 * Same exception as java.lang.NoSuchFieldException but extends RuntimException
 * 
 * @author uriel
 *
 *
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public class NoSuchFieldException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public NoSuchFieldException() {
		super();
	}

	public NoSuchFieldException(String message) {
		super(message);
	}
}
