package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * Core function {@code demandsAndNeeds}: the short IDD demands-and-needs statement (FR-18) for liability,
 * occupational disability and Duplicate Coverage. Its only inputs are the Needs Profile (household, dependants,
 * occupation) and the insurance contracts from the banking data; it selects no product and names no insurer or
 * tariff (a contract is cited by its cover, policyholder and role, never its provider). Pure.
 */
public final class DemandsAndNeeds {

    public static final String FUNCTION = "demandsAndNeeds";
    public static final String OUTPUT_LIABILITY = "liabilityContracts";
    public static final String OUTPUT_DISABILITY = "disabilityContracts";
    public static final String OUTPUT_DUPLICATES = "duplicates";
    /** The Needs Profile facts the statement needs, in reading order. */
    public static final List<String> FIELDS = List.of("household", "dependants", "occupation");
    /** The {@code household} choice that means no own household. */
    static final String WITH_PARENTS = "parents";

    /**
     * One area of the statement.
     *
     * @param area the area in plain words
     * @param need why the cover matters, for this persona
     * @param have what the banking data shows
     * @param check what to check, and with whom
     */
    @NamedInterface("results")
    public record Area(String area, String need, String have, String check) {

        public Area {
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(need, "need");
            Objects.requireNonNull(have, "have");
            Objects.requireNonNull(check, "check");
        }
    }

    /**
     * The non-numeric result.
     *
     * @param areas liability, occupational disability and Duplicate Coverage, in this order
     * @param profileFields the Needs Profile fields the statement read and found
     */
    @NamedInterface("results")
    public record Statement(List<Area> areas, List<String> profileFields) {

        public Statement {
            areas = List.copyOf(areas);
            profileFields = List.copyOf(profileFields);
        }

        /** Whether household, dependants and occupation were all known when the statement was made. */
        public boolean factsCollected() {
            return profileFields.containsAll(FIELDS);
        }
    }

    /** The computation for the gateway and the statement for the turn. */
    public record Result(CoreComputation computation, Statement statement) {
    }

    private DemandsAndNeeds() {
    }

    /**
     * @param needsProfile profile field → value; only {@link #FIELDS} are read
     * @param contracts the contracts on which the persona is policyholder or co-insured
     * @param personaId the persona the contracts cover, part of the key inputs
     */
    public static Result of(Map<String, ?> needsProfile, List<InsuranceContract> contracts, String personaId) {
        Objects.requireNonNull(needsProfile, "needsProfile");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(personaId, "personaId");
        Map<String, Object> read = new LinkedHashMap<>();
        for (String field : FIELDS) {
            Object value = needsProfile.get(field);
            if (field.equals("dependants")) {
                // a count that is no whole number of zero or more is not known
                BigDecimal count = CategorySelection.decimal(value);
                if (count != null && count.signum() >= 0 && count.stripTrailingZeros().scale() <= 0) {
                    read.put(field, count.stripTrailingZeros().toPlainString());
                }
            } else if (value != null && !value.toString().isBlank()) {
                read.put(field, field.equals("household") ? value.toString().strip().toLowerCase(Locale.ROOT)
                        : value.toString().strip());
            }
        }
        String household = (String) read.get("household");
        boolean ownHousehold = household != null && !household.equals(WITH_PARENTS);
        BigDecimal dependants = CategorySelection.decimal(read.get("dependants"));

        List<InsuranceContract> liability = covering(contracts, InsuranceContract.Cover.LIABILITY);
        List<InsuranceContract> disability = covering(contracts, InsuranceContract.Cover.OCCUPATIONAL_DISABILITY);
        DuplicateCoverage.Result duplicates = DuplicateCoverage.detect(contracts, personaId);

        List<Area> areas = List.of(
                liabilityArea(liability, household, ownHousehold),
                disabilityArea(disability, dependants),
                duplicateArea(duplicates.flags(), contracts));

        List<Figure> outputs = List.of(
                new Figure(OUTPUT_LIABILITY, "Contracts covering liability", BigDecimal.valueOf(liability.size()),
                        Unit.COUNT),
                new Figure(OUTPUT_DISABILITY, "Contracts covering occupational disability",
                        BigDecimal.valueOf(disability.size()), Unit.COUNT),
                new Figure(OUTPUT_DUPLICATES, "Risks covered more than once",
                        BigDecimal.valueOf(duplicates.flags().size()), Unit.COUNT));
        List<FigureInput> inputs = new ArrayList<>();
        read.forEach((field, value) -> inputs.add(FigureInput.text(field, value.toString())));
        contracts.stream().filter(c -> c.cover().insurance())
                .forEach(c -> inputs.add(FigureInput.text("Contract", c.citation())));
        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("persona", personaId);
        keyInputs.put("profile", read);
        keyInputs.put("contracts", contracts.stream()
                .map(c -> (Object) (c.id() + ":" + c.cover() + ":" + (c.coInsured() ? "co-insured" : "policyholder")))
                .toList());
        Statement statement = new Statement(areas, List.copyOf(read.keySet()));
        CoreComputation computation = new CoreComputation(FUNCTION, "demands-and-needs statement",
                "Based only on your household, dependants and occupation and the insurance contracts in your banking"
                        + " data; no product, insurer or tariff is an input.",
                keyInputs, inputs, outputs,
                List.of("Needs level only: no product, insurer or tariff is selected.",
                        "Fine knows only the contracts in your banking data; cover you hold elsewhere is not"
                                + " included.",
                        "Fine compares the kind of cover only, not the conditions or limits of each contract."),
                Map.of());
        return new Result(computation, statement);
    }

