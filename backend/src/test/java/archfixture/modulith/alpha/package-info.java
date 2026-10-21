/**
 * Deliberately broken fixture module: declares no allowed dependencies but uses {@code beta}.
 */
@ApplicationModule(allowedDependencies = {})
package archfixture.modulith.alpha;

import org.springframework.modulith.ApplicationModule;
