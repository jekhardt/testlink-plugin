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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.plugins.testlink.result.ResultSeeker;
import hudson.plugins.testlink.result.ResultSeekerException;
import hudson.plugins.testlink.result.TestCaseWrapper;
import hudson.plugins.testlink.util.Messages;
import hudson.plugins.testlink.util.TestLinkHelper;
import hudson.tasks.BuildStep;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import br.eti.kinoshita.testlinkjavaapi.TestLinkAPI;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;
import br.eti.kinoshita.testlinkjavaapi.model.TestSuite;
import br.eti.kinoshita.testlinkjavaapi.util.TestLinkAPIException;

/**
 * A builder to add a TestLink build step.
 *
 * @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
 * @since 1.0
 */
public class TestLinkBuilder extends AbstractTestLinkBuilder {

	private static final Logger LOGGER = Logger.getLogger("hudson.plugins.testlink");

	/**
	 * The Descriptor of this Builder. It contains the TestLink installation.
	 */
	@Extension
	public static final TestLinkBuilderDescriptor DESCRIPTOR = new TestLinkBuilderDescriptor();

	@DataBoundConstructor
	public TestLinkBuilder(String testLinkName, String testProjectName,
			String testPlanName, String buildName, String customFields,
			List<BuildStep> singleBuildSteps,
			List<BuildStep> beforeIteratingAllTestCasesBuildSteps,
			List<BuildStep> iterativeBuildSteps,
			List<BuildStep> afterIteratingAllTestCasesBuildSteps,
			Boolean transactional, Boolean failedTestsMarkBuildAsFailure,
			Boolean failIfNoResults, List<ResultSeeker> resultSeekers) {
		super(testLinkName, testProjectName, testPlanName, buildName,
				customFields, singleBuildSteps,
				beforeIteratingAllTestCasesBuildSteps, iterativeBuildSteps,
				afterIteratingAllTestCasesBuildSteps, transactional,
				failedTestsMarkBuildAsFailure, failIfNoResults, resultSeekers);
	}

	/**
	 * Called when the job is executed.
	 */
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		LOGGER.log(Level.INFO, "TestLink builder started");

		this.failure = false;

		// TestLink installation
		listener.getLogger().println(Messages.TestLinkBuilder_PreparingTLAPI());
		final TestLinkInstallation installation = DESCRIPTOR
				.getInstallationByTestLinkName(this.testLinkName);
		if (installation == null) {
			throw new AbortException(Messages.TestLinkBuilder_InvalidTLAPI());
		}

		TestLinkHelper.setTestLinkJavaAPIProperties(installation.getTestLinkJavaAPIProperties(), listener);

		final TestLinkSite testLinkSite;
		final TestCaseWrapper[] automatedTestCases;
		final String testLinkUrl = installation.getUrl();
		final String testLinkDevKey = installation.getDevKey();
		listener.getLogger().println(Messages.TestLinkBuilder_UsedTLURL(testLinkUrl));

		try {
			final String testProjectName = expandVariable(build.getBuildVariableResolver(),
					build.getEnvironment(listener), getTestProjectName());
			final String testPlanName = expandVariable(build.getBuildVariableResolver(),
					build.getEnvironment(listener), getTestPlanName());
			final String buildName = expandVariable(build.getBuildVariableResolver(),
					build.getEnvironment(listener), getBuildName());
			final String buildNotes = Messages.TestLinkBuilder_Build_Notes();
			if(LOGGER.isLoggable(Level.FINE)) {
				LOGGER.log(Level.FINE, "TestLink project name: ["+testProjectName+"]");
				LOGGER.log(Level.FINE, "TestLink plan name: ["+testPlanName+"]");
				LOGGER.log(Level.FINE, "TestLink build name: ["+buildName+"]");
				LOGGER.log(Level.FINE, "TestLink build notes: ["+buildNotes+"]");
			}
			// TestLink Site object
			testLinkSite = this.getTestLinkSite(testLinkUrl, testLinkDevKey, testProjectName, testPlanName, buildName, buildNotes);
			final String[] customFieldsNames = this.createArrayOfCustomFieldsNames(build.getBuildVariableResolver(), build.getEnvironment(listener));
			// Array of automated test cases
			TestCase[] testCases = testLinkSite.getAutomatedTestCases(customFieldsNames);

			// Transforms test cases into test case wrappers
            automatedTestCases = this.transform(testLinkSite, testCases);

			testCases = null;

			listener.getLogger().println(Messages.TestLinkBuilder_ShowFoundAutomatedTestCases(automatedTestCases.length));

			// Sorts test cases by each execution order (this info comes from
			// TestLink)
			listener.getLogger().println(Messages.TestLinkBuilder_SortingTestCases());
			Arrays.sort(automatedTestCases, this.executionOrderComparator);
		} catch (MalformedURLException mue) {
			mue.printStackTrace(listener.fatalError(mue.getMessage()));
			throw new AbortException(Messages.TestLinkBuilder_InvalidTLURL(testLinkUrl));
		} catch (TestLinkAPIException e) {
			e.printStackTrace(listener.fatalError(e.getMessage()));
			throw new AbortException(Messages.TestLinkBuilder_TestLinkCommunicationError());
		}

