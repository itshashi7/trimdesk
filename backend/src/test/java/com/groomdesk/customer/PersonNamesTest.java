package com.groomdesk.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PersonNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ajith raj          | Ajith Raj",
            "AJITH RAJ          | Ajith Raj",
            "aJiTh   rAj        | Ajith Raj",
            "'  senthil kumar ' | Senthil Kumar",
            "k.r. prakash       | K.R. Prakash",
            "s.kumar            | S.Kumar",
            "mary-ann d'souza   | Mary-Ann D'Souza",
            "Ajith Raj          | Ajith Raj",
    })
    void titleCasesWhateverTheCashierTyped(String typed, String stored) {
        assertThat(PersonNames.normalize(typed)).isEqualTo(stored);
    }
}
