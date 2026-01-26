package com.endava.cats.util;

import jakarta.inject.Singleton;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simple faker implementation that reads data from data.yml file.
 * Provides random data generation for various categories.
 */
@Singleton
@SuppressWarnings("unchecked")
public class CatsFaker {
    private final Map<String, Object> data;
    private final BookFaker bookFaker;
    private final ColorFaker colorFaker;
    private final AncientFaker ancientFaker;
    private final AddressFaker addressFaker;
    private final CompanyFaker companyFaker;
    private final NameFaker nameFaker;
    private final ChuckNorrisFaker chuckNorrisFaker;
    private final DateFaker dateFaker;
    private final FinanceFaker financeFaker;

    /**
     * Builds a new {@link CatsFaker} by loading the {@code data.yml} resource once
     * and caching all helper faker instances.
     */
    @SuppressWarnings("unchecked")
    public CatsFaker() {
        Yaml yaml = new Yaml();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("data.yml");
        Map<String, Object> fullData = yaml.load(inputStream);
        this.data = (Map<String, Object>) ((Map<String, Object>) fullData.get("ro")).get("faker");

        this.bookFaker = new BookFaker();
        this.colorFaker = new ColorFaker();
        this.ancientFaker = new AncientFaker();
        this.addressFaker = new AddressFaker();
        this.companyFaker = new CompanyFaker();
        this.nameFaker = new NameFaker();
        this.chuckNorrisFaker = new ChuckNorrisFaker();
        this.dateFaker = new DateFaker();
        this.financeFaker = new FinanceFaker();
    }

    /**
     * Provides access to the cached book faker helper.
     *
     * @return singleton accessor for book fakery utilities.
     */
    public BookFaker book() {
        return bookFaker;
    }

    /**
     * Provides access to the cached color faker helper.
     *
     * @return singleton accessor for color fakery utilities.
     */
    public ColorFaker color() {
        return colorFaker;
    }

    /**
     * Provides access to the cached ancient myth faker helper.
     *
     * @return singleton accessor for ancient myth fakery utilities.
     */
    public AncientFaker ancient() {
        return ancientFaker;
    }

    /**
     * Provides access to the cached address faker helper.
     *
     * @return singleton accessor for address fakery utilities.
     */
    public AddressFaker address() {
        return addressFaker;
    }

    /**
     * Provides access to the cached company faker helper.
     *
     * @return singleton accessor for company fakery utilities.
     */
    public CompanyFaker company() {
        return companyFaker;
    }

    /**
     * Provides access to the cached person name faker helper.
     *
     * @return singleton accessor for name fakery utilities.
     */
    public NameFaker name() {
        return nameFaker;
    }

    /**
     * Provides access to the cached Chuck Norris fact helper.
     *
     * @return singleton accessor for chuck norris facts.
     */
    public ChuckNorrisFaker chuckNorris() {
        return chuckNorrisFaker;
    }

    /**
     * Provides access to the cached date faker helper.
     *
     * @return singleton accessor for date fakery utilities.
     */
    public DateFaker date() {
        return dateFaker;
    }

    /**
     * Provides access to the cached finance faker helper.
     *
     * @return singleton accessor for finance fakery utilities.
     */
    public FinanceFaker finance() {
        return financeFaker;
    }