		if(LOGGER.isLoggable(Level.FINE)) {
			for(TestCaseWrapper tcw : automatedTestCases) {
				LOGGER.log(Level.FINE, "TestLink automated test case ID [" + tcw.getId() + "], name [" +tcw.getName()+ "]");
			}
		}

		listener.getLogger().println(Messages.TestLinkBuilder_ExecutingSingleBuildSteps());
		this.executeSingleBuildSteps(build, launcher, listener);

		listener.getLogger().println(Messages.TestLinkBuilder_ExecutingIterativeBuildSteps());
		this.executeIterativeBuildSteps(automatedTestCases, testLinkSite, build, launcher, listener);

		// Here we search for test results. The return if a wrapped Test Case
		// that
		// contains attachments, platform and notes.
		try {
			listener.getLogger().println(Messages.Results_LookingForTestResults());

			if(getResultSeekers() != null) {
				for (ResultSeeker resultSeeker : getResultSeekers()) {
					LOGGER.log(Level.INFO, "Seeking test results. Using: " + resultSeeker.getDescriptor().getDisplayName());
					resultSeeker.seek(automatedTestCases, build, launcher, listener, testLinkSite);
				}
			}
		} catch (ResultSeekerException trse) {
			trse.printStackTrace(listener.fatalError(trse.getMessage()));
			throw new AbortException(Messages.Results_ErrorToLookForTestResults(trse.getMessage()));
		} catch (TestLinkAPIException tlae) {
			tlae.printStackTrace(listener.fatalError(tlae.getMessage()));
			throw new AbortException(Messages.TestLinkBuilder_FailedToUpdateTL(tlae.getMessage()));
		}

		// This report is used to generate the graphs and to store the list of
		// test cases with each found status.
		final Report report = testLinkSite.getReport();

		listener.getLogger().println(Messages.TestLinkBuilder_ShowFoundTestResults(report.getTestsTotal()));

		final TestLinkResult result = new TestLinkResult(report, build);
		final TestLinkBuildAction buildAction = new TestLinkBuildAction(build, result);
		build.addAction(buildAction);

		if(report.getTestsTotal() <= 0 && this.getFailIfNoResults() == Boolean.TRUE) {
			listener.getLogger().println("No test results found. Setting the build result as FAILURE.");
			build.setResult(Result.FAILURE);
		} else if (report.getFailed() > 0) {
			if (this.failedTestsMarkBuildAsFailure != null && this.failedTestsMarkBuildAsFailure) {
				build.setResult(Result.FAILURE);
			} else {
				build.setResult(Result.UNSTABLE);
			}
		}

		LOGGER.log(Level.INFO, "TestLink builder finished");

