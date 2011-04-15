package edu.ucla.cens.mobilize.client.dataaccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

import edu.ucla.cens.mobilize.client.AndWellnessConstants;
import edu.ucla.cens.mobilize.client.dataaccess.request.DataPointFilterParams;
import edu.ucla.cens.mobilize.client.model.AuthorizationTokenQueryAwData;
import edu.ucla.cens.mobilize.client.model.CampaignConciseInfo;
import edu.ucla.cens.mobilize.client.model.CampaignDetailedInfo;
import edu.ucla.cens.mobilize.client.model.DataPointAwData;
import edu.ucla.cens.mobilize.client.model.ErrorAwData;
import edu.ucla.cens.mobilize.client.model.ErrorQueryAwData;
import edu.ucla.cens.mobilize.client.model.QueryAwData;
import edu.ucla.cens.mobilize.client.model.SurveyResponse;
import edu.ucla.cens.mobilize.client.model.UserInfo;
import edu.ucla.cens.mobilize.client.rpcservice.ApiException;
import edu.ucla.cens.mobilize.client.rpcservice.AuthenticationException;
import edu.ucla.cens.mobilize.client.rpcservice.NotLoggedInException;
import edu.ucla.cens.mobilize.client.rpcservice.ServerException;
import edu.ucla.cens.mobilize.client.rpcservice.ServerUnavailableException;
import edu.ucla.cens.mobilize.client.utils.MapUtils;

/**
 * Class for requesting data from the AndWellnessServer.
 * 
 * @note was previously "ServerAndWellnessRpcService"
 * 
 * @author jhicks 
 * @author vhajdik
 */
public class AndWellnessDataService implements DataService {
  RequestBuilder authorizationService;
  RequestBuilder dataPointService;
  RequestBuilder configurationService;

  private static Logger _logger = Logger.getLogger(AndWellnessDataService.class.getName());
  
  /**
   * Constructor initializes the various RequestBuilders to contact the AW server.
   * 
   * @note urls come from AndWellnessConstants.getXUrl() to enable using 
   * different urls for different deploy type (e.g., local for debug, 
   * remote for release)
   */
  public AndWellnessDataService() {
    authorizationService = new RequestBuilder(RequestBuilder.POST, URL.encode(AndWellnessConstants.getAuthorizationUrl()));
    authorizationService.setHeader("Content-Type", URL.encode("application/x-www-form-urlencoded"));
    dataPointService = new RequestBuilder(RequestBuilder.POST, URL.encode(AndWellnessConstants.getDataPointUrl()));
    dataPointService.setHeader("Content-Type", "application/x-www-form-urlencoded");
    configurationService = new RequestBuilder(RequestBuilder.POST, URL.encode(AndWellnessConstants.getConfigurationUrl()));
    configurationService.setHeader("Content-Type", "application/x-www-form-urlencoded");
  }
  
  /**
   * Contains all the possible error codes returned by the AndWellness server.
   */
  public static enum ErrorCode {
      E0101("0101", "JSON syntax error"),
      E0102("0102", "no data in message"),
      E0103("0103", "server error"),
      E0104("0104", "session expired"),
      E0200("0200", "authentication failed"),
      E0201("0201", "disabled user"),
      E0202("0202", "new account attempting to access a service without changing default password first"),
      E0300("0300", "missing JSON data"),
      E0301("0301", "unknown request type"),
      E0302("0302", "unknown phone version"),
      E0304("0304", "invalid campaign id");
      
      private final String errorCode;
      private final String errorDescription;
      
      ErrorCode(String code, String description) {
          errorCode = code;
          errorDescription = description;
      }
      
      public String getErrorCode() { return errorCode; }
      public String getErrorDesc() { return errorDescription; }
      
      /**
       * Returns the ErrorCode that has the passed in error code from the server.
       * 
       * @param err The error code from the server
       * @return The correct ErrorCode, NULL if not found.
       */
      public static ErrorCode translateServerError(String err) {
          // Loop over all ErrorCodes to find the right one.
          for (ErrorCode errCode : ErrorCode.values()) {
              if (err.equals(errCode.getErrorCode())) {
                  return errCode;
              }
          }
          
          return null;
      }
  }
  
