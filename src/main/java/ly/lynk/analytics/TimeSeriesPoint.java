package ly.lynk.analytics;

import java.time.LocalDate;

public record TimeSeriesPoint(LocalDate date, long clicks) {}
