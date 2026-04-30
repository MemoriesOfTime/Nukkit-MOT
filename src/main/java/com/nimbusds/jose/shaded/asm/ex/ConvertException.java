package com.nimbusds.jose.shaded.asm.ex;

/**
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */
@Deprecated(forRemoval = true)
public class ConvertException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ConvertException() {
		super();
	}

	public ConvertException(String message) {
		super(message);
	}

}