		// end
		return Boolean.TRUE;
	}

	/**
	 * @param testCases
	 * @return
	 */
    private TestCaseWrapper[] transform(TestLinkSite testLinkSite,
            TestCase[] testCases) {
		if(testCases == null || testCases.length == 0) {
			return new TestCaseWrapper[0];
		}

		List<TestCaseWrapper> automatedTestCases = new ArrayList<TestCaseWrapper>();

        // store suite and project name in lookup Maps to skip repeat calls
        Map<Integer, String> suiteIdMap = new HashMap<Integer, String>();
		for(TestCase testCase : testCases) {
            // get the full test case to get the name and suite
            TestCase fullTestCase = testLinkSite.getApi()
                    .getTestCaseByExternalId(testCase.getFullExternalId(),
                            testCase.getVersion());

            // get the test suite name
            int testSuiteId = fullTestCase.getTestSuiteId();
            if (suiteIdMap.get(testSuiteId) == null) {
                // get the suite name for the first time
                List<Integer> testSuiteIds = new ArrayList<Integer>();
                testSuiteIds.add(testSuiteId);
                TestSuite[] suites = testLinkSite.getApi().getTestSuiteByID(
                        testSuiteIds);
                suiteIdMap.put(testSuiteId, suites[0].getName());
            }

            // set fields not available in test execution results
            testCase.setTestSuiteId(testSuiteId);
            testCase.setName(fullTestCase.getName());

            TestCaseWrapper wrapper = new TestCaseWrapper(testCase);
            wrapper.setTestSuiteName(suiteIdMap.get(testSuiteId));

			automatedTestCases.add(wrapper);
		}
		return automatedTestCases.toArray(new TestCaseWrapper[0]);
	}

	/**
	 * Gets object to interact with TestLink site.
	 *
	 * @throws MalformedURLException
	 */
	public TestLinkSite getTestLinkSite(String testLinkUrl,
			String testLinkDevKey, String testProjectName, String testPlanName,
			String buildName, String buildNotes) throws MalformedURLException {
		final TestLinkAPI api;
		final URL url = new URL(testLinkUrl);
		api = new TestLinkAPI(url, testLinkDevKey);

		final TestProject testProject = api
				.getTestProjectByName(testProjectName);

		final TestPlan testPlan = api.getTestPlanByName(testPlanName,
				testProjectName);

		final Build build = api.createBuild(testPlan.getId(), buildName,
				buildNotes);

		return new TestLinkSite(api, testProject, testPlan, build);
	}

	/**
	 * Executes the list of single build steps.
	 *
	 * @param build
	 *            Jenkins build.
	 * @param launcher
	 * @param listener
	 * @throws IOException
	 * @throws InterruptedException
	 */
	protected void executeSingleBuildSteps(AbstractBuild<?, ?> build,
			Launcher launcher, BuildListener listener) throws IOException,
			InterruptedException {
		if (singleBuildSteps != null) {
			for (BuildStep b : singleBuildSteps) {
				final boolean success = b.perform(build, launcher, listener);
				if (!success) {
					this.failure = Boolean.TRUE;
				}
			}
		}
	}

	/**
	 * <p>
	 * Executes iterative build steps. For each automated test case found in the
	 * array of automated test cases, this method executes the iterative builds
	 * steps using Jenkins objects.
	 * </p>
	 *
	 * @param automatedTestCases
	 *            array of automated test cases
	 * @param testLinkSite
	 *            The TestLink Site object
	 * @param launcher
	 * @param listener
	 * @throws InterruptedException
	 * @throws IOException
	 */
	protected void executeIterativeBuildSteps(TestCaseWrapper[] automatedTestCases,
			TestLinkSite testLinkSite, AbstractBuild<?, ?> build,
			Launcher launcher, BuildListener listener) throws IOException,
			InterruptedException {

		if (beforeIteratingAllTestCasesBuildSteps != null) {
			for (BuildStep b : beforeIteratingAllTestCasesBuildSteps) {
				final boolean success = b.perform(build, launcher, listener);
				if (!success) {
					this.failure = Boolean.TRUE;
				}
			}
		}

		for (TestCaseWrapper automatedTestCase : automatedTestCases) {
			if (this.failure && this.transactional) {
				automatedTestCase.setExecutionStatus(ExecutionStatus.BLOCKED);
			} else {
				if (iterativeBuildSteps != null) {
					final EnvVars iterativeEnvVars = TestLinkHelper.buildTestCaseEnvVars(automatedTestCase,
									testLinkSite.getTestProject(),
									testLinkSite.getTestPlan(),
									testLinkSite.getBuild(), listener);

					build.addAction(new EnvironmentContributingAction() {
						public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
							env.putAll(iterativeEnvVars);
						}
						public String getUrlName() {
							return null;
						}
						public String getIconFileName() {
							return null;
						}
						public String getDisplayName() {
							return null;
						}
					});
					for (BuildStep b : iterativeBuildSteps) {
						final boolean success = b.perform(build, launcher, listener);
						if (!success) {
							this.failure = Boolean.TRUE;
						}
					}
				}
			}
		}

		if (afterIteratingAllTestCasesBuildSteps != null) {
			for (BuildStep b : afterIteratingAllTestCasesBuildSteps) {
				final boolean success = b.perform(build, launcher, listener);
				if (!success) {
					this.failure = Boolean.TRUE;
				}
			}
		}
	}
}