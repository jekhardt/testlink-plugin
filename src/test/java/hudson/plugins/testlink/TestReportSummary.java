/*
 * The MIT License
 *
 * Copyright (c) 2010 Bruno P. Kinoshita <http://www.kinoshita.eti.br>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.testlink;

import hudson.plugins.testlink.result.TestCaseWrapper;
import hudson.plugins.testlink.util.TestLinkHelper;

import java.lang.reflect.Constructor;
import java.util.Locale;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionType;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;

/**
 * Tests the ReportSummary class.
 *
 * @see {@link ResultsSummary}
 *
 * @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
 * @since 2.1
 */
public class TestReportSummary
extends junit.framework.TestCase
{

    private TestProject testProject;
    private TestPlan testPlan;

	/**
	 * Prepares for the tests.
	 */
    public void setUp()
	{
		Locale.setDefault(new Locale("en", "US"));
		testProject = new TestProject();
		testProject.setId(123);
		testProject.setName("test project");
		testPlan = new TestPlan();
		testPlan.setId(1234);
		testPlan.setName("test plan");
	}

	/**
	 * Tests the hidden constructor of the ReportSummary class.
	 */
	public void testConstructor()
	{
		try
		{
			final Constructor<?> c = TestLinkHelper.class.getDeclaredConstructors()[0];
			c.setAccessible(true);
			final Object o = c.newInstance((Object[]) null);

			assertNotNull(o);
		}
		catch (Exception e)
		{
			fail("Failed to instantiate constructor: " + e.getMessage());
		}
	}

	/**
	 * Tests getPlusSignal() method.
	 */
	public void getPlusSignal()
	{
		String plusSignal = TestLinkHelper.getPlusSignal(1, 0);
		assertEquals( plusSignal, " (+1)" );

		plusSignal = TestLinkHelper.getPlusSignal(0, 1);
		assertEquals( plusSignal, "" );
	}

	/**
	 * Tests the createReportSummary() method with a previous TestLinkReport.
	 */
	public void testSummary()
	{
		Report report = new Report(testProject, testPlan, 1, "My build");

		report.setPassed(1);

		report.setFailed(1);

		report.setBlocked(1);

		String reportSummary = TestLinkHelper.createReportSummary(report, null);
		assertNotNull(reportSummary);
		assertEquals(reportSummary, "<p><b>TestLink build ID: 1</b></p><p><b>TestLink build name: My build</b></p><p><a href=\"testLinkResult\">Total of 3 tests</a>. Where 1 passed, 1 failed, 1 were blocked and 0 were not executed.</p>");
	}

	/**
	 * Tests the createReportSummary() method with a previous TestLinkReport.
	 */
	public void testSummaryWithPrevious()
	{
		Report report = new Report(testProject, testPlan, 1, "My build");

		report.setPassed(1);
		report.setFailed(1);
		report.setBlocked(1);

		Report previous = new Report(testProject, testPlan, 1, "My build");

		previous.setPassed(1);
		previous.setFailed(1);

		String reportSummary = TestLinkHelper.createReportSummary(report, previous);
		assertNotNull(reportSummary);
		assertEquals(reportSummary, "<p><b>TestLink build ID: 1</b></p><p><b>TestLink build name: My build</b></p><p><a href=\"testLinkResult\">Total of 3 (+1) tests</a>. Where 1 passed, 1 failed, 1 (+1) were blocked and 0 were not executed.</p>");
	}

	/**
	 * Tests the createReportSummaryDetails() method.
	 */
	public void testSummaryDetails()
	{
		Report report = new Report(testProject, testPlan, 1, null);

		TestCase testCase1 = new TestCase(1, "tc1", 1, 1, "kinow", "No summary", null, "", null, ExecutionType.AUTOMATED, null, 1, 1, false, null, 1, 1, null, null, ExecutionStatus.PASSED );
		TestCase testCase2 = new TestCase(2, "tc2", 2, 2, "kinow", "No summary", null, "", null, ExecutionType.AUTOMATED, null, 2, 2, false, null, 2, 2, null, null, ExecutionStatus.FAILED );
		TestCase testCase3 = new TestCase(3, "tc3", 3, 3, "kinow", "No summary", null, "", null, ExecutionType.AUTOMATED, null, 3, 3, false, null, 3, 3, null, null, ExecutionStatus.BLOCKED );
        testCase1.setFullExternalId("T-1");
        testCase2.setFullExternalId("T-2");
        testCase3.setFullExternalId("T-3");

		TestCaseWrapper tc1 = new TestCaseWrapper(testCase1);
        tc1.setTestSuiteName("Test Suite 1");
        tc1.setTestSuiteId(1111);
		TestCaseWrapper tc2 = new TestCaseWrapper(testCase2);
        tc2.setTestSuiteName("Test Suite 1");
        tc2.setTestSuiteId(1111);
		TestCaseWrapper tc3 = new TestCaseWrapper(testCase3);
        tc3.setTestSuiteName("Test Suite 2");
        tc3.setTestSuiteId(1112);

		report.addTestCase(tc1);
		report.addTestCase(tc2);
		report.addTestCase(tc3);

		Report previous = new Report(testProject, testPlan, 1, null);

		previous.addTestCase(tc1);
		previous.addTestCase(tc2);

		String reportSummaryDetails = TestLinkHelper.createReportSummaryDetails(report, previous);
		assertNotNull(reportSummaryDetails);

		String expectedDetails = ""+
		"<p>List of test cases and execution result status for Project: test project (id:123) Test Plan: test plan (id:1234)</p><table border=\"1\">\n" +
 "<tr><th>Test case ID</th><th>Test case external ID</th><th>Version</th><th>Name</th><th>Test Suite</th><th>Execution status</th></tr>\n"
                +
"<tr>\n" +
"<td>1</td><td>T-1</td><td>1</td><td>tc1</td><td>Test Suite 1 (id:1111)</td><td><span style='color: green'>Passed</span></td>\n" +
"</tr>\n" +
"<tr>\n" +
"<td>2</td><td>T-2</td><td>2</td><td>tc2</td><td>Test Suite 1 (id:1111)</td><td><span style='color: red'>Failed</span></td>\n" +
"</tr>\n" +
"<tr>\n" +
"<td>3</td><td>T-3</td><td>3</td><td>tc3</td><td>Test Suite 2 (id:1112)</td><td><span style='color: yellow'>Blocked</span></td>\n" +
"</tr>\n" +
"</table>";

		assertEquals(expectedDetails, reportSummaryDetails);
	}

}
