package com.cloudedit.dropbox.access;


import java.util.Map;
import java.net.URISyntaxException;
import java.security.SecureRandom;

import org.apache.commons.io.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.*;

import static spark.Spark.*;
import spark.*;


public class DropboxAuth 
{        
    static String APP_KEY ;
    static String APP_SECRET;
    static String strAccessToken = null;
    static String strTokenFile = null;
    
    // Generate a random string to use as a CSRF token.
    public static String generateCSRFToken() 
    {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        return Base64.encodeBase64URLSafeString(b);
    }

    public static String getRedirectURI(spark.Request request) throws URISyntaxException 
    {
        return new URIBuilder(request.url()).setPath("/callback").build().toString();
    }
    
    public static String getAccessToken() throws Exception
    {        
        AuthAccessToken();
        Thread.sleep(10000);
        System.out.println("Access Token:" + strAccessToken);
        return strAccessToken;
    }
    
    public static String getTokenFile()
    {
        return strTokenFile;
    }
    
    public static void AuthAccessToken()
    {
        AuthenticationToken token = new AuthenticationToken();
        APP_KEY = token.getAccessTokenStr();
        APP_SECRET = token.getAccessTokenSecretStr();

        // Route for when the user is redirected back to our app.
        // 判断当前系统是否支持Java AWT Desktop扩展
        if (java.awt.Desktop.isDesktopSupported()) 
        {
            try 
            {
                // 创建一个URI实例
                java.net.URI uri = java.net.URI.create("http://127.0.0.1:5000");
                // 获取当前系统桌面扩展
                java.awt.Desktop dp = java.awt.Desktop.getDesktop();
                // 判断系统桌面是否支持要执行的功能
                if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) 
                {
                    // 获取系统默认浏览器打开链接
                    dp.browse(uri);
                }
            } 
            catch (java.lang.NullPointerException e) 
            {
                // 此为uri为空时抛出异常
            } 
            catch (java.io.IOException e) 
            {
                // 此为无法获取系统默认浏览器
            }  
        }
        
        setPort(5000);

        // Main route when the app is initially loaded.
        get(new Route("/") 
        {
            @Override
            public Object handle(spark.Request request, Response response) {
                String csrfToken = generateCSRFToken();

                // Store the CSRF token in a cookie, to be checked in the callback.
                response.cookie("csrf", csrfToken);

                try {
                    // Redirect the user to authorize the app with Dropbox.
                    response.redirect(new URIBuilder("https://www.dropbox.com/1/oauth2/authorize")
                        .addParameter("client_id", APP_KEY)
                        .addParameter("response_type", "code")
                        .addParameter("redirect_uri", getRedirectURI(request))
                        .addParameter("state", csrfToken)
                        .build().toString());

                    return null;
                } catch (Exception e) {
                    return "ERROR: " + e.toString();
                }
            }
        });

        // Route for when the user is redirected back to our app.
        get(new Route("/callback") {
            @Override
            public Object handle(spark.Request request, Response response) 
            {
                // The CSRF token will only be used once.
                response.removeCookie("csrf");

                // If the CSRF token doesn't match, raise an error.
                if (!request.cookie("csrf").equals(request.queryParams("state"))) 
                {
                    halt(401, "Potential CSRF attack.");
                }

                // This is the authorization code from the OAuth flow.
                String code = request.queryParams("code");

                try 
                {
                    // Exchange the authorization code for an access token.
                    Map json = (Map) JSONValue.parse(
                        Request.Post("https://api.dropbox.com/1/oauth2/token")
                            .bodyForm(Form.form()
                                .add("code", code)
                                .add("grant_type", "authorization_code")
                                .add("redirect_uri", getRedirectURI(request))
                            .build())
                            .addHeader("Authorization", "Basic " + Base64.encodeBase64String((APP_KEY+":"+APP_SECRET).getBytes()))
                            .execute().returnContent().asString());
                    String accessToken = (String) json.get("access_token");
                    strAccessToken = accessToken;
                    // Call the /account/info API with the access token.
                    json = (Map) JSONValue.parse(
                        Request.Get("https://api.dropbox.com/1/account/info")
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .execute().returnContent().asString());

                    return String.format("Successfully authenticated as %s.", json.get("display_name"));
                } catch (Exception e) {
                    return "ERROR: " + e.toString();
                }
            }
        });
        
