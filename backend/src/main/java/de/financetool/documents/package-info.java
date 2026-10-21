/**
 * Document intake pipeline, redaction, grounding, letter templates and DocumentFactsPort.
 */
@ApplicationModule(allowedDependencies = { "shared", "llm", "audit", "personas" })
package de.financetool.documents;

import org.springframework.modulith.ApplicationModule;