  /**
   * Returns various RpcServiceExceptions based on the error codes.
   *
   * http://www.lecs.cs.ucla.edu/wikis/andwellness/index.php/AndWellness-Error-Handling
   * 
   * @param errorResponse The JSON error response from the server.
   * @return exception
   */
  private static Exception parseServerErrorResponse(String errorResponse) {
      Exception returnError = null;
      ErrorQueryAwData errorQuery = ErrorQueryAwData.fromJsonString(errorResponse);
      JsArray<ErrorAwData> errorList = errorQuery.getErrors();
      
      _logger.fine("Received an error response from the server, parsing");
      
      int numErrors = errorList.length();
      
      // Lets just throw the first error for now
      if (numErrors > 0) {
          ErrorCode errorCode = ErrorCode.translateServerError(errorList.get(0).getCode());
          
          switch (errorCode) {
          case E0103:
              returnError = new ServerException(errorCode.getErrorDesc());
              break;
          case E0104:
              returnError = new NotLoggedInException(errorCode.getErrorDesc());
              break;
          case E0200: 
          case E0201: 
          case E0202: 
              returnError = new AuthenticationException(errorCode.getErrorDesc());
              break;
          case E0300:
          case E0301:
          case E0302:
          case E0304:
              returnError = new ApiException(errorCode.getErrorDesc());
              break;
          default:
              returnError = new ServerException("Unknown server error.");
              break;
          }
      }
      
      return returnError;
  }
  
  
  /**
   * Convenience method for usual error handling. (If your request needs
   *   anything special, just roll your own.) To use this method, wrap it in a 
   *   try/catch and pass any exception to you callback.onFailure(throwable)
   * 
   * @param requestBuilder The RequestBuilder used to generate the response,
   *   needed for logging request url
   * @param response
   * @return Response text if request was successful. (This is the text you'll
   *   probably pass to your someclass.fromJsonString(responseText) method)
   * @throws Exception 
   */
  protected String getResponseText(RequestBuilder requestBuilder, 
                                 Response response) throws Exception {
    _logger.fine("Authentication server response: " + response.getText());
    
    String responseText = null;
    int statusCode = response.getStatusCode();
    if (200 == statusCode) {
        // Eval the response into JSON
        // (Hope this doesn't contain malicious JavaScript!)
        responseText = response.getText();
        
        // could throw JavaScriptException if server returns invalid JSON
        QueryAwData serverResponse = QueryAwData.fromJsonString(responseText); 
        String result = serverResponse.getResult();
        
        // Request completed successfully but server returned error message
        if ("failure".equals(result)) {
          throw parseServerErrorResponse(responseText);
        }

        // The only two recognized responses are "success" and "failure"
        if (! "success".equals(result)) {
          throw new ServerException("Invalid server result: " + result + 
                                    ". Should have been one of 'success' or 'failure'");
          
        }
    } else if (0 == statusCode) {
      // Server down or incorrect request url
      throw new ServerUnavailableException(requestBuilder.getUrl());
    }else {
      // NOTE(4/14/2011): josh says the server returns 404 for all server errors
      throw new ServerException("Request failed with status code: " + 
                                 statusCode + 
                                 ". Url was: " + requestBuilder.getUrl());
    }
    
    return responseText;

  }

  
  // TODO: store auth token as part of data class?
  
  /**
   * Sends the passed in username and password to the AW server.  Checks the server response
   * to determine whether the login succeeded or failed, and notifies the callback of such.
   * 
   * @param userName The user name to authenticate.
   * @param password The password for the user name.
   * @param callback The interface to handle the server response.
   */
  public void fetchAuthorizationToken(final String userName, String password,
          final AsyncCallback<AuthorizationTokenQueryAwData> callback) {
   
      // Setup the post parameters
      Map<String,String> parameters = new HashMap<String,String>();

      // params 
      parameters.put("user", userName);
      parameters.put("password", password);
      parameters.put("client", "2");  // Hack in client ID for now
      
      String postParams = MapUtils.translateToParameters(parameters);
      
      // is it ok that this logs the password? (security?)
      _logger.finest("Attempting authentication with parameters: " + postParams);
      
      // Send the username/password to the server.
      try {
          authorizationService.sendRequest(postParams, new RequestCallback() {
              // Error occurred, handle it here
              public void onError(Request request, Throwable exception) {
                  // Couldn't connect to server (could be timeout, SOP violation, etc.)   
                  callback.onFailure(new ServerException("Request to server timed out."));
              }
              
              // Eval the JSON into an overlay class and return
              public void onResponseReceived(Request request, Response response) {
                  String responseText = null;     
                  AuthorizationTokenQueryAwData result = null;
                  try {
                    responseText = getResponseText(authorizationService, response);
                    result = AuthorizationTokenQueryAwData.fromJsonString(responseText);
                    callback.onSuccess(result);
                  } catch (Exception exception) {
                    callback.onFailure(exception);
                  }
              }       
          });
      // Big error occured, handle it here
      } catch (RequestException e) {
        _logger.severe(e.getMessage());
        throw new ServerException("Cannot contact server.");     
      }
      
  }
  
  @Override
  public void fetchUserInfo(String username, AsyncCallback<UserInfo> callback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void fetchCampaignList(Map<String, List<String>> params,
      AsyncCallback<List<CampaignConciseInfo>> callback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void fetchCampaignDetail(String campaignId,
      AsyncCallback<CampaignDetailedInfo> callback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void fetchCampaignDetailList(List<String> campaignIds,
      AsyncCallback<List<CampaignDetailedInfo>> callback) {
    // TODO Auto-generated method stub
    
  }
  
  @Override
  public CampaignDetailedInfo getCampaignDetail(String campaignId) {
    // TODO Auto-generated method stub
    return null;
  }
  
  
  @Override
  public void deleteCampaign(String campaignId,
      AsyncCallback<ResponseDelete> asyncCallback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void fetchDataPoints(String campaignId, DataPointFilterParams params,
      AsyncCallback<List<DataPointAwData>> callback) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void fetchSurveyResponses(String campaignId,
      DataPointFilterParams params, AsyncCallback<List<SurveyResponse>> callback) {
    // TODO Auto-generated method stub
    
  }

  
}