    private static List<InsuranceContract> covering(List<InsuranceContract> contracts, InsuranceContract.Cover cover) {
        return contracts.stream().filter(c -> c.cover() == cover).toList();
    }

    private static Area liabilityArea(List<InsuranceContract> contracts, String household, boolean ownHousehold) {
        String need = "Liability cover pays for damage you cause to others, which can run into amounts no one can"
                + " pay from savings. Everyone needs it.";
        List<InsuranceContract> own = contracts.stream().filter(c -> !c.coInsured()).toList();
        List<InsuranceContract> family = contracts.stream().filter(InsuranceContract::coInsured).toList();
        List<String> parts = new ArrayList<>();
        if (!own.isEmpty()) {
            parts.add(own.size() == 1 ? "your own policy" : own.size() + " policies of your own");
        }
        family.forEach(c -> parts.add("you're co-insured on the policy of " + c.policyholder()));
        String have = parts.isEmpty() ? "No liability contract in your banking data."
                : capitalized(String.join(", and ", parts)) + ".";
        String check;
        if (contracts.isEmpty()) {
            check = "Check whether you have liability cover elsewhere; if not, this is the first cover to look into.";
        } else if (!family.isEmpty() && household == null) {
            check = "Whether the family policy still covers you depends on your household; check its conditions"
                    + " with " + holders(family) + ".";
        } else if (!family.isEmpty() && ownHousehold) {
            check = "It's likely the family policy no longer covers you once you have your own household. Check with "
                    + holders(family) + (own.isEmpty() ? ", and look into cover of your own."
                    : ", and note the cancellation period of your own policy before changing anything.");
        } else if (!family.isEmpty()) {
            check = "While you live with " + holders(family) + ", their family policy may cover you too. Check its"
                    + " conditions with them"
                    + (own.isEmpty() ? "." : ", and note the cancellation period of your own policy before cancelling"
                    + " anything.");
        } else {
            check = "Check that the sum insured still fits your situation.";
        }
        return new Area("Liability", need, have, check);
    }

    private static Area disabilityArea(List<InsuranceContract> contracts, BigDecimal dependants) {
        String need = "If illness or an accident stops you from working, occupational disability cover replaces part"
                + " of your income.";
        if (dependants != null && dependants.signum() > 0) {
            need += " This matters even more because " + dependants.toPlainString()
                    + (dependants.compareTo(BigDecimal.ONE) == 0 ? " person depends" : " people depend")
                    + " on your income.";
        }
        String have = contracts.isEmpty() ? "No occupational disability contract in your banking data."
                : contracts.size() == 1 ? "One occupational disability contract."
                : contracts.size() + " occupational disability contracts.";
        String check = contracts.isEmpty()
                ? "Check whether you have this cover elsewhere, for example through your employer; if not, look into"
                        + " whether you need it."
                : "Check that the insured monthly benefit still fits your income.";
        return new Area("Occupational disability", need, have, check);
    }

    private static Area duplicateArea(List<CoverageFlag> flags, List<InsuranceContract> contracts) {
        String need = "Paying twice for the same cover costs money without protecting you more.";
        if (flags.isEmpty()) {
            return new Area("Cover held twice", need, "No risk is covered by more than one of your contracts.",
                    "Nothing to check here.");
        }
        List<String> parts = new ArrayList<>();
        for (CoverageFlag flag : flags) {
            List<String> holders = contracts.stream().filter(c -> flag.contractIds().contains(c.id()))
                    .map(c -> c.coInsured() ? "the policy of " + c.policyholder() : "your own policy").toList();
            parts.add(capitalized(flag.cover().plainWords()) + " is covered by " + holders.size() + " contracts: "
                    + String.join(" and ", holders) + ".");
        }
        return new Area("Cover held twice", need, String.join(" ", parts),
                "Before cancelling anything, check which contract really covers you and its cancellation period.");
    }

    /** "A", "A and B", "A, B and C". */
    private static String holders(List<InsuranceContract> contracts) {
        List<String> names = contracts.stream().map(InsuranceContract::policyholder).distinct().toList();
        return names.size() < 2 ? String.join("", names)
                : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