    /**
     * Replaces all {@code #} characters in the provided pattern with random digits.
     *
     * @param pattern template containing {@code #} placeholders
     * @return formatted string with digits instead of {@code #}
     */
    public String numerify(String pattern) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '#') {
                result.append(CatsRandom.instance().nextInt(10));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * @param list candidate values
     * @return random item or empty string when the list has no entries
     */
    private String randomElement(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return list.get(CatsRandom.instance().nextInt(list.size()));
    }

    /**
     * Provides fake book related values.
     */
    public class BookFaker {
        /**
         * Generates a random book title from the configured dataset.
         *
         * @return random book title.
         */
        public String title() {
            Map<String, Object> bookData = (Map<String, Object>) data.get("book");
            List<String> titles = (List<String>) bookData.get("title");
            return randomElement(titles);
        }
    }

    /**
     * Provides fake color values.
     */
    public class ColorFaker {
        /**
         * Generates a random color name from the configured dataset.
         *
         * @return random color name.
         */
        public String name() {
            Map<String, Object> colorData = (Map<String, Object>) data.get("color");
            List<String> colors = (List<String>) colorData.get("name");
            return randomElement(colors);
        }
    }

    /**
     * Provides fake ancient mythology values.
     */
    public class AncientFaker {
        /**
         * Generates a random deity name from the ancient listing.
         *
         * @return random god name.
         */
        public String god() {
            Map<String, Object> ancientData = (Map<String, Object>) data.get("ancient");
            List<String> gods = (List<String>) ancientData.get("god");
            return randomElement(gods);
        }

        /**
         * Generates a random primordial entity name.
         *
         * @return random primordial entity name.
         */
        public String primordial() {
            Map<String, Object> ancientData = (Map<String, Object>) data.get("ancient");
            List<String> primordials = (List<String>) ancientData.get("primordial");
            return randomElement(primordials);
        }

        /**
         * Generates a random titan name.
         *
         * @return random titan name.
         */
        public String titan() {
            Map<String, Object> ancientData = (Map<String, Object>) data.get("ancient");
            List<String> titans = (List<String>) ancientData.get("titan");
            return randomElement(titans);
        }

        /**
         * Generates a random hero name from the ancient section.
         *
         * @return random hero name.
         */
        public String hero() {
            Map<String, Object> ancientData = (Map<String, Object>) data.get("ancient");
            List<String> heroes = (List<String>) ancientData.get("hero");
            return randomElement(heroes);
        }
    }

    /**
     * Provides fake address components.
     */
    public class AddressFaker {
        /**
         * Builds a synthetic city name using prefix/suffix combos.
         *
         * @return synthetic city name.
         */
        public String city() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> cityPrefixes = (List<String>) addressData.get("city_prefix");
            List<String> citySuffixes = (List<String>) addressData.get("city_suffix");
            return randomElement(cityPrefixes) + randomElement(citySuffixes);
        }

        /**
         * Picks a random country from the locale dataset.
         *
         * @return random country name.
         */
        public String country() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> countries = (List<String>) addressData.get("country");
            return randomElement(countries);
        }

        /**
         * Picks a random ISO-like country code.
         *
         * @return ISO style two-letter country code.
         */
        public String countryCode() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> codes = (List<String>) addressData.get("country_code");
            return randomElement(codes);
        }

        /**
         * Selects a random US state name.
         *
         * @return random US state.
         */
        public String state() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> states = (List<String>) addressData.get("state");
            return randomElement(states);
        }

        /**
         * Selects a random US state abbreviation.
         *
         * @return random US state abbreviation.
         */
        public String stateAbbr() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> stateAbbrs = (List<String>) addressData.get("state_abbr");
            return randomElement(stateAbbrs);
        }

        /**
         * Generates a random postal code based on the configured patterns.
         *
         * @return random zip/postal code.
         */
        public String zipCode() {
            Map<String, Object> addressData = (Map<String, Object>) data.get("address");
            List<String> postcodes = (List<String>) addressData.get("postcode");
            return numerify(randomElement(postcodes));
        }

        /**
         * Composes a postal-style full address including city/state/zip.
         *
         * @return formatted full street address.
         */
        public String fullAddress() {
            return numerify(randomElement((List<String>) ((Map<String, Object>) data.get("address")).get("building_number"))) + " " +
                    city() + ", " + stateAbbr() + " " + zipCode();
        }

        /**
         * Composes an abbreviated street address consisting of number and suffix.
         *
         * @return simple street address.
         */
        public String streetAddress() {
            return numerify(randomElement((List<String>) ((Map<String, Object>) data.get("address")).get("building_number"))) + " " +
                    randomElement((List<String>) ((Map<String, Object>) data.get("address")).get("street_suffix"));
        }
    }

    /**
     * Provides fake company related values.
     */
    public class CompanyFaker {
        /**
         * Generates a company name using last name plus suffix.
         *
         * @return generated company name.
         */
        public String name() {
            Map<String, Object> companyData = (Map<String, Object>) data.get("company");
            List<String> suffixes = (List<String>) companyData.get("suffix");
            NameFaker nameFaker = new NameFaker();
            return nameFaker.lastName() + " " + randomElement(suffixes);
        }

        /**
         * Picks a random industry label.
         *
         * @return random industry label.
         */
        public String industry() {
            Map<String, Object> companyData = (Map<String, Object>) data.get("company");
            List<String> industries = (List<String>) companyData.get("industry");
            return randomElement(industries);
        }

        /**
         * Picks a random profession label.
         *
         * @return random profession label.
         */
        public String profession() {
            Map<String, Object> companyData = (Map<String, Object>) data.get("company");
            List<String> professions = (List<String>) companyData.get("profession");
            return randomElement(professions);
        }
    }

    /**
     * Provides fake person name values.
     */
    public class NameFaker {
        /**
         * Generates a random first name entry.
         *
         * @return random first name.
         */
        public String firstName() {
            Map<String, Object> nameData = (Map<String, Object>) data.get("name");
            List<String> firstNames = (List<String>) nameData.get("first_name");
            return randomElement(firstNames);
        }

        /**
         * Generates a random last name entry.
         *
         * @return random last name.
         */
        public String lastName() {
            Map<String, Object> nameData = (Map<String, Object>) data.get("name");
            List<String> lastNames = (List<String>) nameData.get("last_name");
            return randomElement(lastNames);
        }

        /**
         * Generates a random honorific/prefix.
         *
         * @return honorific title.
         */
        public String prefix() {
            Map<String, Object> nameData = (Map<String, Object>) data.get("name");
            List<String> prefixes = (List<String>) nameData.get("prefix");
            return randomElement(prefixes);
        }

        /**
         * Generates a random suffix such as Jr. or Sr.
         *
         * @return suffix such as Jr. or Sr.
         */
        public String suffix() {
            Map<String, Object> nameData = (Map<String, Object>) data.get("name");
            List<String> suffixes = (List<String>) nameData.get("suffix");
            return randomElement(suffixes);
        }

        /**
         * Builds a full name using random first and last names.
         *
         * @return formatted full name.
         */
        public String fullName() {
            return firstName() + " " + lastName();
        }

        /**
         * Alias for {@link #fullName()} to mimic Faker API.
         *
         * @return alias for {@link #fullName()}.
         */
        public String name() {
            return fullName();
        }

        /**
         * Builds a username from lowercase first/last name pair.
         *
         * @return username built from first/last name.
         */
        public String username() {
            return firstName().toLowerCase(Locale.ROOT) + "." + lastName().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Provides fake Chuck Norris facts.
     */
    public class ChuckNorrisFaker {
        /**
         * Provides a humorous Chuck Norris fact.
         *
         * @return random Chuck Norris fact.
         */
        public String fact() {
            Map<String, Object> chuckData = (Map<String, Object>) data.get("chuck_norris");
            List<String> facts = (List<String>) chuckData.get("fact");
            return randomElement(facts);
        }
    }

    /**
     * Provides fake date values.
     */
    public static class DateFaker {
        /**
         * Generates a random birthday within a realistic range.
         *
         * @return random birthday within a realistic range.
         */
        public java.time.LocalDate birthday() {
            int year = 1950 + CatsRandom.instance().nextInt(50);
            int month = 1 + CatsRandom.instance().nextInt(12);
            int day = 1 + CatsRandom.instance().nextInt(28);
            return java.time.LocalDate.of(year, month, day);
        }
    }

    /**
     * Provides fake finance related values.
     */
    public class FinanceFaker {
        /**
         * Generates a pseudo IBAN constructed from country code and digits.
         *
         * @return pseudo IBAN constructed from country code and digits.
         */
        public String iban() {
            String countryCode = address().countryCode();
            return countryCode + numerify("####################");
        }

        /**
         * Selects a pseudo BIC from the predefined list.
         *
         * @return pseudo BIC selected from a curated list.
         */
        public String bic() {
            return randomElement(List.of("DEUTDEFF", "COBADEFF", "DRESDEFF", "HYVEDEMM", "GENODEF1"));
        }
    }
}
