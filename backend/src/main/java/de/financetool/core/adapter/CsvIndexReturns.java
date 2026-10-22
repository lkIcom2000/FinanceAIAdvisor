package de.financetool.core.adapter;

import de.financetool.core.application.IndexReturns;
import de.financetool.shared.AppHome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads the historical index series from {@code APP_HOME/data/index/*.csv} once at startup: header {@code
 * month,return}, one row per month ({@code YYYY-MM}, monthly return as a fraction above −1), months strictly
 * ascending. No directory or no file means no series. Startup fails, naming the file, on a malformed file or on more
 * than one series.
 */
@Component
class CsvIndexReturns implements IndexReturns {

    static final String DIRECTORY = "index";
    static final String HEADER = "month,return";

    private final Optional<Series> series;

    @Autowired
    CsvIndexReturns(AppHome appHome) {
        this(appHome.data().resolve(DIRECTORY));
    }

    CsvIndexReturns(Path directory) {
        this.series = load(directory);
    }

    @Override
    public Optional<Series> series() {
        return series;
    }

    static Optional<Series> load(Path directory) {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(directory)) {
            files = list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list index series in " + directory, e);
        }
        if (files.isEmpty()) {
            return Optional.empty();
        }
        if (files.size() > 1) {
            throw new IllegalStateException("Only one index series is supported in " + directory + ", found "
                    + files.stream().map(p -> p.getFileName().toString()).toList());
        }
        return Optional.of(read(files.getFirst()));
    }

    static Series read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read index series " + file, e);
        }
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst().strip().replace("﻿", ""))) {
            throw new IllegalStateException("Index series " + file + " must start with the header '" + HEADER + "'");
        }
        List<BigDecimal> returns = new ArrayList<>();
        YearMonth previous = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            try {
                if (cells.length != 2) {
                    throw new IllegalArgumentException("expected 2 columns, got " + cells.length);
                }
                YearMonth month = YearMonth.parse(cells[0].strip());
                BigDecimal value = new BigDecimal(cells[1].strip());
                if (value.compareTo(BigDecimal.ONE.negate()) <= 0) {
                    throw new IllegalArgumentException("a monthly return must be above -1");
                }
                if (previous != null && !month.isAfter(previous)) {
                    throw new IllegalArgumentException("months must be strictly ascending");
                }
                previous = month;
                returns.add(value);
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IllegalStateException("Index series " + file + ", line " + (i + 1) + ": "
                        + e.getMessage(), e);
            }
        }
        return new Series(file.getFileName().toString(), returns);
    }
}
