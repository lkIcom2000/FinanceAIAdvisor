package de.financetool.monitoring.domain;

import de.financetool.monitoring.domain.MonitoringSnapshot.Announcement;
import de.financetool.monitoring.domain.MonitoringSnapshot.Duplicate;
import de.financetool.monitoring.domain.MonitoringSnapshot.PremiumDue;
import de.financetool.monitoring.domain.MonitoringSnapshot.RuleChange;
import de.financetool.monitoring.domain.MonitoringSnapshot.Shortfall;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The fixed customer texts of Notifications (no LLM): English number format with a real minus sign, plain words, no
 * internal vocabulary, never buy or sell wording.
 */
public final class NotificationTemplates {

    /**
     * A title and body.
     *
     * @param question the prepared chat question for "Ask Fine about this", or {@code null}
     */
    public record Text(String title, String body, String question) {

        public Text(String title, String body) {
            this(title, body, null);
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final char MINUS = '−';

    private NotificationTemplates() {
    }

    public static Text shortfallTransfer(Shortfall shortfall, BigDecimal amount) {
        return new Text(shortfallTitle(shortfall), shortfallFacts(shortfall) + " Fine has prepared a transfer of "
                + eur(amount) + " from your overnight deposit to your current account to cover it.");
    }

    public static Text shortfallWithoutOvernight(Shortfall shortfall) {
        return new Text(shortfallTitle(shortfall), shortfallFacts(shortfall)
                + " You could move money to your current account or spend less before then.");
    }

    public static Text payPremiumMonthly(PremiumDue premium) {
        return new Text("Paying monthly could avoid this shortfall",
                "Your yearly premium for " + premium.typeName() + " of " + eur(premium.premium().value())
                        + " is due on " + date(premium.nextDueDate())
                        + ", no later than the day your balance drops below zero. Paying the premium monthly would"
                        + " avoid this every"
                        + " year.",
                "Should I pay my premium for " + premium.typeName() + " monthly instead of yearly?");
    }

    public static Text emergencyFundWhileShortfall(SourcedNumber fund, SourcedNumber target) {
        return new Text(EF_TITLE, fundFacts(fund, target) + " Fine is not suggesting a top-up now because your current"
                + " account is expected to run short first.");
    }

    public static Text emergencyFundTopUp(SourcedNumber fund, SourcedNumber target, BigDecimal amount) {
        return new Text("Top up your emergency fund", fundFacts(fund, target) + " Fine has prepared a transfer of "
                + eur(amount) + " from your current account to your overnight deposit: what an average month leaves"
                + " over, up to the target.");
    }

    public static Text emergencyFundNothingLeftOver(SourcedNumber fund, SourcedNumber target) {
        return new Text(EF_TITLE, fundFacts(fund, target) + " An average month leaves nothing over after your"
                + " expenses, so Fine is not suggesting a top-up now.");
    }

    public static Text emergencyFundWithoutOvernight(SourcedNumber fund, SourcedNumber target) {
        return new Text(EF_TITLE, fundFacts(fund, target)
                + " You don't have an overnight deposit to keep an emergency fund in yet.");
    }

    public static Text duplicateCoverage(Duplicate duplicate) {
        return new Text("You may be paying twice for " + duplicate.plainWords() + " insurance",
                "More than one of your contracts covers " + duplicate.plainWords() + ". Check whether you need all"
                        + " of them; Fine can help you compare them.",
                "Do I need all of my contracts that cover " + duplicate.plainWords() + "?");
    }

    public static Text subsidyRuleChange(RuleChange change) {
        String before = change.previous() == null ? "" : " (before: " + value(change.previous()) + ")";
        return new Text("A subsidy rule has changed",
                "The " + lowerFirst(change.name()) + " is " + value(change.value()) + " from "
                        + date(change.validFrom()) + before + ". This may change the subsidy you can get. Ask Fine"
                        + " what it means for you.",
                "What does the change to the " + lowerFirst(change.name()) + " mean for me?");
    }

    /** One note for an ECB decision: old and new rates, what they usually move, no investment wording (S19). */
    public static Text ecbRateChange(LocalDate validFrom, List<RuleChange> changes) {
        String rates = changes.stream()
                .map(c -> c.name() + " " + (c.previous() == null ? "" : value(c.previous()) + " to ")
                        + value(c.value()))
                .collect(Collectors.joining("; "));
        return new Text("The ECB has changed its key interest rates",
                "From " + date(validFrom) + ": " + rates + ". Interest on your overnight deposit and the cost of an"
                        + " overdraft often follow these rates. This is for your information only.");
    }

    public static Text announcementEscalated(Announcement announcement) {
        return new Text("A " + announcement.topic() + " change has been announced",
                announcement.title() + " has been announced for " + date(announcement.effectiveFrom())
                        + ". Its rules are not confirmed yet, so an advisor will look at what it means for you.");
    }

    /** The case summary for the advisor (not shown to the customer). */
    public static String announcementCaseSummary(Announcement announcement) {
        return "Announced " + announcement.topic() + " change \"" + announcement.title() + "\" from "
                + date(announcement.effectiveFrom()) + " has no confirmed source or rules yet; check what it means"
                + " for this customer.";
    }

    private static final String EF_TITLE = "Your emergency fund is below its target";

    private static String shortfallTitle(Shortfall shortfall) {
        return "Your current account may run short on " + date(shortfall.date());
    }

    private static String shortfallFacts(Shortfall shortfall) {
        return "Your forecast shows your current account dropping below zero on " + date(shortfall.date())
                + ", to " + eur(shortfall.lowest().value()) + " at its lowest on " + date(shortfall.lowestDate())
                + ".";
    }

    private static String fundFacts(SourcedNumber fund, SourcedNumber target) {
        return "Your emergency fund is " + eur(fund.value()) + ", below its target of " + eur(target.value()) + ".";
    }

    static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** "1,240.00 EUR", "−90.00 EUR". */
    static String eur(BigDecimal amount) {
        BigDecimal cents = amount.setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return (cents.signum() < 0 ? String.valueOf(MINUS) : "") + format.format(cents.abs()) + " EUR";
    }

    /** A figure in its unit: EUR as money, a fraction as a percentage ("2.15 %"), anything else as its number. */
    static String value(SourcedNumber number) {
        BigDecimal value = number.value();
        String text = switch (number.unit()) {
            case EUR -> eur(value);
            case FRACTION -> value.movePointRight(2).stripTrailingZeros().abs().toPlainString() + " %";
            default -> value.stripTrailingZeros().abs().toPlainString();
        };
        return number.unit() != Unit.EUR && value.signum() < 0 ? MINUS + text : text;
    }

    private static String lowerFirst(String text) {
        return text.startsWith("Riester") || text.startsWith("ECB") ? text
                : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
