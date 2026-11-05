package polight.server.domain.trip.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class CountryCodeValidator implements ConstraintValidator<CountryCode, String> {

  private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value != null && COUNTRY_CODES.contains(value);
  }
}
