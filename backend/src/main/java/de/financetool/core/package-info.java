/**
 * Deterministic Core and the CoreGateway.
 *
 * <p>The types the gateway returns live in {@code core.domain} (the domain functions build them) and form the named
 * interface {@code results}; a caller allows {@code "core :: results"} next to {@code "core"}. No other domain type is
 * exposed, so the Core's functions stay behind the gateway (AD-2).
 */
@ApplicationModule(allowedDependencies = { "bank", "knowledge", "audit", "documents", "shared" })
package de.financetool.core;

import org.springframework.modulith.ApplicationModule;
