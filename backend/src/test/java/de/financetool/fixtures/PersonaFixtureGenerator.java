package de.financetool.fixtures;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Generates the Synthetic Persona fixtures in {@code data/personas/} (FR-51), deterministically from a fixed seed
 * per persona ({@code L64X128MixRandom}). {@link PersonaFixturesGeneratedTest} regenerates them on every build and
 * compares bytes; {@code -Dfine.fixtures.write=true} rewrites the files.
 *
 * <p>Calibration (spec Story 2.1, Design Notes): recurring flows ({@code recurrenceId}) are booked on the same day
 * each month, premiums ({@code contractId}) on the contract's due dates, and variable spending (everything else on
 * the current account except transfers between own accounts) adds up to exactly V in every complete calendar month
 * and pro rata in the month of the simulated date, with at most 4 days between two variable bookings. The current
 * account's opening balance is back-computed so that its balance at the end of the simulated date is the calibrated
 * one; it must not be negative.
 */
final class PersonaFixtureGenerator {

    static final int GENERATOR_VERSION = 1;
    static final LocalDate HISTORY_START = LocalDate.of(2025, 7, 1);
    static final Path DIRECTORY = Path.of("..", "data", "personas");

    private static final String CURRENT = "current";
    private static final String OVERNIGHT = "overnight";
    private static final String DEPOT = "depot";

    record AccountSpec(String key, String type, String name, long openingCents) {
    }

    /** A monthly flow on the current account; {@code toAccount} adds the credit leg of a standing order. */
    record Recurring(String id, int day, long cents, String category, String counterparty, String reference,
            String toAccount) {
    }

    record ContractSpec(String key, String coverage, String provider, String policyNumber, String policyholder,
            String insuredRole, Long premiumCents, String interval, LocalDate nextDueDate, LocalDate startDate,
            String category, String reference) {
    }

    record OneOffTransfer(LocalDate date, long cents, String toAccount, String reference) {
    }

    record SavingsPlanSpec(String key, String name, String assetClass, long cents, int day, LocalDate startDate,
            String recurrenceId) {
    }

    record HoldingSpec(String key, String name, String assetClass, String units, String unitPrice, String costBasis) {
    }

    record GroundTruth(String id, String description, LocalDate date, String expectedBalance,
            String premiumOverride) {
    }

    record PersonaSpec(String id, String description, long seed, LocalDate simulatedDate, long currentBalanceCents,
            List<AccountSpec> accounts, List<Recurring> recurring, long variableMonthlyCents,
            List<ContractSpec> contracts, List<OneOffTransfer> oneOffTransfers, List<SavingsPlanSpec> savingsPlans,
            List<HoldingSpec> holdings, List<GroundTruth> groundTruth) {
    }

    private record VariableKind(String category, String counterparty, String reference, int weight) {
    }

    private static final List<VariableKind> VARIABLE_KINDS = List.of(
            new VariableKind("GROCERIES", "Corner Grocery", "Card payment", 6),
            new VariableKind("GROCERIES", "Fresh Market", "Card payment", 5),
            new VariableKind("DINING", "Example Bistro", "Card payment", 2),
            new VariableKind("DINING", "Example Bakery", "Card payment", 2),
            new VariableKind("SHOPPING", "Example Department Store", "Card payment", 2),
            new VariableKind("SHOPPING", "Example Online Shop", "Online purchase", 2),
            new VariableKind("LEISURE", "Example Cinema", "Card payment", 1),
            new VariableKind("HEALTH", "City Pharmacy", "Card payment", 1),
            new VariableKind("MOBILITY", "Example Fuel Station", "Card payment", 1),
            new VariableKind("CASH_WITHDRAWAL", "Cash machine", "Cash withdrawal", 2));

    private PersonaFixtureGenerator() {
    }

    /** Persona id to fixture JSON (LF line endings, trailing newline), sorted by persona id. */
    static Map<String, String> generateAll() {
        Map<String, String> files = new TreeMap<>();
        for (PersonaSpec spec : personas()) {
            files.put(spec.id(), new Generation(spec).json());
        }
        return files;
    }

