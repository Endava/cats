package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

@QuarkusTest
class CatsFakerTest {

    private CatsFaker catsFaker;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
        catsFaker = new CatsFaker();
    }

    @Nested
    @DisplayName("BookFaker Tests")
    class BookFakerTests {

        @Test
        void shouldReturnBookTitle() {
            String title = catsFaker.book().title();
            
            Assertions.assertThat(title).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.book()).isSameAs(catsFaker.book());
        }
    }

    @Nested
    @DisplayName("ColorFaker Tests")
    class ColorFakerTests {

        @Test
        void shouldReturnColorName() {
            String color = catsFaker.color().name();
            
            Assertions.assertThat(color).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.color()).isSameAs(catsFaker.color());
        }
    }

    @Nested
    @DisplayName("AncientFaker Tests")
    class AncientFakerTests {

        @Test
        void shouldReturnGodName() {
            String god = catsFaker.ancient().god();
            
            Assertions.assertThat(god).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnPrimordialName() {
            String primordial = catsFaker.ancient().primordial();
            
            Assertions.assertThat(primordial).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnTitanName() {
            String titan = catsFaker.ancient().titan();
            
            Assertions.assertThat(titan).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnHeroName() {
            String hero = catsFaker.ancient().hero();
            
            Assertions.assertThat(hero).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.ancient()).isSameAs(catsFaker.ancient());
        }
    }

    @Nested
    @DisplayName("AddressFaker Tests")
    class AddressFakerTests {

        @Test
        void shouldReturnCity() {
            String city = catsFaker.address().city();
            
            Assertions.assertThat(city).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnCountry() {
            String country = catsFaker.address().country();
            
            Assertions.assertThat(country).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnCountryCode() {
            String countryCode = catsFaker.address().countryCode();
            
            Assertions.assertThat(countryCode).isNotNull().isNotEmpty().hasSizeBetween(2, 3);
        }

        @Test
        void shouldReturnState() {
            String state = catsFaker.address().state();
            
            Assertions.assertThat(state).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnStateAbbr() {
            String stateAbbr = catsFaker.address().stateAbbr();
            
            Assertions.assertThat(stateAbbr).isNotNull().hasSize(2);
        }

        @Test
        void shouldReturnZipCode() {
            String zipCode = catsFaker.address().zipCode();
            
            Assertions.assertThat(zipCode).isNotNull().isNotEmpty().matches("\\d{5}(-\\d{4})?");
        }

        @Test
        void shouldReturnFullAddress() {
            String fullAddress = catsFaker.address().fullAddress();
            
            Assertions.assertThat(fullAddress).isNotNull().isNotEmpty().contains(",");
        }

        @Test
        void shouldReturnStreetAddress() {
            String streetAddress = catsFaker.address().streetAddress();
            
            Assertions.assertThat(streetAddress).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.address()).isSameAs(catsFaker.address());
        }
    }

    @Nested
    @DisplayName("CompanyFaker Tests")
    class CompanyFakerTests {

        @Test
        void shouldReturnCompanyName() {
            String companyName = catsFaker.company().name();
            
            Assertions.assertThat(companyName).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnIndustry() {
            String industry = catsFaker.company().industry();
            
            Assertions.assertThat(industry).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnProfession() {
            String profession = catsFaker.company().profession();
            
            Assertions.assertThat(profession).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.company()).isSameAs(catsFaker.company());
        }
    }

    @Nested
    @DisplayName("NameFaker Tests")
    class NameFakerTests {

        @Test
        void shouldReturnFirstName() {
            String firstName = catsFaker.name().firstName();
            
            Assertions.assertThat(firstName).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnLastName() {
            String lastName = catsFaker.name().lastName();
            
            Assertions.assertThat(lastName).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnPrefix() {
            String prefix = catsFaker.name().prefix();
            
            Assertions.assertThat(prefix).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSuffix() {
            String suffix = catsFaker.name().suffix();
            
            Assertions.assertThat(suffix).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnFullName() {
            String fullName = catsFaker.name().fullName();
            
            Assertions.assertThat(fullName).isNotNull().isNotEmpty().contains(" ");
        }

        @Test
        void shouldReturnName() {
            String name = catsFaker.name().name();
            
            Assertions.assertThat(name).isNotNull().isNotEmpty().contains(" ");
        }

        @Test
        void shouldReturnUsername() {
            String username = catsFaker.name().username();
            
            Assertions.assertThat(username).isNotNull().isNotEmpty().contains(".");
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.name()).isSameAs(catsFaker.name());
        }
    }

    @Nested
    @DisplayName("ChuckNorrisFaker Tests")
    class ChuckNorrisFakerTests {

        @Test
        void shouldReturnFact() {
            String fact = catsFaker.chuckNorris().fact();
            
            Assertions.assertThat(fact).isNotNull().isNotEmpty();
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.chuckNorris()).isSameAs(catsFaker.chuckNorris());
        }
    }

    @Nested
    @DisplayName("DateFaker Tests")
    class DateFakerTests {

        @Test
        void shouldReturnBirthday() {
            LocalDate birthday = catsFaker.date().birthday();
            
            Assertions.assertThat(birthday).isNotNull();
            Assertions.assertThat(birthday.getYear()).isBetween(1950, 2000);
            Assertions.assertThat(birthday.getMonthValue()).isBetween(1, 12);
            Assertions.assertThat(birthday.getDayOfMonth()).isBetween(1, 28);
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.date()).isSameAs(catsFaker.date());
        }
    }

    @Nested
    @DisplayName("FinanceFaker Tests")
    class FinanceFakerTests {

        @Test
        void shouldReturnIban() {
            String iban = catsFaker.finance().iban();
            
            Assertions.assertThat(iban).isNotNull().isNotEmpty();
            Assertions.assertThat(iban).hasSizeGreaterThanOrEqualTo(22);
        }

        @Test
        void shouldReturnBic() {
            String bic = catsFaker.finance().bic();
            
            Assertions.assertThat(bic).isNotNull().isNotEmpty();
            Assertions.assertThat(bic).isIn("DEUTDEFF", "COBADEFF", "DRESDEFF", "HYVEDEMM", "GENODEF1");
        }

        @Test
        void shouldReturnSameInstanceOnMultipleCalls() {
            Assertions.assertThat(catsFaker.finance()).isSameAs(catsFaker.finance());
        }
    }

    @Nested
    @DisplayName("Numerify Tests")
    class NumerifyTests {

        @Test
        void shouldReplaceHashWithDigits() {
            String result = catsFaker.numerify("###");
            
            Assertions.assertThat(result).matches("\\d{3}");
        }

        @Test
        void shouldReplaceHashesInPattern() {
            String result = catsFaker.numerify("AB-###-CD");
            
            Assertions.assertThat(result).matches("AB-\\d{3}-CD");
        }

        @Test
        void shouldHandlePatternWithoutHashes() {
            String result = catsFaker.numerify("ABCD");
            
            Assertions.assertThat(result).isEqualTo("ABCD");
        }

        @Test
        void shouldHandleEmptyPattern() {
            String result = catsFaker.numerify("");
            
            Assertions.assertThat(result).isEmpty();
        }

        @Test
        void shouldHandleMixedPattern() {
            String result = catsFaker.numerify("Test-##-##-####");
            
            Assertions.assertThat(result).matches("Test-\\d{2}-\\d{2}-\\d{4}");
        }
    }
}
