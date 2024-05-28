package fr.openent.moodle.helper;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;
import org.entcore.common.controller.ControllerHelper;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.openent.moodle.Moodle.moodleConfig;

public class HttpClientHelper extends ControllerHelper {


    public HttpClientHelper() {
        super();
    }

    /**
     * Create default HttpClient
     * @return new HttpClient
     */
    public static HttpClient createHttpClient(Vertx vertx, JsonObject moodleClient) {
        boolean setSsl = true;
        try {
            setSsl = "https".equals(new URI(moodleClient.getString("address_moodle")).getScheme());
        } catch (URISyntaxException e) {
            log.error("Invalid moodle uri",e);
        }
        final HttpClientOptions options = new HttpClientOptions();
        options.setSsl(setSsl);
        options.setTrustAll(true);
        options.setVerifyHost(false);
        if (System.getProperty("httpclient.proxyHost") != null) {
            ProxyOptions proxyOptions = new ProxyOptions()
                    .setHost(System.getProperty("httpclient.proxyHost"))
                    .setPort(Integer.parseInt(System.getProperty("httpclient.proxyPort")))
                    .setUsername(System.getProperty("httpclient.proxyUsername"))
                    .setPassword(System.getProperty("httpclient.proxyPassword"));
            options.setProxyOptions(proxyOptions);
        }
        int maxPoolSize = moodleConfig.getInteger("http-client-max-pool-size", 0);
        if(maxPoolSize > 0) {
            options.setMaxPoolSize(maxPoolSize);
        }
        return vertx.createHttpClient(options);
    }

    public static void webServiceMoodlePost(JsonObject shareSend, String moodleUrl, Vertx vertx, JsonObject moodleClient,
                                             Handler<Either<String, Buffer>> handler) throws UnsupportedEncodingException {
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, moodleClient);

        URI url;
        try {
            url = new URI(moodleUrl);
        } catch (URISyntaxException e) {
            handler.handle(new Either.Left<>("Bad request"));
            return;
        }

        httpClient.request(createWebServiceMoodlePostRequestOptions(url, moodleClient))
                .flatMap(httpClientRequest -> {
                    httpClientRequest.setFollowRedirects(true);

                    if(shareSend == null)
                        return httpClientRequest.send();


                    Buffer chunk = Buffer.buffer()
                            .appendBuffer(Buffer.buffer("wstoken=" + shareSend.getString("wstoken") +
                            "&wsfunction=" + shareSend.getString("wsfunction") +
                            "&moodlewsrestformat=" + shareSend.getString("moodlewsrestformat")));

                    Object parameters = shareSend.getMap().get("parameters");
                    String encodedParameters = "";
                    if (parameters instanceof JsonObject) {
                        encodedParameters = ((JsonObject) parameters).encode();
                    } else if (parameters instanceof JsonArray) {
                        encodedParameters = ((JsonArray) parameters).encode();
                    }
                    if(!encodedParameters.isEmpty()){
                        chunk.appendBuffer(Buffer.buffer("&parameters=" + encodedParameters));
                    }

                    return httpClientRequest.send(chunk);
                })
                .onSuccess(response -> {
                    if (response.statusCode() == 200) {
                        final Buffer buff = Buffer.buffer();
                        response.handler(buff::appendBuffer);
                        response.endHandler(end -> {
                            handler.handle(new Either.Right<>(buff));
                            if (!responseIsSent.getAndSet(true)) {
                                httpClient.close();
                            }
                        });
                    } else {
                        log.error("Fail to post webservice" + response.statusMessage());
                        handler.handle(new Either.Left<>("Fail to post webservice" + response.statusMessage()));
                        response.bodyHandler(event -> {
                            log.error("Returning body after POST CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                            if (!responseIsSent.getAndSet(true)) {
                                httpClient.close();
                            }
                        });
                    }
                })
                .onFailure(throwable -> {
                    log.error(throwable.getMessage(), throwable);
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });



    }

    private static RequestOptions createWebServiceMoodlePostRequestOptions(URI url, JsonObject moodleClient){
        HeadersMultiMap headers = new HeadersMultiMap();
        headers.add("Host", moodleClient.getString("address_moodle")
                .replace("http://","").replace("https://",""));
        headers.add("Content-type", "application/x-www-form-urlencoded");


        return new RequestOptions()
                .setAbsoluteURI(url.toString())
                .setMethod(HttpMethod.POST)
                .setHeaders(headers);
    }
}
