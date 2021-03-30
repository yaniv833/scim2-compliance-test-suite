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
package info.wso2.scim2.compliance.tests;


import info.wso2.scim2.compliance.entities.TestResult;
import info.wso2.scim2.compliance.exception.ComplianceException;
import info.wso2.scim2.compliance.exception.CriticalComplianceException;
import info.wso2.scim2.compliance.exception.GeneralComplianceException;
import info.wso2.scim2.compliance.httpclient.HTTPClient;
import info.wso2.scim2.compliance.protocol.ComplianceTestMetaDataHolder;
import info.wso2.scim2.compliance.protocol.ComplianceUtils;
import info.wso2.scim2.compliance.tests.common.ResponseValidateTests;
import info.wso2.scim2.compliance.utils.ComplianceConstants;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wso2.charon3.core.encoder.JSONDecoder;
import org.wso2.charon3.core.exceptions.BadRequestException;
import org.wso2.charon3.core.exceptions.CharonException;
import org.wso2.charon3.core.exceptions.InternalErrorException;
import org.wso2.charon3.core.objects.Group;
import org.wso2.charon3.core.objects.User;
import org.wso2.charon3.core.schema.SCIMResourceSchemaManager;
import org.wso2.charon3.core.schema.SCIMResourceTypeSchema;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class consists of Filtering test cases for both users and groups.
 */
public class FilterTest {

    private ComplianceTestMetaDataHolder complianceTestMetaDataHolder;
    private String usersURL  = null;
    private String groupURL  = null;
    private HashMap<String,String> groupIDs = new HashMap<>();
    private HashMap<String,String> userIDs = new HashMap<>();

    /**
     * Initialize.
     * @param complianceTestMetaDataHolder
     */
    public FilterTest(ComplianceTestMetaDataHolder complianceTestMetaDataHolder) {

        this.complianceTestMetaDataHolder = complianceTestMetaDataHolder;

        usersURL =  complianceTestMetaDataHolder.getUrl() +
                ComplianceConstants.TestConstants.USERS_ENDPOINT;

        groupURL =  complianceTestMetaDataHolder.getUrl() +
                ComplianceConstants.TestConstants.GROUPS_ENDPOINT;
    }

