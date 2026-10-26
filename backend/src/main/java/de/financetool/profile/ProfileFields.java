package de.financetool.profile;

import java.util.Set;

/**
 * The profile fields a persona may store ({@code PROFILE_FIELDS} of {@code experiments/finlab/profile.py}) and the
 * table each belongs to: the Needs Profile holds the protection facts (IDD), the Suitability Profile all others.
 */
public final class ProfileFields {

    public static final Set<String> NEEDS = Set.of("occupation", "household", "dependants", "existing_contracts",
            "existing_contract_details");

    public static final Set<String> SUITABILITY = Set.of("age", "employment_status", "net_income", "gross_income",
            "monthly_expenses", "monthly_surplus", "emergency_fund", "current_balance", "risk_tolerance",
            "loss_aversion", "loss_bearing_capacity", "investment_horizon_years", "goal", "knowledge_experience",
            "sustainability_preference", "current_holdings", "tax_allowance_unused", "pension_situation", "children",
            "amount", "requested_monthly_amount");

    /** The recommendation topics answered from the Needs Profile; the strict FR-17 fields do not apply to them. */
    public static final Set<String> NEEDS_TOPICS = Set.of("protection_needs", "protection_duplicate");

    private ProfileFields() {
    }

    public static boolean known(String field) {
        return NEEDS.contains(field) || SUITABILITY.contains(field);
    }
}
