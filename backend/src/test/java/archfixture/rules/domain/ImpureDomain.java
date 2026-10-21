package archfixture.rules.domain;

import org.springframework.util.Assert;

/** Deliberately broken: a domain class that uses a framework type (AD-1). */
public class ImpureDomain {

    public void check(Object value) {
        Assert.notNull(value, "value");
    }
}