    // ------------------------------------------------------------------------------------------- persona calibration

    static List<PersonaSpec> personas() {
        LocalDate oct15 = LocalDate.of(2026, 10, 15);
        return List.of(
                new PersonaSpec("p-lena",
                        "Lena, 29, nurse, 2,400 EUR net. Pays her car insurance of 620 EUR annually on 1 Nov, which"
                                + " takes her current account below zero before the next salary (UJ-1, UJ-7).",
                        2026_1015_01L, oct15, 1974_00,
                        List.of(current(), new AccountSpec(OVERNIGHT, "OVERNIGHT", "Overnight deposit", 400_00)),
                        List.of(
                                new Recurring("salary", 3, 2400_00, "SALARY", "Example Hospital", "Salary", null),
                                rent(1, -850_00),
                                ticket(),
                                new Recurring("standing-order-overnight", 4, -300_00, "TRANSFER",
                                        "Overnight deposit", "Standing order to overnight deposit", OVERNIGHT),
                                electricity(5, -65_00),
                                streaming(12, -13_00),
                                phone(20, -25_00)),
                        930_00,
                        List.of(
                                new ContractSpec("car", "CAR", "Example Motor Insurance", "EX-CAR-1048-2231", "Lena",
                                        "POLICYHOLDER", 620_00L, "ANNUAL", LocalDate.of(2026, 11, 1),
                                        LocalDate.of(2021, 11, 1), "INSURANCE", "Car insurance annual premium"),
                                new ContractSpec("liability", "LIABILITY", "Example Mutual Insurance",
                                        "EX-LIA-5521-0907", "Lena", "POLICYHOLDER", 62_00L, "ANNUAL",
                                        LocalDate.of(2027, 4, 1), LocalDate.of(2020, 4, 1), "INSURANCE",
                                        "Private liability insurance annual premium")),
                        List.of(new OneOffTransfer(LocalDate.of(2026, 2, 16), 500_00, OVERNIGHT,
                                "Transfer to overnight deposit")),
                        List.of(), List.of(),
                        List.of(
                                new GroundTruth("uj1-car-premium-shortfall",
                                        "UJ-1: the annual car insurance premium of 620 EUR due on 1 Nov 2026 takes"
                                                + " the forecast current-account balance to -90 EUR.",
                                        LocalDate.of(2026, 11, 1), "-90.00", null),
                                new GroundTruth("uj7-premium-increase-shortfall",
                                        "UJ-7: with the increased premium of 744 EUR from the insurer's letter the"
                                                + " forecast balance on 1 Nov 2026 drops to -214 EUR.",
                                        LocalDate.of(2026, 11, 1), "-214.00", "744.00"))),
                new PersonaSpec("p-marco",
                        "Marco, 34, warehouse worker. 2,100 EUR income, 2,000 EUR monthly expenses, 300 EUR emergency"
                                + " fund in his overnight deposit; about 100 EUR monthly surplus (UJ-2).",
                        2026_1015_02L, oct15, 2300_00,
                        List.of(current(), new AccountSpec(OVERNIGHT, "OVERNIGHT", "Overnight deposit", 300_00)),
                        List.of(
                                new Recurring("salary", 1, 2100_00, "SALARY", "Example Logistics GmbH", "Salary",
                                        null),
                                rent(1, -750_00),
                                ticket(),
                                electricity(5, -70_00),
                                streaming(12, -12_00),
                                phone(15, -40_00)),
                        1070_00, List.of(), List.of(), List.of(), List.of(), List.of()),
                new PersonaSpec("p-jonas",
                        "Jonas, 26, in his first job. Has his own private liability policy and is also co-insured on"
                                + " his parents' family liability policy (Duplicate Coverage); saves 100 EUR a month"
                                + " in a world equity ETF savings plan (UJ-3, UJ-4).",
                        2026_1015_03L, oct15, 1850_00,
                        List.of(current(), new AccountSpec(OVERNIGHT, "OVERNIGHT", "Overnight deposit", 1200_00),
                                new AccountSpec(DEPOT, "DEPOT", "Depot", 0)),
                        List.of(
                                new Recurring("salary", 1, 2150_00, "SALARY", "Example Engineering GmbH", "Salary",
                                        null),
                                rent(1, -680_00),
                                ticket(),
                                new Recurring("standing-order-overnight", 2, -200_00, "TRANSFER",
                                        "Overnight deposit", "Standing order to overnight deposit", OVERNIGHT),
                                new Recurring("gym", 3, -30_00, "LEISURE", "Example Fitness Club",
                                        "Membership fee", null),
                                electricity(5, -55_00),
                                phone(10, -30_00),
                                streaming(14, -13_00)),
                        900_00,
                        List.of(
                                new ContractSpec("liability", "LIABILITY", "Example Mutual Insurance",
                                        "EX-LIA-7310-4415", "Jonas", "POLICYHOLDER", 48_00L, "ANNUAL",
                                        LocalDate.of(2027, 2, 1), LocalDate.of(2024, 2, 1), "INSURANCE",
                                        "Private liability insurance annual premium"),
                                new ContractSpec("family-liability", "LIABILITY", "Example Family Insurance",
                                        "EX-FAM-2208-1963", "Parents of Jonas", "CO_INSURED", null, null, null,
                                        LocalDate.of(2008, 5, 1), null, null)),
                        List.of(),
                        List.of(new SavingsPlanSpec("etf-plan", "World equity ETF savings plan", "EQUITY_ETF",
                                100_00, 15, LocalDate.of(2025, 4, 15), "etf-savings-plan")),
                        List.of(new HoldingSpec("etf", "World equity ETF", "EQUITY_ETF", "17.842300", "112.36",
                                "1900.00")),
                        List.of()),
                new PersonaSpec("p-petra",
                        "Petra, 41, part-time clerk with two children. Pays 45 EUR a month into an existing Riester"
                                + " pension contract and receives child benefit for both children (UJ-5).",
                        2026_1015_04L, oct15, 1450_00,
                        List.of(current(), new AccountSpec(OVERNIGHT, "OVERNIGHT", "Overnight deposit", 2100_00)),
                        List.of(
                                new Recurring("salary", 1, 1850_00, "SALARY", "Example Municipal Office", "Salary",
                                        null),
                                rent(1, -920_00),
                                ticket(),
                                new Recurring("childcare", 1, -120_00, "CHILDCARE", "Example Daycare Centre",
                                        "Daycare fee", null),
                                electricity(5, -110_00),
                                phone(8, -45_00),
                                new Recurring("child-benefit-1", 10, 259_00, "CHILD_BENEFIT",
                                        "Family Benefits Office", "Child benefit, first child", null),
                                new Recurring("child-benefit-2", 10, 259_00, "CHILD_BENEFIT",
                                        "Family Benefits Office", "Child benefit, second child", null),
                                new Recurring("sports-club", 15, -30_00, "LEISURE", "Example Sports Club",
                                        "Children's sports club", null)),
                        980_00,
                        List.of(
                                new ContractSpec("riester", "RIESTER_PENSION", "Example Pension Fund",
                                        "EX-RIE-3307-1186", "Petra", "POLICYHOLDER", 45_00L, "MONTHLY",
                                        LocalDate.of(2026, 11, 1), LocalDate.of(2016, 1, 1), "PENSION",
                                        "Riester pension contribution"),
                                new ContractSpec("liability", "LIABILITY", "Example Mutual Insurance",
                                        "EX-LIA-6604-2290", "Petra", "POLICYHOLDER", 75_00L, "ANNUAL",
                                        LocalDate.of(2027, 3, 1), LocalDate.of(2015, 3, 1), "INSURANCE",
                                        "Family liability insurance annual premium")),
                        List.of(), List.of(), List.of(), List.of()),
                new PersonaSpec("p-tim",
                        "Tim, 23, trainee. 1,150 EUR training allowance at the end of the month and about 80 EUR to"
                                + " spare each month (UJ-6).",
                        2026_1015_05L, oct15, 520_00,
                        List.of(current()),
                        List.of(
                                rent(1, -420_00),
                                ticket(),
                                phone(6, -25_00),
                                streaming(18, -8_00),
                                new Recurring("salary", 28, 1150_00, "SALARY", "Example Crafts Company",
                                        "Training allowance", null)),
                        559_00, List.of(), List.of(), List.of(), List.of(), List.of()),
                new PersonaSpec("p-shortfall",
                        "Backs Q&A item 56 (PRD FR-51). Salary on the 1st, 1,800 EUR fixed costs on the 1st and about"
                                + " 400 EUR variable spending a month; the annual car insurance of 620 EUR is debited"
                                + " on 28 Nov, before the next salary credit.",
                        2026_1028_06L, LocalDate.of(2026, 10, 28), 450_00,
                        List.of(current(), new AccountSpec(OVERNIGHT, "OVERNIGHT", "Overnight deposit", 1000_00)),
                        List.of(
                                new Recurring("salary", 1, 2300_00, "SALARY", "Example Clinic", "Salary", null),
                                rent(1, -1420_00),
                                electricity(1, -190_00),
                                phone(1, -50_00),
                                ticket(),
                                new Recurring("gym", 1, -45_00, "LEISURE", "Example Fitness Club", "Membership fee",
                                        null),
                                streaming(1, -17_00),
                                new Recurring("newspaper", 1, -20_00, "SUBSCRIPTIONS", "Example Daily News",
                                        "Newspaper subscription", null)),
                        400_00,
                        List.of(new ContractSpec("car", "CAR", "Example Motor Insurance", "EX-CAR-9902-3317",
                                "Shortfall test persona", "POLICYHOLDER", 620_00L, "ANNUAL",
                                LocalDate.of(2026, 11, 28), LocalDate.of(2023, 11, 28), "INSURANCE",
                                "Car insurance annual premium")),
                        List.of(new OneOffTransfer(LocalDate.of(2026, 1, 20), 500_00, OVERNIGHT,
                                        "Transfer to overnight deposit"),
                                new OneOffTransfer(LocalDate.of(2026, 5, 12), 300_00, OVERNIGHT,
                                        "Transfer to overnight deposit")),
                        List.of(), List.of(),
                        List.of(new GroundTruth("item-56-premium-before-salary",
                                "Q&A item 56: the car insurance premium of 620 EUR debited on 28 Nov 2026, before the"
                                        + " next salary credit, takes the forecast balance on the due date to"
                                        + " -82.04 EUR (at least 50 EUR below zero); fixed costs alone would leave"
                                        + " +330 EUR.",
                                LocalDate.of(2026, 11, 28), "-82.04", null))),
                new PersonaSpec("p-harness",
                        "Neutral test harness persona without a stored profile: a current account only, no contracts,"
                                + " income equal to spending.",
                        2026_1020_07L, LocalDate.of(2026, 10, 20), 1500_00,
                        List.of(current()),
                        List.of(
                                new Recurring("salary", 1, 2000_00, "SALARY", "Example Employer", "Salary", null),
                                rent(1, -800_00),
                                electricity(5, -60_00),
                                phone(10, -30_00)),
                        1110_00, List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private static AccountSpec current() {
        return new AccountSpec(CURRENT, "CURRENT", "Current account", 0);
    }

    private static Recurring rent(int day, long cents) {
        return new Recurring("rent", day, cents, "RENT", "Example Housing Co.", "Rent", null);
    }

    private static Recurring ticket() {
        return new Recurring("transport-ticket", 1, -58_00, "PUBLIC_TRANSPORT", "Example Transit Authority",
                "Monthly public transport ticket", null);
    }

    private static Recurring electricity(int day, long cents) {
        return new Recurring("electricity", day, cents, "UTILITIES", "Example Power Utility", "Electricity", null);
    }

    private static Recurring phone(int day, long cents) {
        return new Recurring("phone", day, cents, "TELECOM", "Example Mobile", "Phone and internet", null);
    }

    private static Recurring streaming(int day, long cents) {
        return new Recurring("streaming", day, cents, "SUBSCRIPTIONS", "Example Streaming Service",
                "Streaming subscription", null);
    }

    // --------------------------------------------------------------------------------------------------- generation

    /** One booking before ids are assigned. {@code account} and {@code contract} are spec keys. */
    private record Booking(LocalDate date, String account, long cents, String category, String counterparty,
            String reference, String recurrenceId, String contract, String idKey) {
    }

    private static final class Generation {

        private final PersonaSpec spec;
        private final RandomGenerator random;
        private final List<Booking> bookings = new ArrayList<>();

        Generation(PersonaSpec spec) {
            this.spec = spec;
            this.random = RandomGeneratorFactory.of("L64X128MixRandom").create(spec.seed());
        }

        String json() {
            Map<String, String> ibans = new LinkedHashMap<>();
            for (AccountSpec account : spec.accounts()) {
                ibans.put(account.key(), "DE00" + String.format("%018d", random.nextLong(1_000_000_000_000_000_000L)));
            }
            recurringBookings();
            premiumBookings();
            oneOffBookings();
            savingsPlanBookings();
            variableBookings();
            bookings.sort(Comparator.comparing(Booking::date));

            long currentMovement = bookings.stream().filter(b -> b.account().equals(CURRENT))
                    .mapToLong(Booking::cents).sum();
            long currentOpening = spec.currentBalanceCents() - currentMovement;
            if (currentOpening < 0) {
                throw new IllegalStateException(spec.id() + ": back-computed opening balance is negative: "
                        + money(currentOpening));
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schemaVersion", 1);
            root.put("personaId", spec.id());
            root.put("description", spec.description());
            Map<String, Object> generator = new LinkedHashMap<>();
            generator.put("seed", spec.seed());
            generator.put("generatorVersion", GENERATOR_VERSION);
            root.put("generator", generator);
            root.put("simulatedDate", spec.simulatedDate().toString());

            List<Object> accounts = new ArrayList<>();
            for (AccountSpec account : spec.accounts()) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", id("account", account.key()));
                a.put("type", account.type());
                a.put("name", account.name());
                a.put("iban", ibans.get(account.key()));
                a.put("openingDate", HISTORY_START.toString());
                a.put("openingBalance", money(account.key().equals(CURRENT) ? currentOpening : account.openingCents()));
                accounts.add(a);
            }
            root.put("accounts", accounts);

            List<Object> contracts = new ArrayList<>();
            for (ContractSpec contract : spec.contracts()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", id("contract", contract.key()));
                c.put("coverage", contract.coverage());
                c.put("provider", contract.provider());
                c.put("policyNumber", contract.policyNumber());
                c.put("policyholder", contract.policyholder());
                c.put("insuredRole", contract.insuredRole());
                if (contract.premiumCents() != null) {
                    c.put("premium", money(contract.premiumCents()));
                    c.put("paymentInterval", contract.interval());
                    c.put("nextDueDate", contract.nextDueDate().toString());
                    c.put("debitAccountId", id("account", CURRENT));
                }
                c.put("startDate", contract.startDate().toString());
                contracts.add(c);
            }
            root.put("contracts", contracts);

            List<Object> transactions = new ArrayList<>();
            for (Booking booking : bookings) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", id("transaction", booking.idKey()));
                t.put("accountId", id("account", booking.account()));
                t.put("bookingDate", booking.date().toString());
                t.put("amount", money(booking.cents()));
                t.put("category", booking.category());
                t.put("counterparty", booking.counterparty());
                t.put("reference", booking.reference());
                if (booking.recurrenceId() != null) {
                    t.put("recurrenceId", booking.recurrenceId());
                }
                if (booking.contract() != null) {
                    t.put("contractId", id("contract", booking.contract()));
                }
                transactions.add(t);
            }
            root.put("transactions", transactions);

            List<Object> holdings = new ArrayList<>();
            for (HoldingSpec holding : spec.holdings()) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("id", id("holding", holding.key()));
                h.put("accountId", id("account", DEPOT));
                h.put("name", holding.name());
                h.put("assetClass", holding.assetClass());
                h.put("units", holding.units());
                h.put("unitPrice", holding.unitPrice());
                h.put("valuationDate", spec.simulatedDate().toString());
                h.put("costBasis", holding.costBasis());
                holdings.add(h);
            }
            root.put("holdings", holdings);

            List<Object> plans = new ArrayList<>();
            for (SavingsPlanSpec plan : spec.savingsPlans()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", id("savings-plan", plan.key()));
                p.put("accountId", id("account", DEPOT));
                p.put("debitAccountId", id("account", CURRENT));
                p.put("name", plan.name());
                p.put("assetClass", plan.assetClass());
                p.put("amount", money(plan.cents()));
                p.put("dayOfMonth", plan.day());
                p.put("startDate", plan.startDate().toString());
                p.put("recurrenceId", plan.recurrenceId());
                plans.add(p);
            }
            root.put("savingsPlans", plans);

            List<Object> truths = new ArrayList<>();
            for (GroundTruth truth : spec.groundTruth()) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("id", truth.id());
                g.put("description", truth.description());
                g.put("date", truth.date().toString());
                g.put("expectedBalance", truth.expectedBalance());
                if (truth.premiumOverride() != null) {
                    g.put("premiumOverride", truth.premiumOverride());
                }
                truths.add(g);
            }
            root.put("plantedGroundTruth", truths);
            return Json.write(root);
        }

        private void recurringBookings() {
            for (YearMonth month = YearMonth.from(HISTORY_START); !month.atDay(1).isAfter(spec.simulatedDate());
                    month = month.plusMonths(1)) {
                for (Recurring r : spec.recurring()) {
                    LocalDate date = month.atDay(Math.min(r.day(), month.lengthOfMonth()));
                    if (date.isAfter(spec.simulatedDate())) {
                        continue;
                    }
                    bookings.add(new Booking(date, CURRENT, r.cents(), r.category(), r.counterparty(), r.reference(),
                            r.id(), null, "recurring:" + r.id() + ":" + date));
                    if (r.toAccount() != null) {
                        bookings.add(new Booking(date, r.toAccount(), -r.cents(), r.category(), "Current account",
                                r.reference(), r.id(), null, "recurring-credit:" + r.id() + ":" + date));
                    }
                }
            }
        }

        private void premiumBookings() {
            for (ContractSpec c : spec.contracts()) {
                if (c.premiumCents() == null) {
                    continue;
                }
                int months = switch (c.interval()) {
                    case "MONTHLY" -> 1;
                    case "QUARTERLY" -> 3;
                    case "ANNUAL" -> 12;
                    default -> throw new IllegalArgumentException(c.interval());
                };
                if (!c.nextDueDate().isAfter(spec.simulatedDate())) {
                    throw new IllegalStateException(spec.id() + ": next due date must follow the simulated date");
                }
                for (int k = 1; ; k++) {
                    LocalDate due = c.nextDueDate().minusMonths((long) k * months);
                    if (due.isBefore(HISTORY_START) || due.isBefore(c.startDate())) {
                        break;
                    }
                    bookings.add(new Booking(due, CURRENT, -c.premiumCents(), c.category(), c.provider(),
                            c.reference() + " " + c.policyNumber(), null, c.key(), "premium:" + c.key() + ":" + due));
                }
            }
        }

        private void oneOffBookings() {
            for (OneOffTransfer o : spec.oneOffTransfers()) {
                bookings.add(new Booking(o.date(), CURRENT, -o.cents(), "TRANSFER", "Overnight deposit",
                        o.reference(), null, null, "one-off:" + o.date()));
                bookings.add(new Booking(o.date(), o.toAccount(), o.cents(), "TRANSFER", "Current account",
                        o.reference(), null, null, "one-off-credit:" + o.date()));
            }
        }

        private void savingsPlanBookings() {
            for (SavingsPlanSpec plan : spec.savingsPlans()) {
                for (YearMonth month = YearMonth.from(HISTORY_START); !month.atDay(1).isAfter(spec.simulatedDate());
                        month = month.plusMonths(1)) {
                    LocalDate date = month.atDay(plan.day());
                    if (date.isBefore(plan.startDate()) || date.isAfter(spec.simulatedDate())) {
                        continue;
                    }
                    bookings.add(new Booking(date, CURRENT, -plan.cents(), "SAVINGS_PLAN", "Depot",
                            "Savings plan: " + plan.name(), plan.recurrenceId(), null,
                            "savings-plan:" + plan.key() + ":" + date));
                }
            }
        }

        /** A chain of days 2 to 4 days apart; each calendar month's amounts add up to exactly V (pro rata at the end). */
        private void variableBookings() {
            Map<YearMonth, List<LocalDate>> days = new TreeMap<>();
            for (LocalDate day = HISTORY_START.plusDays(random.nextInt(3)); !day.isAfter(spec.simulatedDate());
                    day = day.plusDays(2 + random.nextInt(3))) {
                days.computeIfAbsent(YearMonth.from(day), m -> new ArrayList<>()).add(day);
            }
            YearMonth last = YearMonth.from(spec.simulatedDate());
            days.forEach((month, monthDays) -> {
                long total = month.equals(last)
                        ? Math.round((double) spec.variableMonthlyCents() * spec.simulatedDate().getDayOfMonth()
                                / month.lengthOfMonth())
                        : spec.variableMonthlyCents();
                long[] weights = new long[monthDays.size()];
                long weightSum = 0;
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = 50 + random.nextInt(251);
                    weightSum += weights[i];
                }
                long allocated = 0;
                long[] amounts = new long[weights.length];
                for (int i = 0; i < weights.length; i++) {
                    amounts[i] = total * weights[i] / weightSum;
                    allocated += amounts[i];
                }
                for (int i = 0; allocated < total; i = (i + 1) % amounts.length) {
                    amounts[i]++;
                    allocated++;
                }
                for (int i = 0; i < monthDays.size(); i++) {
                    VariableKind kind = pickKind();
                    bookings.add(new Booking(monthDays.get(i), CURRENT, -amounts[i], kind.category(),
                            kind.counterparty(), kind.reference(), null, null, "variable:" + monthDays.get(i)));
                }
            });
        }

