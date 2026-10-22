package de.financetool.core.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.core.application.IndexReturns;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Story 2.4: the optional historical index series in {@code data/index/}. */
class CsvIndexReturnsTest {

    @TempDir
    Path data;

    @Test
    void withoutDirectoryOrFileThereIsNoSeries() throws Exception {
        assertThat(new CsvIndexReturns(data.resolve("index")).series()).isEmpty();
        Files.createDirectories(data.resolve("index"));
        assertThat(new CsvIndexReturns(data.resolve("index")).series()).isEmpty();
    }

    @Test
    void aWellFormedFileIsReadInOrder() throws Exception {
        Path index = Files.createDirectories(data.resolve("index"));
        Files.writeString(index.resolve("world.csv"), "month,return\n2020-01,0.012\n2020-02,-0.034\n\n");

        IndexReturns.Series series = new CsvIndexReturns(index).series().orElseThrow();
        assertThat(series.source()).isEqualTo("world.csv");
        assertThat(series.monthlyReturns()).containsExactly(new BigDecimal("0.012"), new BigDecimal("-0.034"));
    }

    @Test
    void aMalformedFileFailsNamingTheFile() throws Exception {
        Path index = Files.createDirectories(data.resolve("index"));
        Path file = index.resolve("world.csv");

        Files.writeString(file, "month,value\n2020-01,0.01\n");
        assertThatThrownBy(() -> new CsvIndexReturns(index)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(file.toString()).hasMessageContaining("header");

        Files.writeString(file, "month,return\n2020-01,0.01\n2020-02,abc\n");
        assertThatThrownBy(() -> new CsvIndexReturns(index)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(file.toString()).hasMessageContaining("line 3");

        Files.writeString(file, "month,return\n2020-02,0.01\n2020-01,0.02\n");
        assertThatThrownBy(() -> new CsvIndexReturns(index)).hasMessageContaining("ascending");

        Files.writeString(file, "month,return\n2020-01,-1\n");
        assertThatThrownBy(() -> new CsvIndexReturns(index)).hasMessageContaining("above -1");
    }

    @Test
    void moreThanOneSeriesIsRefused() throws Exception {
        Path index = Files.createDirectories(data.resolve("index"));
        Files.writeString(index.resolve("a.csv"), "month,return\n");
        Files.writeString(index.resolve("b.csv"), "month,return\n");
        assertThatThrownBy(() -> new CsvIndexReturns(index)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a.csv").hasMessageContaining("b.csv");
    }
}
