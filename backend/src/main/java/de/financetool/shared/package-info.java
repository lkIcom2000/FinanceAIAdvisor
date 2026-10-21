/**
 * Shared module: RequestContext, cross-module value types in {@code shared.domain} and problem codes.
 * Open so that every module may use its sub-packages; it depends on no other module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, allowedDependencies = {})
package de.financetool.shared;

import org.springframework.modulith.ApplicationModule;
