package com.erp.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supported operating countries — ISO codes, currency and phone dialing metadata.
 */
@Getter
public enum CountryEnum {

    Kenya("Kenya", "KE", "KEN", "KES", "KSh", "+03:00", "254"),
    Nigeria("Nigeria", "NG", "NGA", "NGN", "₦", "+01:00", "234"),
    Uganda("Uganda", "UG", "UGA", "UGX", "USh", "+03:00", "256"),
    China("China", "CN", "CHN", "CNY", "¥", "+08:00", "86"),
    Tanzania("Tanzania", "TZ", "TZA", "TZS", "TSh", "+03:00", "255");

    private final String countryName;
    private final String countryCode;
    private final String countryCodeFor3;
    private final String currencyCode;
    private final String currencyUnit;
    private final String timeZoneCode;
    private final String areaCode;

    CountryEnum(
            String countryName,
            String countryCode,
            String countryCodeFor3,
            String currencyCode,
            String currencyUnit,
            String timeZoneCode,
            String areaCode) {
        this.countryName = countryName;
        this.countryCode = countryCode;
        this.countryCodeFor3 = countryCodeFor3;
        this.currencyCode = currencyCode;
        this.currencyUnit = currencyUnit;
        this.timeZoneCode = timeZoneCode;
        this.areaCode = areaCode;
    }

    public static Optional<CountryEnum> ofCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        String cc = countryCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(e -> e.countryCode.equals(cc))
                .findFirst();
    }

    /** Default Kenya when code is blank or unknown. */
    public static CountryEnum ofCountryCodeOrDefault(String countryCode) {
        return ofCountryCode(countryCode).orElse(Kenya);
    }

    public static boolean isSupported(String countryCode) {
        return ofCountryCode(countryCode).isPresent();
    }

    public static Set<String> supportedCountryCodes() {
        return Arrays.stream(values()).map(CountryEnum::getCountryCode).collect(Collectors.toSet());
    }

    /** Currency display symbol for the given ISO alpha-2 code (defaults to KSh). */
    public static String currencyUnitOf(String countryCode) {
        return ofCountryCodeOrDefault(countryCode).getCurrencyUnit();
    }

    public static String allowedCodesMessage() {
        return Arrays.stream(values())
                .map(CountryEnum::getCountryCode)
                .collect(Collectors.joining(", "));
    }
}
