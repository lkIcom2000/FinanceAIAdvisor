package de.financetool.monitoring.domain;

import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What Trigger detection reads: Core figures at the as-of date, gathered once by the monitoring run. Price-free by
 * design: no holding, price or index figure is part of it, so no Trigger can follow a market move (S9).
 *
 * @param overnightAccountId {@code null} without an overnight deposit
 * @param shortfall {@code null} if the forecast stays at or above zero
 * @param topUp the Core's emergency fund top-up, or {@code null} if it was not computed (fund at or above target)
 */
public record MonitoringSnapshot(UUID currentAccountId, UUID overnightAccountId, Shortfall shortfall,
        SourcedNumber emergencyFund, SourcedNumber emergencyFundTarget, SourcedNumber topUp, SourcedNumber netIncome,
        List<PremiumDue> annualPremiums, List<Duplicate> duplicates, List<RuleChange> ruleChanges,
        List<Announcement> announcements) {

    /**
     * The forecast shortfall.
     *
     * @param firstBelowZero the expected balance on {@code date}, the first day below zero
     * @param lowest the lowest expected balance, on {@code lowestDate}
     */
    public record Shortfall(LocalDate date, SourcedNumber firstBelowZero, LocalDate lowestDate,
            SourcedNumber lowest) {

        public Shortfall {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(firstBelowZero, "firstBelowZero");
            Objects.requireNonNull(lowestDate, "lowestDate");
            Objects.requireNonNull(lowest, "lowest");
        }
    }

    /** A contract paid yearly, with its next due date. */
    public record PremiumDue(String contractId, String typeName, LocalDate nextDueDate, SourcedNumber premium) {

        public PremiumDue {
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(typeName, "typeName");
            Objects.requireNonNull(nextDueDate, "nextDueDate");
            Objects.requireNonNull(premium, "premium");
        }
    }

    /**
     * A Duplicate Coverage flag.
     *
     * @param cover the cover's id, e.g. {@code LIABILITY}
     * @param plainWords the cover in running text, e.g. "liability"
     * @param flag the Core output the flag carries
     */
    public record Duplicate(String cover, String plainWords, SourcedNumber flag) {

        public Duplicate {
            Objects.requireNonNull(cover, "cover");
            Objects.requireNonNull(plainWords, "plainWords");
            Objects.requireNonNull(flag, "flag");
        }
    }

    /**
     * A Rule Parameter version that started recently.
     *
     * @param name the rule in plain words
     * @param previous the value it replaces, or {@code null} for a first version
     */
    public record RuleChange(String key, String version, LocalDate validFrom, String name, SourcedNumber value,
            SourcedNumber previous) {

        public RuleChange {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(validFrom, "validFrom");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * An announced rule change.
     *
     * @param rulesValid whether every key of the change has a Rule Parameter version valid on {@code effectiveFrom}
     */
    public record Announcement(String id, String topic, String title, LocalDate announcedOn, LocalDate effectiveFrom,
            boolean hasSource, boolean rulesValid) {

        public Announcement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(announcedOn, "announcedOn");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        }
    }

    public MonitoringSnapshot {
        Objects.requireNonNull(currentAccountId, "currentAccountId");
        Objects.requireNonNull(emergencyFund, "emergencyFund");
        Objects.requireNonNull(emergencyFundTarget, "emergencyFundTarget");
        Objects.requireNonNull(netIncome, "netIncome");
        annualPremiums = List.copyOf(annualPremiums);
        duplicates = List.copyOf(duplicates);
        ruleChanges = List.copyOf(ruleChanges);
        announcements = List.copyOf(announcements);
    }
}
