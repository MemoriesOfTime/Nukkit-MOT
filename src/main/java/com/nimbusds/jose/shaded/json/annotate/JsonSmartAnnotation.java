package com.nimbusds.jose.shaded.json.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson Annotation like
 * 
 * @author uriel
 *
 *
 * @deprecated This compatibility type exists only for legacy plugins that referenced
 * Nimbus' former shaded json-smart classes and may be removed in a future release.
 * Plugins should bundle their own JSON library or migrate to a supported JSON API.
 */

@Target({ ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Deprecated(forRemoval = true)
public @interface JsonSmartAnnotation {

}
