/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.uhndata.cards.clarity.importer.internal;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.cm.ConfigurationException;

/**
 * Unit tests for the condition language of {@link AbstractConditionalClarityDataProcessor}.
 *
 * @version $Id$
 */
public class AbstractConditionalClarityDataProcessorTest
{
    /** Records which branch the conditions led to, so a test can tell a match from a miss. */
    private static final class TestProcessor extends AbstractConditionalClarityDataProcessor
    {
        private String unmatchedCondition;

        private boolean allMatched;

        TestProcessor(final String... conditions) throws ConfigurationException
        {
            super(new String[0], 0, conditions);
        }

        @Override
        protected Map<String, String> handleUnmatchedCondition(final Map<String, String> input,
            final String condition)
        {
            this.unmatchedCondition = condition;
            return input;
        }

        @Override
        protected Map<String, String> handleAllConditionsMatched(final Map<String, String> input)
        {
            this.allMatched = true;
            return input;
        }
    }

    private static Map<String, String> row(final String... keysAndValues)
    {
        final Map<String, String> row = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    /** Runs one condition against one row and reports whether the condition held. */
    private static boolean matches(final String condition, final Map<String, String> input)
        throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor(condition);
        processor.processEntry(input);
        return processor.allMatched;
    }

    private static void assertMatches(final String condition, final Map<String, String> input)
        throws ConfigurationException
    {
        Assert.assertTrue("expected [" + condition + "] to hold for " + input, matches(condition, input));
    }

    private static void assertDoesNotMatch(final String condition, final Map<String, String> input)
        throws ConfigurationException
    {
        Assert.assertFalse("expected [" + condition + "] not to hold for " + input, matches(condition, input));
    }

    // Equality

    @Test
    public void equalsIsCaseInsensitive() throws ConfigurationException
    {
        assertMatches("STATUS = Completed", row("STATUS", "completed"));
        assertMatches("STATUS = completed", row("STATUS", "COMPLETED"));
        assertDoesNotMatch("STATUS = Completed", row("STATUS", "Cancelled"));
    }

    @Test
    public void equalsDoesNotMatchAMissingColumn() throws ConfigurationException
    {
        assertDoesNotMatch("STATUS = Completed", row());
    }

    @Test
    public void notEqualsIsCaseInsensitive() throws ConfigurationException
    {
        assertDoesNotMatch("STATUS <> Completed", row("STATUS", "completed"));
        assertMatches("STATUS <> Completed", row("STATUS", "Cancelled"));
    }

    /** An absent value is different from anything, which is what lets a filter act on missing data. */
    @Test
    public void notEqualsMatchesAMissingColumn() throws ConfigurationException
    {
        assertMatches("STATUS <> Completed", row());
    }

    // Numeric comparisons

    @Test
    public void numericOperatorsCompareAsNumbers() throws ConfigurationException
    {
        assertMatches("AGE > 5", row("AGE", "10"));
        assertDoesNotMatch("AGE > 50", row("AGE", "10"));
        assertMatches("AGE >= 10", row("AGE", "10"));
        assertMatches("AGE < 50", row("AGE", "10"));
        assertMatches("AGE <= 10", row("AGE", "10"));
        assertDoesNotMatch("AGE < 10", row("AGE", "10"));
    }

    /** String ordering would put "10" before "5"; these must be read as numbers. */
    @Test
    public void numericOperatorsDoNotCompareAsStrings() throws ConfigurationException
    {
        assertMatches("AGE > 5", row("AGE", "10"));
        assertDoesNotMatch("AGE < 5", row("AGE", "10"));
    }

    @Test
    public void numericOperatorsHandleDecimals() throws ConfigurationException
    {
        assertMatches("SCORE >= 1.5", row("SCORE", "1.75"));
        assertDoesNotMatch("SCORE >= 1.5", row("SCORE", "1.25"));
    }

    @Test
    public void numericOperatorsRejectValuesThatAreNotNumbers() throws ConfigurationException
    {
        assertDoesNotMatch("AGE > 5", row("AGE", "unknown"));
        assertDoesNotMatch("AGE > 5", row());
        assertDoesNotMatch("AGE < 5", row("AGE", ""));
    }

    // Lists

    @Test
    public void inMatchesAnyListedValueCaseInsensitively() throws ConfigurationException
    {
        assertMatches("CLINIC in a; b; c", row("CLINIC", "b"));
        assertMatches("CLINIC in a; b; c", row("CLINIC", "C"));
        assertDoesNotMatch("CLINIC in a; b; c", row("CLINIC", "d"));
        assertDoesNotMatch("CLINIC in a; b; c", row());
    }

    @Test
    public void inToleratesSpacingAroundTheSeparators() throws ConfigurationException
    {
        assertMatches("CLINIC in a;b;c", row("CLINIC", "c"));
        assertMatches("CLINIC in a ;  b ; c", row("CLINIC", "b"));
    }

    @Test
    public void notInIsTheNegationOfIn() throws ConfigurationException
    {
        assertDoesNotMatch("CLINIC not in a; b; c", row("CLINIC", "b"));
        assertMatches("CLINIC not in a; b; c", row("CLINIC", "d"));
        assertMatches("CLINIC not in a; b; c", row());
    }

