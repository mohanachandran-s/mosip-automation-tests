package io.mosip.testrig.dslrig.ivv.e2e.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.entities.BIR;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.jna.StringArray;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import io.mosip.testrig.apirig.admin.fw.util.AdminTestException;
import io.mosip.testrig.apirig.admin.fw.util.TestCaseDTO;
import io.mosip.testrig.apirig.authentication.fw.precon.JsonPrecondtion;
import io.mosip.testrig.apirig.authentication.fw.util.AuthenticationTestException;
import io.mosip.testrig.apirig.kernel.util.ConfigManager;
import io.mosip.testrig.apirig.testscripts.GetWithParam;
import io.mosip.testrig.dslrig.ivv.core.base.StepInterface;
import io.mosip.testrig.dslrig.ivv.core.exceptions.RigInternalError;
import io.mosip.testrig.dslrig.ivv.orchestrator.BaseTestCaseUtil;
import io.restassured.response.Response;

public class CheckForBDBAbsence extends BaseTestCaseUtil implements StepInterface {
	private static final Logger logger = Logger.getLogger(CheckForBDBAbsence.class);
	private static final String CheckForBDB = "idaData/RetrieveBioDocumentByID/RetrieveBioDocumentByID.yml";
	GetWithParam checkForBDB = new GetWithParam();

	private String decodedString;

	static {
		if (ConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	@Override
	public void run() throws RigInternalError {
		step.getScenario().getVidPersonaProp().clear();
		String uins = null;
		List<String> uinList = null;
		Object[] testObj = checkForBDB.getYmlTestData(CheckForBDB);
		TestCaseDTO test = (TestCaseDTO) testObj[0];
		String[] modalityArray = null;
		if (step.getParameters().size() == 2) {
			uins = step.getParameters().get(0);
			if (uins.startsWith("$$")) {
				uins = step.getScenario().getVariables().get(uins);
				uinList = new ArrayList<>(Arrays.asList(uins.split("@@")));
			}
			modalityArray = step.getParameters().get(1).split("@@");

			String input = test.getInput();
			input = JsonPrecondtion.parseAndReturnJsonContent(input, uins, "id");
			test.setInput(input);

			try {
				checkForBDB.test(test);
			} catch (AuthenticationTestException e) {
				logger.error(e.getMessage());
			} catch (AdminTestException e) {
				logger.error(e.getMessage());
			}

			Response response = checkForBDB.response;
			JSONObject responseJson = new JSONObject(response.asString());
			JSONObject responseData = responseJson.getJSONObject("response");
			JSONArray responseArray = responseData.getJSONArray("documents");

			String bioData = responseArray.getJSONObject(0).getString("value");

//				String cleanedString = cleanBase64String(bioData);

			Base64.Decoder decoder = Base64.getUrlDecoder();

			// Decode the base64 encoded string.
			byte[] decodedBytes = decoder.decode(bioData);

			// Convert the decoded bytes to a string.
			decodedString = new String(decodedBytes);

			logger.info(decodedString);

			BIR bir = null;
			Map<String, String> finalMap = new HashMap<>();

			try {
				bir = CbeffValidator.getBIRFromXML(decodedBytes);

				boolean isXmlValid = CbeffValidator.validateXML(bir);

				if (isXmlValid)

					finalMap = CbeffValidator.getBDBBasedOnTypeAndSubType(bir, null, null);

				List<String> missBDBInCbeff = new ArrayList<>();

				for (String key : finalMap.keySet()) {

					if (key.contains("FINGER") && finalMap.get(key).contains("Fingeg=="))
						missBDBInCbeff.add(key);
					else if (key.contains("IRIS") && finalMap.get(key).contains("Iris"))
						missBDBInCbeff.add(key);
					else if (key.contains("FACE") && finalMap.get(key).contains("Face"))
						missBDBInCbeff.add(key);
					else if (key.contains("EXCEPTION") && finalMap.get(key).contains("Exception"))
						missBDBInCbeff.add(key);
				}

				if (missBDBInCbeff.size() != modalityArray.length) {
					this.hasError = true;
					throw new RigInternalError(
							"Modalities present in modalityArray and  missBDBInCbeff are not matching");
				}
				for (String str : modalityArray) {
					if (missBDBInCbeff.toString().contains(str) == false) {
						// BDB for the given modality is present
						this.hasError = true;
						throw new RigInternalError("BDB for the given modality " + str + " is present");
					}
				}

			} catch (Exception e) {
				logger.error(e.getMessage());
			}
		}
	}
}
