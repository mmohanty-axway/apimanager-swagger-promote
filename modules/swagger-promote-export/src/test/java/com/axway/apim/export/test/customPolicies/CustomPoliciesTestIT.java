package com.axway.apim.export.test.customPolicies;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.axway.apim.api.model.APIQuota;
import com.axway.apim.api.model.CorsProfile;
import com.axway.apim.api.model.OutboundProfile;
import com.axway.apim.api.model.SecurityProfile;
import com.axway.apim.api.model.TagMap;
import com.axway.apim.export.test.ExportTestAction;
import com.axway.apim.test.ImportTestAction;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Test
public class CustomPoliciesTestIT extends TestNGCitrusTestRunner {

	private ExportTestAction swaggerExport = new ExportTestAction();
	private ImportTestAction swaggerImport = new ImportTestAction();
	ObjectMapper mapper = new ObjectMapper();
	
	@CitrusTest
	@Test @Parameters("context")
	public void run(@Optional @CitrusResource TestContext context) throws IOException {		
		description("Import an API to export it afterwards");

		variable("apiNumber", RandomNumberFunction.getRandomNumber(3, true));
		variable("apiPath", "/api/test/"+this.getClass().getSimpleName()+"-${apiNumber}");
		variable("apiName", this.getClass().getSimpleName()+"-${apiNumber}");
		variable("state", "published");
		


		echo("####### Importing the API, which should exported in the second step #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/test/export/files/basic/petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  "/test/export/files/customPolicies/custom-policies-issue-156.json");
		createVariable("requestPolicy", "Request policy 1");
		createVariable("responsePolicy", "Response policy 1");
		createVariable("tokenInfoPolicy", "Tokeninfo policy 1");
		createVariable("expectedReturnCode", "0");
		swaggerImport.doExecute(context);
		
		exportAPI(context, false);
		exportAPI(context, true);
	}
	
	private void exportAPI(TestContext context, boolean ignoreAdminAccount) throws FileNotFoundException, IOException {
		variable("exportLocation", "citrus:systemProperty('java.io.tmpdir')");
		variable(ExportTestAction.EXPORT_API,  "${apiPath}");
		// These are the folder and filenames generated by the export tool 
		variable("exportFolder", "api-test-${apiName}");
		variable("exportAPIName", "${apiName}.json");
		
		echo("####### Export the API from the API-Manager #######");
		createVariable("expectedReturnCode", "0");
		
		if(ignoreAdminAccount) {
			echo("####### Exporting the API with Org-Admin permissions only #######");
			createVariable("exportLocation", "${exportLocation}/orgAdmin");
			createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
			createVariable("apiManagerPass", "${oadminPassword1}");
			createVariable("ignoreAdminAccount", "true"); // Don't use the Admin-Account given in the env.properties for this test
		} else {
			createVariable("exportLocation", "${exportLocation}/ignoreAdminAccount");
			echo("####### Exporting the API with Admin permissions #######");
		}
		
		swaggerExport.doExecute(context);
		
		String exportedAPIConfigFile = context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/api-config.json";
		
		echo("####### Reading exported API-Config file: '"+exportedAPIConfigFile+"' #######");
		JsonNode exportedAPIConfig = mapper.readTree(new FileInputStream(new File(exportedAPIConfigFile)));
		String tmp = context.replaceDynamicContentInString(IOUtils.toString(this.getClass().getResourceAsStream("/test/export/files/customPolicies/custom-policies-issue-156.json"), "UTF-8"));
		JsonNode importedAPIConfig = mapper.readTree(tmp);

		assertEquals(exportedAPIConfig.get("path").asText(), 				context.getVariable("apiPath"));
		assertEquals(exportedAPIConfig.get("name").asText(), 				context.getVariable("apiName"));
		assertEquals(exportedAPIConfig.get("state").asText(), 				context.getVariable("state"));
		assertEquals(exportedAPIConfig.get("version").asText(), 			"v1");
		assertEquals(exportedAPIConfig.get("organization").asText(),		"API Development "+context.getVariable("orgNumber"));
		
		List<SecurityProfile> importedSecurityProfiles = mapper.convertValue(importedAPIConfig.get("securityProfiles"), new TypeReference<List<SecurityProfile>>(){});
		List<SecurityProfile> exportedSecurityProfiles = mapper.convertValue(exportedAPIConfig.get("securityProfiles"), new TypeReference<List<SecurityProfile>>(){});
		assertEquals(importedSecurityProfiles, exportedSecurityProfiles, "SecurityProfiles are not equal.");
		
		Map<String, OutboundProfile> importedOutboundProfiles = mapper.convertValue(importedAPIConfig.get("outboundProfiles"), new TypeReference<Map<String, OutboundProfile>>(){});
		Map<String, OutboundProfile> exportedOutboundProfiles = mapper.convertValue(exportedAPIConfig.get("outboundProfiles"), new TypeReference<Map<String, OutboundProfile>>(){});
		assertEquals(importedOutboundProfiles, exportedOutboundProfiles, "OutboundProfiles are not equal.");
		
		TagMap<String, String[]> importedTags = mapper.convertValue(importedAPIConfig.get("tags"), new TypeReference<TagMap<String, String[]>>(){});
		TagMap<String, String[]> exportedTags = mapper.convertValue(exportedAPIConfig.get("tags"), new TypeReference<TagMap<String, String[]>>(){});
		assertEquals(importedTags, exportedTags, "Tags are not equal.");
		
		List<CorsProfile> importedCorsProfiles = mapper.convertValue(importedAPIConfig.get("corsProfiles"), new TypeReference<List<CorsProfile>>(){});
		List<CorsProfile> exportedCorsProfiles = mapper.convertValue(exportedAPIConfig.get("corsProfiles"), new TypeReference<List<CorsProfile>>(){});
		assertEquals(importedCorsProfiles, exportedCorsProfiles, "CorsProfiles are not equal.");
		
		APIQuota importedAppQuota = mapper.convertValue(importedAPIConfig.get("applicationQuota"), new TypeReference<APIQuota>(){});
		APIQuota exportedAppQuota = mapper.convertValue(exportedAPIConfig.get("applicationQuota"), new TypeReference<APIQuota>(){});
		assertEquals(importedAppQuota, exportedAppQuota, "applicationQuota are not equal.");
		
		APIQuota importedSystemQuota = mapper.convertValue(importedAPIConfig.get("systemQuota"), new TypeReference<APIQuota>(){});
		APIQuota exportedSystemQuota = mapper.convertValue(exportedAPIConfig.get("systemQuota"), new TypeReference<APIQuota>(){});
		assertEquals(importedSystemQuota, exportedSystemQuota, "systemQuota are not equal.");
		

		assertEquals(exportedAPIConfig.get("caCerts").size(), 				3);
		
		assertEquals(exportedAPIConfig.get("caCerts").get(0).get("certFile").asText(), 				"risequipmentservicedev.nov.cloud.crt");
		assertEquals(exportedAPIConfig.get("caCerts").get(0).get("inbound").asBoolean(), 			false);
		assertEquals(exportedAPIConfig.get("caCerts").get(0).get("outbound").asBoolean(), 			true);
		
		assertEquals(exportedAPIConfig.get("caCerts").get(1).get("certFile").asText(), 				"Let'sEncryptAuthorityX3.crt");
		assertEquals(exportedAPIConfig.get("caCerts").get(1).get("inbound").asBoolean(), 			false);
		assertEquals(exportedAPIConfig.get("caCerts").get(1).get("outbound").asBoolean(), 			true);
		
		assertEquals(exportedAPIConfig.get("caCerts").get(2).get("certFile").asText(), 				"DSTRootCAX3.crt");
		assertEquals(exportedAPIConfig.get("caCerts").get(2).get("inbound").asBoolean(), 			false);
		assertEquals(exportedAPIConfig.get("caCerts").get(2).get("outbound").asBoolean(), 			true);
		
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/risequipmentservicedev.nov.cloud.crt").exists(), "Certificate risequipmentservicedev.nov.cloud.crt is missing");
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/Let'sEncryptAuthorityX3.crt").exists(), "Certificate Let'sEncryptAuthorityX3.crt is missing");
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/DSTRootCAX3.crt").exists(), "Certificate DSTRootCAX3.crt is missing");
		
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/"+context.getVariable("exportAPIName")).exists(), "Exported Swagger-File is missing");
	}
}
