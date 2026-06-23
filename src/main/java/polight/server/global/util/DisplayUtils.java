package polight.server.global.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DisplayUtils {
  private DisplayUtils() {}

  public static String dDayLabel(LocalDate date, LocalDate today) {
    long days = ChronoUnit.DAYS.between(today, date);
    if (days == 0) return "D-DAY";
    return days > 0 ? "D-" + days : "D+" + Math.abs(days);
  }
}