        get(new Route("/shutdown") 
        {
            @Override
            public Object handle(spark.Request request, Response response) 
            {
                System.out.println("/shutdown " + strAccessToken);
                return response;
        
            }
        });
    }
    
    //public static void main(String[] args) throws Exception 
    //{
    //    AuthAccessToken();
    //    Thread.sleep(5000);
    //    System.out.println("Access Token: " + strAccessToken);
    //}

    //public static void main(String[] args) throws IOException, DbxException 
    //{
    //    // Get your app key and secret from the Dropbox developers website.
    //
    //    AuthenticationToken token = new AuthenticationToken();
    //    APP_KEY = token.getAccessTokenStr();
    //    APP_SECRET = token.getAccessTokenSecretStr();
    //    
    //    DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
    //
    //    DbxRequestConfig config = new DbxRequestConfig("JavaTutorial/1.0",
    //        Locale.getDefault().toString());
    //    DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);
    //
    //    // Have the user sign in and authorize your app.
    //    String authorizeUrl = webAuth.start();
    //    
    //    System.out.println(authorizeUrl);
    //    
    //    @SuppressWarnings("resource")
    //    HttpClient httpclient = new DefaultHttpClient(); 
    //    try 
    //    {
    //        OAuthConsumer oAuthConsumer = new CommonsHttpOAuthConsumer(APP_KEY, APP_SECRET);
    //        oAuthConsumer.setTokenWithSecret(APP_KEY, APP_SECRET);
    //    
    //        HttpGet httpGetRequest = new HttpGet(authorizeUrl);
    //        oAuthConsumer.sign(httpGetRequest);
    //    
    //        HttpResponse httpResponse = httpclient.execute(httpGetRequest);
    // 
    //        System.out.println("----------------------------------------");
    //        System.out.println(httpResponse.getStatusLine());
    //        System.out.println("----------------------------------------");
    // 
    //        HttpEntity entity = httpResponse.getEntity();
    // 
    //        //byte[] buffer = new byte[1024];
    //        if (entity != null) 
    //        {
    //            InputStream inputStream = entity.getContent();
    //            try 
    //            { 
    //                inputStream.close(); 
    //            } 
    //            catch (Exception ignore) 
    //            {
    //                
    //            }
    //        }
    //    } 
    //    catch (Exception e) 
    //    {
    //        e.printStackTrace();
    //    } 
    //    finally 
    //    {
    //        httpclient.getConnectionManager().shutdown();
    //    }
    //    
    //    
    //    
//  //      BareBonesBrowserLaunch.openURL(authorizeUrl);  
    //    System.out.println("1. Go to: " + authorizeUrl);
    //    System.out.println("2. Click \"Allow\" (you might have to log in first)");
    //    System.out.println("3. Copy the authorization code.");
    //
    //    //@SuppressWarnings({ "deprecation", "resource" })
	//	//HttpClient httpclient = new DefaultHttpClient();  
    //    //try {  
    //    //    //以get方式请求网页http://www.yshjava.cn  
    //    //    HttpGet httpget = new HttpGet(authorizeUrl);  
    //    //    //打印请求地址  
    //    //    System.out.println("executing request " + httpget.getURI());  
    //    //    //创建响应处理器处理服务器响应内容  
    //    //    @SuppressWarnings("rawtypes")
	//	//	ResponseHandler responseHandler = new BasicResponseHandler();  
    //    //    //执行请求并获取结果  
    //    //    String responseBody = (String) httpclient.execute(httpget, responseHandler);  
    //    //    System.out.println("----------------------------------------");  
    //    //    System.out.println(responseBody);  
    //    //    System.out.println("----------------------------------------");  
    //    //} finally {  
    //    //    //关闭连接管理器  
    //    //    httpclient.getConnectionManager().shutdown();  
    //    //}  
    //    //
    //    //String code = new BufferedReader(new InputStreamReader(System.in)).readLine();
    //    //
    //    //Document doc = Jsoup.connect("https://www.dropbox.com/1/oauth2/authorize_submit").get();
    //    //Element newsHeadlines = doc.select("div#auth-code").first();
    //    //System.out.println(newsHeadlines.text());
    //    //
    //    //if (code == null) {
    //    //    System.exit(1); return;
    //    //}
    //    //code = code.trim();
    //    //
    //    //DbxAuthFinish authFinish;
    //    //try {
    //    //    authFinish = webAuth.finish(code);
    //    //}
    //    //catch (DbxException ex) {
    //    //    System.err.println("Error in DbxWebAuth.start: " + ex.getMessage());
    //    //    System.exit(1); return;
    //    //}
    //    //
    //    //System.out.println("Authorization complete.");
    //    //System.out.println("- User ID: " + authFinish.userId);
    //    //System.out.println("- Access Token: " + authFinish.accessToken);
    //    //
    //    //// This will fail if the user enters an invalid authorization code.
    //    //String accessToken = authFinish.accessToken;
    //    //
    //    //DbxClient client = new DbxClient(config, accessToken);
    //    //
    //    //System.out.println("Linked account: " + client.getAccountInfo().displayName);
    //    //
    //    //File inputFile = new File("working-draft.txt");
    //    //FileInputStream inputStream = new FileInputStream(inputFile);
    //    //try {
    //    //    DbxEntry.File uploadedFile = client.uploadFile("/magnum-opus.txt",
    //    //        DbxWriteMode.add(), inputFile.length(), inputStream);
    //    //    System.out.println("Uploaded: " + uploadedFile.toString());
    //    //} finally {
    //    //    inputStream.close();
    //    //}
    //    //
    //    //DbxEntry.WithChildren listing = client.getMetadataWithChildren("/");
    //    //System.out.println("Files in the root path:");
    //    //for (DbxEntry child : listing.children) {
    //    //    System.out.println("	" + child.name + ": " + child.toString());
    //    //}
    //    //
    //    //FileOutputStream outputStream = new FileOutputStream("magnum-opus.txt");
    //    //try {
    //    //    DbxEntry.File downloadedFile = client.getFile("/magnum-opus.txt", null,
    //    //        outputStream);
    //    //    System.out.println("Metadata: " + downloadedFile.toString());
    //    //} finally {
    //    //    outputStream.close();
    //    //}
    //}
}
