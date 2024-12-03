package io.mosip.testrig.apirig.esignet.testscripts;

import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import io.mosip.testrig.apirig.dto.OutputValidationDto;
import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.esignet.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.esignet.utils.EsignetUtil;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.HealthChecker;
import io.mosip.testrig.apirig.utils.AdminTestException;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthenticationTestException;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.ReportUtil;
import io.restassured.response.Response;

public class SimplePostForAutoGenIdForUrlEncoded extends AdminTestUtil implements ITest {
	private static final Logger logger = Logger.getLogger(SimplePostForAutoGenIdForUrlEncoded.class);
	protected String testCaseName = "";
	public String idKeyName = null;
	public Response response = null;

	@BeforeClass
	public static void setLogLevel() {
		if (EsignetConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	/**
	 * get current testcaseName
	 */
	@Override
	public String getTestName() {
		return testCaseName;
	}

	/**
	 * Data provider class provides test case list
	 * 
	 * @return object of data provider
	 */
	@DataProvider(name = "testcaselist")
	public Object[] getTestCaseList(ITestContext context) {
		String ymlFile = context.getCurrentXmlTest().getLocalParameters().get("ymlFile");
		idKeyName = context.getCurrentXmlTest().getLocalParameters().get("idKeyName");
		logger.info("Started executing yml: " + ymlFile);
		return getYmlTestData(ymlFile);
	}

	/**
	 * Test method for OTP Generation execution
	 * 
	 * @param objTestParameters
	 * @param testScenario
	 * @param testcaseName
	 * @throws AuthenticationTestException
	 * @throws AdminTestException
	 * @throws NoSuchAlgorithmException
	 */
	@Test(dataProvider = "testcaselist")
	public void test(TestCaseDTO testCaseDTO)
			throws AuthenticationTestException, AdminTestException, NoSuchAlgorithmException {
		testCaseName = testCaseDTO.getTestCaseName();
		testCaseName = EsignetUtil.isTestCaseValidForExecution(testCaseDTO);
		if (HealthChecker.signalTerminateExecution) {
			throw new SkipException(
					GlobalConstants.TARGET_ENV_HEALTH_CHECK_FAILED + HealthChecker.healthCheckFailureMapS);
		}

		if (testCaseDTO.getEndPoint().startsWith("$ESIGNETMOCKBASEURL$") && testCaseName.contains("SunBirdRC")) {
			if (EsignetConfigManager.isInServiceNotDeployedList("sunbirdrc"))
				throw new SkipException(GlobalConstants.SERVICE_NOT_DEPLOYED_MESSAGE);
		}

		if (testCaseDTO.getTestCaseName().contains("VID") || testCaseDTO.getTestCaseName().contains("Vid")) {
			if (!BaseTestCase.getSupportedIdTypesValue().contains("VID")
					&& !BaseTestCase.getSupportedIdTypesValue().contains("vid")) {
				throw new SkipException(GlobalConstants.VID_FEATURE_NOT_SUPPORTED);
			}
		}

		if (testCaseDTO.getEndPoint().startsWith("$ESIGNETMOCKBASEURL$")
				&& testCaseName.contains("SunBirdC")) {
			if (EsignetConfigManager.isInServiceNotDeployedList("sunbirdrc"))
				throw new SkipException(GlobalConstants.SERVICE_NOT_DEPLOYED_MESSAGE);
		}
		
		if (EsignetConfigManager.isInServiceNotDeployedList(GlobalConstants.ESIGNET)) {
			throw new SkipException("esignet is not deployed hence skipping the testcase");
		}
		String[] templateFields = testCaseDTO.getTemplateFields();

		String inputJson = getJsonFromTemplate(testCaseDTO.getInput(), testCaseDTO.getInputTemplate());
		String outputJson = getJsonFromTemplate(testCaseDTO.getOutput(), testCaseDTO.getOutputTemplate());

		String jsonInput = inputJsonKeyWordHandeler(inputJson, testCaseName);

		if (testCaseDTO.getTemplateFields() != null && templateFields.length > 0) {
			ArrayList<JSONObject> inputtestCases = AdminTestUtil.getInputTestCase(testCaseDTO);
			ArrayList<JSONObject> outputtestcase = AdminTestUtil.getOutputTestCase(testCaseDTO);
			for (int i = 0; i < languageList.size(); i++) {
				response = postWithBodyAndCookieForAutoGeneratedId(ApplnURI + testCaseDTO.getEndPoint(),
						getJsonFromTemplate(inputtestCases.get(i).toString(), testCaseDTO.getInputTemplate()),
						COOKIENAME, testCaseDTO.getRole(), testCaseDTO.getTestCaseName(), idKeyName);

				Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil.doJsonOutputValidation(
						response.asString(),
						getJsonFromTemplate(outputtestcase.get(i).toString(), testCaseDTO.getOutputTemplate()),
						testCaseDTO, response.getStatusCode());
				if (testCaseDTO.getTestCaseName().toLowerCase().contains("dynamic")) {
					JSONObject json = new JSONObject(response.asString());
					idField = json.getJSONObject("response").get("id").toString();
				}
				Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));

				if (!OutputValidationUtil.publishOutputResult(ouputValid))
					throw new AdminTestException("Failed at output validation");
			}
		} else {
			if (testCaseName.contains("ESignet_")) {
				String tempUrl = EsignetConfigManager.getEsignetBaseUrl();
				if (testCaseDTO.getEndPoint().startsWith("$ESIGNETMOCKBASEURL$")
						&& testCaseName.contains("SunBirdC")) {

					if (EsignetConfigManager.getEsignetMockBaseURL() != null
							&& !EsignetConfigManager.getEsignetMockBaseURL().isBlank())
						tempUrl = ApplnURI.replace("api-internal.", EsignetConfigManager.getEsignetMockBaseURL());
					testCaseDTO.setEndPoint(testCaseDTO.getEndPoint().replace("$ESIGNETMOCKBASEURL$", ""));
				}
				String endPoint = tempUrl + testCaseDTO.getEndPoint();
				if (testCaseDTO.getEndPoint().contains("$GETENDPOINTFROMRESIDENTACTUATOR$")
						&& BaseTestCase.currentModule.equalsIgnoreCase("resident")) {
					endPoint = getValueFromActuator("mosip-config/resident-default.properties",
							"mosip.iam.token_endpoint");
				}
				if (testCaseDTO.getEndPoint().contains("$GETENDPOINTFROMWELLKNOWN$")
						&& BaseTestCase.currentModule.equalsIgnoreCase("esignet")) {
					endPoint = EsignetUtil.getValueFromEsignetWellKnownEndPoint("token_endpoint", EsignetConfigManager.getEsignetBaseUrl());
				}
				response = postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(endPoint, jsonInput,
						testCaseDTO.getTestCaseName(), idKeyName);

			} else {
				response = postWithBodyAndCookieForAutoGeneratedIdForUrlEncoded(ApplnURI + testCaseDTO.getEndPoint(),
						jsonInput, testCaseDTO.getTestCaseName(), idKeyName);
			}

			Map<String, List<OutputValidationDto>> ouputValid = OutputValidationUtil
					.doJsonOutputValidation(response.asString(), outputJson, testCaseDTO, response.getStatusCode());
			Reporter.log(ReportUtil.getOutputValidationReport(ouputValid));
			if (!OutputValidationUtil.publishOutputResult(ouputValid))
				throw new AdminTestException("Failed at output validation");
		}

	}

	/**
	 * The method ser current test name to result
	 * 
	 * @param result
	 */
	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		try {
			Field method = TestResult.class.getDeclaredField("m_method");
			method.setAccessible(true);
			method.set(result, result.getMethod().clone());
			BaseTestMethod baseTestMethod = (BaseTestMethod) result.getMethod();
			Field f = baseTestMethod.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			f.set(baseTestMethod, testCaseName);
		} catch (Exception e) {
			Reporter.log("Exception : " + e.getMessage());
		}
	}
}
