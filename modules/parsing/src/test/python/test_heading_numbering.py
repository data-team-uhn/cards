# Copyright 2026 DATA @ UHN. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Unit tests for the heading-numbering helpers ported from docling-hierarchical-pdf and
hardened against the numbering styles seen across the REB protocol corpus."""

import heading_numbering as hn


class TestNumericalNumbering:
    def test_single_integer(self):
        assert hn.numerical_numbering("2 Heading") == [2]

    def test_dotted_two_levels(self):
        assert hn.numerical_numbering("1.2 Methods") == [1, 2]

    def test_dotted_three_levels(self):
        assert hn.numerical_numbering("3.2.1 Recruitment") == [3, 2, 1]

    def test_trailing_dot(self):
        assert hn.numerical_numbering("1. Schema") == [1]

    def test_dash_separated(self):
        assert hn.numerical_numbering("1-2-3 Title") == [1, 2, 3]

    def test_raw_keeps_trailing_zero(self):
        # numerical_numbering is a faithful parse; the .0 collapse happens in numbering_vector.
        assert hn.numerical_numbering("1.0 General Information") == [1, 0]

    def test_no_numbering(self):
        assert hn.numerical_numbering("Introduction") == []


class TestNumericalNumberingRealWorld:
    def test_dot_zero_is_top_level(self):
        assert hn.numbering_depth("1.0 General Information") == 1

    def test_ten_dot_zero_is_top_level(self):
        assert hn.numbering_depth("10.0 References") == 1

    def test_sub_section_depth_two(self):
        assert hn.numbering_depth("1.1 Study Title") == 2

    def test_four_levels(self):
        assert hn.numbering_vector("2.3.1.1 COVID-19 risk mitigation measures") == [2, 3, 1, 1]

    def test_space_mangled_numbering(self):
        assert hn.numbering_vector("3. 1. 1 Aim 1") == [3, 1, 1]

    def test_colon_separated_top_level(self):
        assert hn.numbering_depth("1: General Information") == 1

    def test_colon_dot_zero(self):
        assert hn.numbering_depth("2.0: Background") == 1

    def test_zero_dot_zero_is_top_level(self):
        assert hn.numbering_depth("0.0 Protocol Summary") == 1

    def test_two_spaces_after_number(self):
        assert hn.numbering_depth("1.0  Background information and rationale") == 1

    def test_no_false_positive_ordinal(self):
        assert hn.numbering_vector("1st quarter review") == []

    def test_no_false_positive_year_range(self):
        assert hn.numbering_vector("1-year follow-up") == []

    def test_space_is_not_a_level_separator(self):
        # A bare space between numbers must NOT create a multi-level vector ([3, 5]);
        # a lone leading integer is still legitimate depth-1 section numbering.
        assert hn.numbering_vector("3 5-year survival") == [3]


class TestLetterNumbering:
    def test_uppercase_dot(self):
        assert hn.letter_numbering("A. Consent") == [1]

    def test_lowercase_paren(self):
        assert hn.letter_numbering("b) Details") == [2]

    def test_lowercase_dot(self):
        assert hn.letter_numbering("a. Background") == [1]

    def test_no_separator(self):
        assert hn.letter_numbering("Appendix") == []


class TestRomanNumbering:
    def test_simple(self):
        assert hn.roman_numbering("II. Results") == [2]

    def test_thirteen(self):
        assert hn.roman_numbering("XIII Introduction") == [13]

    def test_twelve_from_corpus(self):
        assert hn.roman_numbering("XII. References") == [12]

    def test_lowercase(self):
        assert hn.roman_numbering("i. Inclusion criteria") == [1]

    def test_no_roman(self):
        assert hn.roman_numbering("Background") == []


class TestNumberingVector:
    def test_numeric(self):
        assert hn.numbering_vector("1.2 Methods") == [1, 2]

    def test_letter_ignored(self):
        assert hn.numbering_vector("A. Consent") == []

    def test_roman_ignored(self):
        assert hn.numbering_vector("II. Results") == []


class TestNumberingDepth:
    def test_depth_three(self):
        assert hn.numbering_depth("3.2.1 Recruitment") == 3

    def test_zero_when_unnumbered(self):
        assert hn.numbering_depth("Introduction") == 0

    def test_letter_depth_zero(self):
        assert hn.numbering_depth("A. Consent") == 0
