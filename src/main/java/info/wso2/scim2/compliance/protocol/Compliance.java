/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.wso2.scim2.compliance.protocol;

import info.wso2.scim2.compliance.entities.Result;
import info.wso2.scim2.compliance.entities.Statistics;
import info.wso2.scim2.compliance.entities.TestResult;
import info.wso2.scim2.compliance.exception.ComplianceException;
import info.wso2.scim2.compliance.exception.CriticalComplianceException;
import info.wso2.scim2.compliance.objects.SCIMServiceProviderConfig;
import info.wso2.scim2.compliance.pdf.PDFGenerator;
import info.wso2.scim2.compliance.tests.*;
import info.wso2.scim2.compliance.tests.BulkTest;
import info.wso2.scim2.compliance.utils.ComplianceConstants;
import org.apache.commons.validator.routines.UrlValidator;
import org.wso2.charon3.core.exceptions.CharonException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;

@Path("/test2")
public class Compliance extends HttpServlet {

    @Context ServletContext context;

    private static final long serialVersionUID = 1L;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Result runTests(@FormParam(ComplianceConstants.RequestCodeConstants.URL) String url,
                           @FormParam(ComplianceConstants.RequestCodeConstants.USERNAME) String username,
                           @FormParam(ComplianceConstants.RequestCodeConstants.PASSWORD) String password,
                           @FormParam(ComplianceConstants.RequestCodeConstants.CLIENT_ID) String clientId,
                           @FormParam(ComplianceConstants.RequestCodeConstants.CLIENT_SECRET)
                                   String clientSecret,
                           @FormParam(ComplianceConstants.RequestCodeConstants.AUTHORIZATION_SERVER)
                                   String authorizationServer,
                           @FormParam(ComplianceConstants.RequestCodeConstants.AUTHORIZATION_HEADER)
                                   String authorizationHeader,
                           @FormParam(ComplianceConstants.RequestCodeConstants.AUTHORIZATION_METHOD)
                                   String authMethod)
            throws InterruptedException, ServletException {

        if (url == null || url.isEmpty()) {
            ComplianceException BadRequestException = new ComplianceException();
            BadRequestException.setDetail("URL can not be empty.");
            return (new Result(BadRequestException.getDetail()));
        }

        //TODO : Add other authentication logging checks as well.
//        if ((username.isEmpty() || password.isEmpty())) {
//            ComplianceException BadRequestException = new ComplianceException();
//            BadRequestException.setDetail("Authorization with service provider failed.");
//            return (new Result(BadRequestException.getDetail()));
//        }

        // This is to keep the test results
        ArrayList<TestResult> results = new ArrayList<TestResult>();

        // Valid schemas
        String[] schemes = {ComplianceConstants.RequestCodeConstants.HTTP,
                ComplianceConstants.RequestCodeConstants.HTTPS};

        UrlValidator urlValidator = new UrlValidator(schemes);

        //TODO : Remove the comment when deployed
        /*
        if (!urlValidator.isValid(url)) {
            ComplianceException BadRequestException = new ComplianceException();
            BadRequestException.setDetail("Invalid URL had been entered.");
            return (new Result(BadRequestException.getDetail()));
        }
        */

        // create a complianceTestMetaDataHolder to use to hold the test suite configurations
        ComplianceTestMetaDataHolder complianceTestMetaDataHolder = new ComplianceTestMetaDataHolder();
        complianceTestMetaDataHolder.setUrl(url);
        complianceTestMetaDataHolder.setUsername(username);
        complianceTestMetaDataHolder.setPassword(password);
        complianceTestMetaDataHolder.setAuthorization_server(authorizationServer);
        complianceTestMetaDataHolder.setAuthorization_header(authorizationHeader);
        complianceTestMetaDataHolder.setAuthorization_method(authMethod);
        complianceTestMetaDataHolder.setClient_id(clientId);
        complianceTestMetaDataHolder.setClient_secret(clientSecret);
        complianceTestMetaDataHolder.setScimServiceProviderConfig(new SCIMServiceProviderConfig());



        //SCIMUser Test
        UserTest userTest = new UserTest(complianceTestMetaDataHolder);
        ArrayList<TestResult> userTestResults = null;
        try {
            userTestResults = userTest.performTest();
        } catch (ComplianceException e) {
            return (new Result(e.getDetail()));
        }
        for (TestResult testResult : userTestResults) {
            results.add(testResult);
        }

        //SCIMGroup Test
        GroupTest groupTest = new GroupTest(complianceTestMetaDataHolder);
        ArrayList<TestResult> groupTestResults = null;
        try {
            groupTestResults = groupTest.performTest();
        } catch (ComplianceException e) {
            return (new Result(e.getDetail()));
        }
        for (TestResult testResult : groupTestResults) {
            results.add(testResult);
        }

        // Filter Test
        FilterTest filterTest = new FilterTest(complianceTestMetaDataHolder);
        ArrayList<TestResult> filterTestResults = new ArrayList<>();
        try {
            filterTestResults = filterTest.performTest();
        } catch (ComplianceException e) {
            return (new Result(e.getDetail()));
        }
        for (TestResult testResult : filterTestResults) {
            results.add(testResult);
        }

        Statistics statistics = new Statistics();
        for (TestResult result : results) {

            switch (result.getStatus()) {
                case TestResult.ERROR:
                    statistics.incFailed();
                    break;
                case TestResult.SUCCESS:
                    statistics.incSuccess();
                    break;
                case TestResult.SKIPPED:
                    statistics.incSkipped();
                    break;
            }
        }
        Result finalResults = new Result(statistics, results);

        //generate pdf results sheet
        if(context!=null) {
            try {
                String fullPath = context.getRealPath("/WEB-INF");
                System.out.println(fullPath);
                String reportURL = PDFGenerator.GeneratePDFResults(finalResults, fullPath);
                //TODO : Change this on server
                finalResults.setReportLink("file://" + reportURL);
            } catch (IOException e) {
                return (new Result(e.getMessage()));
            }
        }
        return finalResults;
    }