        private VariableKind pickKind() {
            int total = VARIABLE_KINDS.stream().mapToInt(VariableKind::weight).sum();
            int pick = random.nextInt(total);
            for (VariableKind kind : VARIABLE_KINDS) {
                pick -= kind.weight();
                if (pick < 0) {
                    return kind;
                }
            }
            throw new IllegalStateException();
        }

        private String id(String kind, String key) {
            return UUID.nameUUIDFromBytes(("fine:fixture:" + spec.id() + ":" + kind + ":" + key)
                    .getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    static String money(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return sign + (abs / 100) + "." + String.format("%02d", abs % 100);
    }

    /** A small JSON writer with a fixed layout: one array element per line, LF line endings. */
    static final class Json {

        private Json() {
        }

        static String write(Map<String, Object> root) {
            StringBuilder out = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                out.append("  ").append(string(entry.getKey())).append(": ");
                if (entry.getValue() instanceof List<?> list) {
                    if (list.isEmpty()) {
                        out.append("[]");
                    } else {
                        out.append("[\n");
                        for (int j = 0; j < list.size(); j++) {
                            out.append("    ").append(inline(list.get(j)));
                            out.append(j < list.size() - 1 ? ",\n" : "\n");
                        }
                        out.append("  ]");
                    }
                } else {
                    out.append(inline(entry.getValue()));
                }
                out.append(++i < root.size() ? ",\n" : "\n");
            }
            return out.append("}\n").toString();
        }

        private static String inline(Object value) {
            if (value instanceof Map<?, ?> map) {
                StringBuilder out = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.append(i++ == 0 ? "" : ", ").append(string((String) entry.getKey())).append(": ")
                            .append(inline(entry.getValue()));
                }
                return out.append("}").toString();
            }
            if (value instanceof String s) {
                return string(s);
            }
            if (value instanceof Integer || value instanceof Long) {
                return value.toString();
            }
            throw new IllegalArgumentException("Unsupported JSON value " + value);
        }

        private static String string(String s) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    default -> {
                        if (c < 0x20 || c > 0x7e) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            return out.append('"').toString();
        }
    }
}
