package io.mosip.testrig.dslrig.ivv.e2e.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.mosip.testrig.apirig.dto.TestCaseDTO;
import io.mosip.testrig.apirig.testrunner.JsonPrecondtion;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.testscripts.BioAuth;
import io.mosip.testrig.dslrig.ivv.core.base.StepInterface;
import io.mosip.testrig.dslrig.ivv.core.exceptions.RigInternalError;
import io.mosip.testrig.dslrig.ivv.e2e.constant.E2EConstants;
import io.mosip.testrig.dslrig.ivv.orchestrator.BaseTestCaseUtil;
import io.mosip.testrig.dslrig.ivv.orchestrator.TestRunner;
import io.mosip.testrig.dslrig.ivv.orchestrator.dslConfigManager;

@Scope("prototype")
@Component
public class OperatorOnboardAuthentication extends BaseTestCaseUtil implements StepInterface {
	static Logger logger = Logger.getLogger(OperatorOnboardAuthentication.class);
	private static final String BIOMETRIC_FACE = "regproc/OperatorOnboardAuth/operatorOnboardAuth.yml";
	Properties deviceProp = null;
	Properties uinResidentDataPathFinalProps = new Properties();
	BioAuth bioAuth = new BioAuth();
	String bioResponse = null;

	static {
		if (dslConfigManager.IsDebugEnabled())
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.ERROR);
	}

	@Override
	public void run() throws RigInternalError {
		List<String> modalityList = new ArrayList<>();
		String personFilePathvalue = null;
		String deviceInfoFilePath = null;
		String uins = null;
		List<String> uinList = null;
		if (step.getParameters() == null || step.getParameters().isEmpty() || step.getParameters().size() < 1) {
			logger.error("Parameter is  missing from DSL step");
			this.hasError = true;
			throw new RigInternalError("Modality paramter is  missing in step: " + step.getName());
		} else {
			deviceInfoFilePath = step.getParameters().get(0);
			if (!StringUtils.isBlank(deviceInfoFilePath)) {
				deviceInfoFilePath = TestRunner.getExternalResourcePath()
						+ props.getProperty("ivv.path.deviceinfo.folder") + deviceInfoFilePath + ".properties";
				deviceProp = AdminTestUtil.getproperty(deviceInfoFilePath);
			} else {
				this.hasError = true;
				throw new RigInternalError("deviceInfo file path Parameter is  missing from DSL step");
			}
		}
		if (step.getParameters().size() == 2) {
			uins = step.getParameters().get(1);
			if (!StringUtils.isBlank(uins))
				uinList = new ArrayList<>(Arrays.asList(uins.split("@@")));
		} else if (step.getParameters().size() == 3) { // e2e_bioAuthentication(faceDevice,$$uin,$$personaFilePath)
			uins = step.getParameters().get(1);
			String _personaFilePath = step.getParameters().get(2);
			if (uins.startsWith("$$") && _personaFilePath.startsWith("$$")) {
				uins = step.getScenario().getVariables().get(uins);
				_personaFilePath = step.getScenario().getVariables().get(_personaFilePath);
				uinList = new ArrayList<>();
				uinList.add(uins);
				step.getScenario().getUinPersonaProp().put(uins, _personaFilePath);
			}
		} else
			uinList = new ArrayList<>(step.getScenario().getUinPersonaProp().stringPropertyNames());

		for (String uin : uinList) {

			if (step.getScenario().getUinPersonaProp().containsKey(uin))
				personFilePathvalue = step.getScenario().getUinPersonaProp().getProperty(uin);
			else {
				this.hasError = true;
				throw new RigInternalError("Persona doesn't exist for the given UIN " + uin);
			}
			String bioType = null, bioSubType = null;

			String modalityToLog = null;
			String modalityKeyTogetBioValue = null;
			if (deviceProp != null) {
				bioType = deviceProp.getProperty("bioType");

				bioSubType = deviceProp.getProperty("bioSubType");
				switch (bioType) {
				case E2EConstants.FACEBIOTYPE:
					modalityList.add(E2EConstants.FACEFETCH);
					modalityToLog = bioType;
					modalityKeyTogetBioValue = E2EConstants.FACEFETCH;
					break;
				case E2EConstants.IRISBIOTYPE:
					modalityList.add(E2EConstants.IRISFETCH);
					modalityToLog = bioSubType + "_" + bioType;
					modalityKeyTogetBioValue = (bioSubType.equalsIgnoreCase("left")) ? E2EConstants.LEFT_EYE
							: E2EConstants.RIGHT_EYE;
					break;
				case E2EConstants.FINGERBIOTYPE:
					modalityList.add(E2EConstants.FINGERFETCH);
					modalityToLog = bioSubType;
					modalityKeyTogetBioValue = bioSubType;
					break;
				default:
					this.hasError = true;
					throw new RigInternalError("Given BIO Type in device property file is not valid");
				}
			}
			bioResponse = packetUtility.retrieveBiometric(personFilePathvalue, modalityList, step);

			String fileName = BIOMETRIC_FACE;
			bioAuth.isInternal = false;
			Object[] casesList = bioAuth.getYmlTestData(fileName);

			if (bioResponse != null && !bioResponse.isEmpty() && modalityKeyTogetBioValue != null) {
				String bioValue = JsonPrecondtion.getValueFromJson(bioResponse, modalityKeyTogetBioValue);
				if (bioValue == null || bioValue.length() < 100) {
					this.hasError = true;
					throw new RigInternalError(
							"Not able to get the bio value for field " + modalityToLog + " from persona");
				}
				for (Object object : casesList) {
					TestCaseDTO test = (TestCaseDTO) object;
					packetUtility.operatorOnboardAuth(modalityToLog, bioValue, "dsl1", test, bioAuth, "USERID",
							deviceProp, step);
				}
			}
		}
	}
}