    /**
     * Method to handle test case.
     * @return
     * @throws ComplianceException
     */
    public ArrayList<TestResult> performTest() throws ComplianceException {
        //perform filter tests
        ArrayList<TestResult> testResults = new ArrayList<>();
        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            TestCase annos = method.getAnnotation(TestCase.class);
            if (annos != null) {
                try {
                    testResults = (ArrayList<TestResult>) method.invoke(this);
                } catch (InvocationTargetException e) {
                    try{
                        throw  e.getCause();
                    } catch (ComplianceException e1) {
                        throw e1;
                    } catch (GeneralComplianceException e1){
                        testResults.add(e1.getResult());
                    } catch (Throwable throwable) {
                        throw new ComplianceException("Error occurred in Bulk Test.");
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }
        }
        return testResults;
    }

    /**
     * Perform user and group filter tests.
     * @return
     * @throws ComplianceException
     */
    @TestCase
    public ArrayList<TestResult> GetFilterTest() throws ComplianceException {
        ArrayList<TestResult> testResults = new ArrayList<>();
        try {
            CreateTestsUsers();
            testResults.add(FilterUsers());
        } catch (GeneralComplianceException e){
            testResults.add(e.getResult());
        }
        try {
            CreateTestsGroups();
            testResults.add(FilterGroups());
        } catch (GeneralComplianceException e){
            testResults.add(e.getResult());
        }
        return testResults;
    }

    /**
     * Filter Users.
     * @return
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private TestResult FilterUsers()
            throws ComplianceException, GeneralComplianceException {
        String value = (new ArrayList<>(userIDs.values())).get(0);
        String filter = URLEncoder.encode("userName eq \"" + value + "\"", StandardCharsets.UTF_8);
        HttpGet method = new HttpGet(usersURL +"?filter="+filter);

        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpGet) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests = new ArrayList<>();
        try {
            response = client.execute(method);
            // Read the response body.
            responseString = new BasicResponseHandler().handleResponse(response);
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " " +
                    response.getStatusLine().getReasonPhrase();

        } catch (Exception e) {
            // Read the response body.
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();
            //clean up task
            for (String id : userIDs.keySet()) {
                CleanUpUser(id);
            }
            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                    "Could not filter the users at url " + usersURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }

        if (response.getStatusLine().getStatusCode() == 200) {

            //obtain the schema corresponding to user
            SCIMResourceTypeSchema schema = SCIMResourceSchemaManager.getInstance().getUserResourceSchema();
            JSONDecoder jsonDecoder = new JSONDecoder();
            ArrayList<User> userList = new ArrayList<>();
            try {
                JSONObject jsonObj = new JSONObject(responseString);
                JSONArray usersArray = jsonObj.getJSONArray("Resources");
                JSONObject tmp;
                for (int i = 0; i < usersArray.length(); i++) {
                    tmp = usersArray.getJSONObject(i);
                    userList.add((User) jsonDecoder.decodeResource(tmp.toString(), schema, new User()));
                    try {
                        ResponseValidateTests.runValidateTests(userList.get(i), schema,
                                null, null, method,
                                responseString, headerString, responseStatus, subTests);

                    } catch (BadRequestException | CharonException e) {
                        //clean up task
                        for (String id : userIDs.keySet()) {
                            CleanUpUser(id);
                        }
                        throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                                "Response Validation Error",
                                ComplianceUtils.getWire(method, responseString, headerString,
                                        responseStatus, subTests)));
                    }
                }
            } catch (JSONException e) {
                //clean up task
                for (String id : userIDs.keySet()) {
                    CleanUpUser(id);
                }
                throw new ComplianceException(500, "Error in decoding the returned filter resource.");

            } catch (BadRequestException | CharonException | InternalErrorException e) {
                //clean up task
                for (String id : userIDs.keySet()) {
                    CleanUpUser(id);
                }
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                        "Could not decode the server response",
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
            // check for all created groups
            try {
                CheckForListOfUsersReturned(userList, method, responseString, headerString,
                        responseStatus, subTests);
            } catch (CharonException e) {
                throw new ComplianceException(500, "Could not get the created user id");
            }
            //clean up task
            for (String id : userIDs.keySet()) {
                CleanUpUser(id);
            }
            return new TestResult
                    (TestResult.SUCCESS, "Filter Users",
                            "", ComplianceUtils.getWire(method, responseString,
                            headerString, responseStatus, subTests));
        } else {
            return new TestResult
                    (TestResult.ERROR, "Filter Users",
                            "", ComplianceUtils.getWire(method, responseString,
                            headerString, responseStatus, subTests));
        }
    }

    /**
     * Clean up task to delete the created users.
     * @param id
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private void CleanUpUser(String id) throws ComplianceException, GeneralComplianceException {

        String deleteUserURL = usersURL + "/" + id;

        HttpDelete method = new HttpDelete(deleteUserURL);

        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpDelete) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests =  new ArrayList<>();
        try {

            response = client.execute(method);
            // Read the response body.
            responseString = new BasicResponseHandler().handleResponse(response);
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();

        } catch (Exception e) {
            // Read the response body.
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();

            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                    "Could not delete the default user at url " + deleteUserURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }

        if (response.getStatusLine().getStatusCode() != 204) {
            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                    "Could not delete the default user at url " + deleteUserURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }
    }

    /**
     * Validation test to check whether the response contains all the expected users.
     * @param userList
     * @param method
     * @param responseString
     * @param headerString
     * @param responseStatus
     * @param subTests
     * @throws CharonException
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private void CheckForListOfUsersReturned(ArrayList<User> userList,
                                             HttpGet method, String responseString,
                                             String headerString, String responseStatus,
                                             ArrayList<String> subTests) throws CharonException,
            ComplianceException, GeneralComplianceException {

        subTests.add(ComplianceConstants.TestConstants.FILTER_CONTENT_TEST);
        String value = (new ArrayList<>(userIDs.values())).get(0);
        for (User user : userList) {
            if (!value.equalsIgnoreCase(user.getUserName())){
                //clean up task
                for (String id : userIDs.keySet()) {
                    CleanUpUser(id);
                }
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                        "Response does not contain the expected users",
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
        }
    }

    /**
     * Create test users.
     * @return
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private HashMap<String, String> CreateTestsUsers() throws ComplianceException, GeneralComplianceException {

        ArrayList<String> definedUsers = new ArrayList<>();
        definedUsers.add("{\"password\": \"7019asd81\",\"userName\": \"AbrTkAA31\"}");
        definedUsers.add("{\"password\": \"7019asd82\",\"userName\": \"UttEdHt32\"}");
        definedUsers.add("{\"password\": \"7019asd83\",\"userName\": \"KKTQwhr33\"}");

        HttpPost method = new HttpPost(usersURL);
        //create users
        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpPost) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests =  new ArrayList<>();
        for (int i = 0 ; i < 3 ; i++) {
            try {
                //create the group
                HttpEntity entity = new ByteArrayEntity(definedUsers.get(i).getBytes("UTF-8"));
                method.setEntity(entity);
                response = client.execute(method);
                // Read the response body.
                responseString = new BasicResponseHandler().handleResponse(response);
                responseStatus = String.valueOf(response.getStatusLine().getStatusCode());
                if (responseStatus.equals("201")) {
                    //obtain the schema corresponding to group
                    SCIMResourceTypeSchema schema = SCIMResourceSchemaManager.getInstance().getUserResourceSchema();

                    JSONDecoder jsonDecoder = new JSONDecoder();
                    User user = null;
                    try {
                        user = (User) jsonDecoder.decodeResource(responseString, schema, new User());
                    } catch (BadRequestException | CharonException | InternalErrorException e) {
                        throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                                "Could not decode the server response of users create.",
                                ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
                    }
                    userIDs.put(user.getId(), user.getUserName());
                }

            } catch (Exception e) {
                // Read the response body.
                Header[] headers = response.getAllHeaders();
                for (Header header : headers) {
                    headerString += header.getName() + " : " + header.getValue() + "\n";
                }
                responseStatus = response.getStatusLine().getStatusCode() + " "
                        + response.getStatusLine().getReasonPhrase();
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Users",
                        "Could not create default users at url " + usersURL,
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
        }
        return userIDs;
    }

    /**
     * Create test groups.
     * @return
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private HashMap<String, String> CreateTestsGroups () throws ComplianceException, GeneralComplianceException {

        ArrayList<String> definedGroups = new ArrayList<>();
        definedGroups.add("{\"displayName\": \"EYtXcD31\"}");
        definedGroups.add("{\"displayName\": \"BktqER32\"}");
        definedGroups.add("{\"displayName\": \"ZwLtOP33\"}");

        HttpPost method = new HttpPost(groupURL);
        //create groups
        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpPost) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests =  new ArrayList<>();
        for (int i = 0 ; i < 3 ; i++) {
            try {
                //create the group
                HttpEntity entity = new ByteArrayEntity(definedGroups.get(i).getBytes("UTF-8"));
                method.setEntity(entity);
                response = client.execute(method);
                // Read the response body.
                responseString = new BasicResponseHandler().handleResponse(response);
                responseStatus = String.valueOf(response.getStatusLine().getStatusCode());
                if (responseStatus.equals("201")) {
                    //obtain the schema corresponding to group
                    SCIMResourceTypeSchema schema = SCIMResourceSchemaManager.getInstance().getGroupResourceSchema();

                    JSONDecoder jsonDecoder = new JSONDecoder();
                    Group group = null;
                    try {
                        group = (Group) jsonDecoder.decodeResource(responseString, schema, new Group());
                    } catch (BadRequestException | CharonException | InternalErrorException e) {
                        throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                                "Could not decode the server response of groups create.",
                                ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
                    }
                    groupIDs.put(group.getId(), group.getDisplayName());
                }

            } catch (Exception e) {
                // Read the response body.
                Header[] headers = response.getAllHeaders();
                for (Header header : headers) {
                    headerString += header.getName() + " : " + header.getValue() + "\n";
                }
                responseStatus = response.getStatusLine().getStatusCode() + " "
                        + response.getStatusLine().getReasonPhrase();
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                        "Could not create default groups at url " + groupURL,
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
        }
        return groupIDs;
    }

    /**
     * Method to filter groups.
     * @return
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private TestResult FilterGroups()
            throws ComplianceException, GeneralComplianceException {
        String value = (new ArrayList<>(groupIDs.values())).get(0);
        String filter = URLEncoder.encode("displayName eq \"" + value + "\"", StandardCharsets.UTF_8);

        HttpGet method = new HttpGet(groupURL +"?filter="+filter);

        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpGet) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests = new ArrayList<>();
        try {
            response = client.execute(method);
            // Read the response body.
            responseString = new BasicResponseHandler().handleResponse(response);
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " " +
                    response.getStatusLine().getReasonPhrase();

        } catch (Exception e) {
            // Read the response body.
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();
            //clean up task
            for (String id : groupIDs.keySet()) {
                CleanUpGroup(id);
            }
            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                    "Could not filter the groups at url " + groupURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }

        if (response.getStatusLine().getStatusCode() == 200) {

            //obtain the schema corresponding to group
            SCIMResourceTypeSchema schema = SCIMResourceSchemaManager.getInstance().getGroupResourceSchema();
            JSONDecoder jsonDecoder = new JSONDecoder();
            ArrayList<Group> groupList = new ArrayList<>();
            try {
                JSONObject jsonObj = new JSONObject(responseString);
                JSONArray groupsArray = jsonObj.getJSONArray("Resources");
                JSONObject tmp;
                for (int i = 0; i < groupsArray.length(); i++) {
                    tmp = groupsArray.getJSONObject(i);
                    groupList.add((Group) jsonDecoder.decodeResource(tmp.toString(), schema, new Group()));
                    try {
                        ResponseValidateTests.runValidateTests(groupList.get(i), schema,
                                null, null, method,
                                responseString, headerString, responseStatus, subTests);

                    } catch (BadRequestException | CharonException e) {
                        //clean up task
                        for (String id : groupIDs.keySet()) {
                            CleanUpGroup(id);
                        }
                        throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                                "Response Validation Error",
                                ComplianceUtils.getWire(method, responseString, headerString,
                                        responseStatus, subTests)));
                    }
                }
            } catch (JSONException e) {
                //clean up task
                for (String id : groupIDs.keySet()) {
                    CleanUpGroup(id);
                }
                throw new ComplianceException(500, "Error in decoding the returned filter resource.");

            } catch (BadRequestException | CharonException | InternalErrorException e) {
                //clean up task
                for (String id : groupIDs.keySet()) {
                    CleanUpGroup(id);
                }
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                        "Could not decode the server response",
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
            // check for all created groups
            try {
                CheckForListOfGroupsReturned(groupList, method, responseString, headerString, responseStatus, subTests);
            } catch (CharonException e) {
                //clean up task
                for (String id : groupIDs.keySet()) {
                    CleanUpGroup(id);
                }
                throw new ComplianceException(500, "Could not get the created group id");
            }
            //clean up task
            for (String id : groupIDs.keySet()) {
                CleanUpGroup(id);
            }
            return new TestResult
                    (TestResult.SUCCESS, "Filter Groups",
                            "", ComplianceUtils.getWire(method, responseString,
                            headerString, responseStatus, subTests));
        } else {
            //clean up task
            for (String id : groupIDs.keySet()) {
                CleanUpGroup(id);
            }
            return new TestResult
                    (TestResult.ERROR, "Filter Groups",
                            "", ComplianceUtils.getWire(method, responseString,
                            headerString, responseStatus, subTests));
        }
    }


    /**
     * Validation test to check whether the response contains all the expected groups.
     * @param returnedGroups
     * @param method
     * @param responseString
     * @param headerString
     * @param responseStatus
     * @param subTests
     * @throws CharonException
     * @throws ComplianceException
     * @throws GeneralComplianceException
     */
    private void CheckForListOfGroupsReturned(ArrayList<Group> returnedGroups,
                                              HttpGet method, String responseString,
                                              String headerString, String responseStatus,
                                              ArrayList<String> subTests)
            throws CharonException, ComplianceException, GeneralComplianceException {
        subTests.add(ComplianceConstants.TestConstants.FILTER_CONTENT_TEST);

        String value = (new ArrayList<>(groupIDs.values())).get(0);
        for (Group group : returnedGroups) {
            if (!value.equals(group.getDisplayName())){
                //clean up task
                for (String id : groupIDs.keySet()) {
                    CleanUpGroup(id);
                }
                throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                        "Response does not contain the expected groups",
                        ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
            }
        }
    }

    /**
     * Clean up the created groups after the test.
     * @param id
     * @throws GeneralComplianceException
     * @throws ComplianceException
     */
    private void CleanUpGroup (String id) throws GeneralComplianceException, ComplianceException {

        String deleteGroupURL = groupURL + "/" + id;

        HttpDelete method = new HttpDelete(deleteGroupURL);

        HttpClient client = HTTPClient.getHttpClient();

        method = (HttpDelete) HTTPClient.setAuthorizationHeader(complianceTestMetaDataHolder, method);
        method.setHeader("Accept", "application/json");

        HttpResponse response = null;
        String responseString = "";
        String headerString = "";
        String responseStatus = "";
        ArrayList<String> subTests =  new ArrayList<>();
        try {

            response = client.execute(method);
            // Read the response body.
            responseString = new BasicResponseHandler().handleResponse(response);
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();

        } catch (Exception e) {
            // Read the response body.
            //get all headers
            Header[] headers = response.getAllHeaders();
            for (Header header : headers) {
                headerString += header.getName() + " : " + header.getValue() + "\n";
            }
            responseStatus = response.getStatusLine().getStatusCode() + " "
                    + response.getStatusLine().getReasonPhrase();

            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                    "Could not delete the default group at url " + deleteGroupURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }

        if (response.getStatusLine().getStatusCode() != 204) {
            throw new GeneralComplianceException(new TestResult(TestResult.ERROR, "Filter Groups",
                    "Could not delete the default group at url " + deleteGroupURL,
                    ComplianceUtils.getWire(method, responseString, headerString, responseStatus, subTests)));
        }
    }
}