    public void skippedTests(){

        //Me Test
//        MeTest meTest = new MeTest(complianceTestMetaDataHolder);
//        ArrayList<TestResult> meTestResults = null;
//        try {
//            meTestResults = meTest.performTest();
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }
//        for (TestResult testResult : meTestResults) {
//            results.add(testResult);
//        }

        // /ResourceType Test
//        ResourceTypeTest resourceTypeTest = new ResourceTypeTest(complianceTestMetaDataHolder);
//        try {
//            ArrayList<TestResult> resourceTypeResults = resourceTypeTest.performTest();
//            for(TestResult result : resourceTypeResults){
//                results.add(result);
//            }
//        } catch (CriticalComplianceException e) {
//            results.add(e.getResult());
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }
//        //List Test
//        ListTest listTest = new ListTest(complianceTestMetaDataHolder);
//        ArrayList<TestResult> listTestResults = new ArrayList<>();
//        try {
//            listTestResults = listTest.performTest();
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }
//        for (TestResult testResult : listTestResults) {
//            results.add(testResult);
//        }

        //        try {
//            // Schema Test
//            SchemaTest schemaTest = new SchemaTest(complianceTestMetaDataHolder);
//            ArrayList<TestResult> schemaResults = schemaTest.performTest();
//            for(TestResult result : schemaResults){
//                results.add(result);
//            }
//        } catch (CriticalComplianceException e) {
//            results.add(e.getResult());
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }
//
//
//        try {
//            // Schema Test
//            ConfigTest configTest = new ConfigTest(complianceTestMetaDataHolder);
//            ArrayList<TestResult> configResults = configTest.performTest();
//            for(TestResult result : configResults){
//                results.add(result);
//            }
//        } catch (CriticalComplianceException e) {
//            results.add(e.getResult());
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }

        // Pagination Test
//        PaginationTest paginationTest = new PaginationTest(complianceTestMetaDataHolder);
//        ArrayList<TestResult> paginationTestResults = new ArrayList<>();
//        try {
//            paginationTestResults = paginationTest.performTest();
//        } catch (ComplianceException e) {
//            return (new Result(e.getDetail()));
//        }
//        for (TestResult testResult : paginationTestResults) {
//            results.add(testResult);
//        }

        // Sort Test
//        SortTest sortTest = new SortTest(complianceTestMetaDataHolder);
//        ArrayList<TestResult> sortTestResults = new ArrayList<>();
//        try {
//            if (complianceTestMetaDataHolder.getScimServiceProviderConfig().getSortSupported()){
//                try {
//                    sortTestResults = sortTest.performTest();
//                } catch (ComplianceException e) {
//                    return (new Result(e.getDetail()));
//                }
//                for (TestResult testResult : sortTestResults) {
//                    results.add(testResult);
//                }
//            } else {
//                results.add(new TestResult(TestResult.SKIPPED, "Sort Test", "Skipped",null));
//            }
//        } catch (CharonException e) {
//            return (new Result(e.getDetail()));
//        }

        // Bulk Test
//        BulkTest bulkTest = new BulkTest(complianceTestMetaDataHolder);
//        ArrayList<TestResult> bulkTestResults = new ArrayList<>();
//        try {
//            if (complianceTestMetaDataHolder.getScimServiceProviderConfig().getBulkSupported()){
//                try {
//                    bulkTestResults = bulkTest.performTest();
//                } catch (ComplianceException e) {
//                    return (new Result(e.getDetail()));
//                }
//                for (TestResult testResult : bulkTestResults) {
//                    results.add(testResult);
//                }
//            } else {
//                results.add(new TestResult(TestResult.SKIPPED, "Bulk Test", "Skipped",null));
//            }
//        } catch (CharonException e) {
//            return (new Result(e.getDetail()));
//        }
    }
}
