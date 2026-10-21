/**
 * Deterministic Core and the CoreGateway.
 */
@ApplicationModule(allowedDependencies = { "bank", "knowledge", "audit", "documents", "shared" })
package de.financetool.core;

import org.springframework.modulith.ApplicationModule;
