package com.robomwm.thetranslator;

/**
 * Created on 5/26/2018.
 *
 * @author RoboMWM
 */
import com.google.gson.Gson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


//Basically snippets from https://docs.microsoft.com/en-us/azure/cognitive-services/translator/quickstarts/java
//Cuz unless I'm blind I can't find a nice Java API that uses a non-deprecated API...
public class Translate
{
    static String subscriptionKey = "ENTER KEY HERE";

    private static final String host = "https://api.cognitive.microsofttranslator.com";
    private static final String languagePath = "/languages?api-version=3.0";
    private static final String translatePath = "/translate?api-version=3.0";


    public static void setSubscriptionKey(String key)
    {
        subscriptionKey = key;
    }

    public static Map<String, String> getLanguages() throws Exception {
        URL url = new URL (host + languagePath + "&scope=translation");

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);

        StringBuilder response = new StringBuilder ();
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));

        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        Map<String, String> pairings = new HashMap<>();

        JSONObject json = (JSONObject)new JSONParser().parse(response.toString());
        json = (JSONObject)json.get("translation");
        for (Object codeObject : json.keySet())
        {
            String key = (String) codeObject;
            JSONObject code = (JSONObject)json.get(key);
            pairings.put(key, (String)code.get("nativeName"));
        }

        return pairings;
    }



    public static String post(URL url, String content) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", content.length() + "");
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
        connection.setDoOutput(true);

        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        byte[] encoded_content = content.getBytes("UTF-8");
        wr.write(encoded_content, 0, encoded_content.length);
        wr.flush();
        wr.close();

        StringBuilder response = new StringBuilder ();
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return response.toString();
    }

    public static class RequestBody {
        String Text;

        public RequestBody(String text) {
            this.Text = text;
        }
    }

    public class TranslatedText
    {
        private String original;
        private String text;
        private float score;

        TranslatedText(String original, String translated, float score)
        {
            this.original = original;
            this.text = translated;
            this.score = score;
        }
    }

    public static String translate(String message, String from, String to) throws Exception {

        String params = "&to=" + to + "&profanityAction=Deleted";
        if (from != null)
            params += "&from=" + from;

        URL url = new URL (host + translatePath + params);

        List<RequestBody> objList = new ArrayList<>();
        objList.add(new RequestBody(message));
        String content = new Gson().toJson(objList);

        JSONArray jsonResponse = (JSONArray)new JSONParser().parse(post(url, content));
        JSONObject json = (JSONObject)jsonResponse.get(0);
        JSONArray translations = (JSONArray)json.get("translations");
        JSONObject jsonObject = (JSONObject)translations.get(0);

        return (String)jsonObject.get("text");
    }

    public static class DetectResult
    {
        private String language;
        private float score;

        DetectResult(String language, float score)
        {
            this.language = language;
            this.score = score;
        }

        public String getLanguage()
        {
            return language;
        }

        public float getScore()
        {
            return score;
        }
    }

    public static DetectResult detect(String message) throws Exception
    {
        String path = "/detect?api-version=3.0";
        URL url = new URL (host + path);

        List<RequestBody> objList = new ArrayList<>();
        objList.add(new RequestBody(message));
        String content = new Gson().toJson(objList);

        JSONArray jsonResponse = (JSONArray)new JSONParser().parse(post(url, content));
        JSONObject json = (JSONObject)jsonResponse.get(0);


        return new DetectResult((String)json.get("language"), (float)json.get("score"));
    }

}
