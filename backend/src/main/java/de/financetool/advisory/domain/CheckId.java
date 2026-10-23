package de.financetool.advisory.domain;

/**
 * The closed set of checks in the Check Chain (AD-6). Each has a plain description for the customer; the id itself
 * appears only as a small caption. The Experiment Track's {@code FALSE_PREMISE} and {@code ROLE_INTEGRITY} are not
 * part of the set: adding them needs a spine change of AD-6.
 */
public enum CheckId {
    SCOPE("The answer stays within what this service covers"),
    PROFILE_COMPLETE("No personal verdict without enough information about you"),
    SUITABILITY("The recommendation refers to your situation and names the risks"),
    COST_TRANSPARENCY("The costs are mentioned"),
    NO_PRODUCT_NAMING("No single product, provider or ISIN is pushed"),
    NO_GUARANTEE_OR_TIMING("No promised returns and no advice to buy or sell because of expected price moves"),
    CONFLICT_OF_INTEREST("No preference for the bank's own products or commissions"),
    IDD_NEEDS("Insurance stays at the level of your needs, without naming insurers or tariffs"),
    DOCUMENTATION("The basis of the recommendation is documented"),
    NUMBER_MATCH("Every number matches the calculation"),
    SOURCE_VALIDITY("The explanation is based on valid, cited sources");

    private final String description;

    CheckId(String description) {
        this.description = description;
    }

    /** The plain description shown to the customer. */
    public String description() {
        return description;
    }
}