    // Regular expressions

    @Test
    public void matchesAppliesARegularExpressionToTheWholeValue() throws ConfigurationException
    {
        assertMatches("MRN matches [0-9]+", row("MRN", "12345"));
        assertDoesNotMatch("MRN matches [0-9]+", row("MRN", "12345X"));
        assertDoesNotMatch("MRN matches [0-9]+", row());
    }

    @Test
    public void notMatchesIsTheNegationOfMatches() throws ConfigurationException
    {
        assertDoesNotMatch("MRN not matches [0-9]+", row("MRN", "12345"));
        assertMatches("MRN not matches [0-9]+", row("MRN", "12345X"));
        assertMatches("MRN not matches [0-9]+", row());
    }

    // Unary operators

    @Test
    public void isEmptyCoversBothAnAbsentAndABlankValue() throws ConfigurationException
    {
        assertMatches("EMAIL is empty", row("EMAIL", ""));
        assertMatches("EMAIL is empty", row());
        assertDoesNotMatch("EMAIL is empty", row("EMAIL", "a@b.com"));
    }

    @Test
    public void isNotEmptyIsTheNegationOfIsEmpty() throws ConfigurationException
    {
        assertDoesNotMatch("EMAIL is not empty", row("EMAIL", ""));
        assertDoesNotMatch("EMAIL is not empty", row());
        assertMatches("EMAIL is not empty", row("EMAIL", "a@b.com"));
    }

    /** " is empty" is tried before " is not empty", so the longer operator must still win. */
    @Test
    public void isNotEmptyIsNotMistakenForIsEmpty() throws ConfigurationException
    {
        assertMatches("EMAIL is not empty", row("EMAIL", "a@b.com"));
    }

    // Parsing

    @Test
    public void theTwoCharacterOperatorsWinOverTheirPrefixes() throws ConfigurationException
    {
        // "<=" must not be read as "<" followed by "=5"
        assertMatches("AGE <= 10", row("AGE", "10"));
        assertMatches("AGE >= 10", row("AGE", "10"));
        // "<>" must not be read as "<"
        assertMatches("STATUS <> a", row("STATUS", "b"));
    }

    @Test
    public void aValueMayItselfContainTheEqualsSign() throws ConfigurationException
    {
        assertMatches("COMMENT = a=b", row("COMMENT", "a=b"));
    }

    @Test
    public void spacesAroundTheOperatorAreOptional() throws ConfigurationException
    {
        assertMatches("STATUS=Completed", row("STATUS", "Completed"));
        assertMatches("STATUS   =   Completed", row("STATUS", "Completed"));
    }

    @Test(expected = ConfigurationException.class)
    public void anUnparseableConditionIsRejected() throws ConfigurationException
    {
        new TestProcessor("this has no operator at all");
    }

    @Test
    public void noConditionsMeansEverythingMatches() throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor();
        processor.processEntry(row("ANY", "value"));
        Assert.assertTrue(processor.allMatched);
    }

    @Test
    public void nullConditionsAreAccepted() throws ConfigurationException
    {
        final AbstractConditionalClarityDataProcessor processor =
            new AbstractConditionalClarityDataProcessor(new String[0], 0, null)
            {
                @Override
                protected Map<String, String> handleUnmatchedCondition(final Map<String, String> input,
                    final String condition)
                {
                    return input;
                }

                @Override
                protected Map<String, String> handleAllConditionsMatched(final Map<String, String> input)
                {
                    return input;
                }
            };
        final Map<String, String> input = row("ANY", "value");
        Assert.assertSame(input, processor.processEntry(input));
    }

    // Several conditions

    @Test
    public void everyConditionMustHoldForAMatch() throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor("STATUS = Completed", "AGE > 18");
        processor.processEntry(row("STATUS", "Completed", "AGE", "20"));
        Assert.assertTrue(processor.allMatched);
    }

    @Test
    public void theFirstFailingConditionStopsTheEvaluation() throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor("STATUS = Completed", "AGE > 18");
        processor.processEntry(row("STATUS", "Cancelled", "AGE", "20"));
        Assert.assertFalse(processor.allMatched);
        Assert.assertEquals("STATUS=Completed", processor.unmatchedCondition);
    }

    @Test
    public void theReportedConditionIsTheOneThatFailed() throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor("STATUS = Completed", "AGE > 18");
        processor.processEntry(row("STATUS", "Completed", "AGE", "12"));
        Assert.assertFalse(processor.allMatched);
        Assert.assertEquals("AGE>18", processor.unmatchedCondition);
    }

    /** The rendered condition is what the filters put in their log messages. */
    @Test
    public void aUnaryConditionIsReportedWithoutAValue() throws ConfigurationException
    {
        final TestProcessor processor = new TestProcessor("EMAIL is not empty");
        processor.processEntry(row());
        Assert.assertEquals("EMAIL is not empty", processor.unmatchedCondition);
    }
}
