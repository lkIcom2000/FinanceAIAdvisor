package de.financetool.core.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The historical monthly index returns in {@code APP_HOME/data/index/}, if a series is present. */
public interface IndexReturns {

    /**
     * @param source the file name, shown in the projection's assumptions
     * @param monthlyReturns monthly returns as fractions, in file order
     */
    record Series(String source, List<BigDecimal> monthlyReturns) {

        public Series {
            Objects.requireNonNull(source, "source");
            monthlyReturns = List.copyOf(monthlyReturns);
        }
    }

    Optional<Series> series();
}
