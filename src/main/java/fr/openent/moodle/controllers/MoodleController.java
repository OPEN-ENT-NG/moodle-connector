package fr.openent.moodle.controllers;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.core.Config;
import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.security.AccessRight;
import fr.openent.moodle.service.impl.DefaultModuleSQLRequestService;
import fr.openent.moodle.service.impl.DefaultMoodleEventBus;
import fr.openent.moodle.service.ModuleSQLRequestService;
import fr.openent.moodle.service.MoodleEventBus;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.response.DefaultResponseHandler;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

import static fr.openent.moodle.Moodle.*;
import static fr.wseduc.webutils.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class MoodleController extends ControllerHelper {

    private final ModuleSQLRequestService moduleSQLRequestService;
    private final MoodleEventBus moodleEventBus;
    private final Storage storage;

    private final EventStore eventStore;

    private final WebClient client;

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public MoodleController(EventStore eventStore, final Storage storage, EventBus eb, Vertx vertx) {
        super();
        this.eventStore = eventStore;
        this.eb = eb;
        this.storage = storage;

        this.moduleSQLRequestService = new DefaultModuleSQLRequestService(Moodle.moodleSchema, "course");
        this.moodleEventBus = new DefaultMoodleEventBus(eb);

        this.client = WebClient.create(vertx);

    }

    /**
     * Displays the home view.
     *
     * @param request Client request
     */
    @Get("")
    @SecuredAction(workflow_view)
    public void view(HttpServerRequest request) {
        JsonObject params = new JsonObject().put("selfEnrollmentCategoryId", config.getInteger("selfEnrollmentCategoryId", null));
        renderView(request, params);
        eventStore.createAndStoreEvent(MoodleEvent.ACCESS.name(), request);
    }

    @Get("/config")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getConfig(final HttpServerRequest request) {
        JsonObject safeConfig = config.copy();
        safeConfig.put("wsToken", "**********");
        JsonObject configMultiClient = safeConfig.getJsonObject("multiClient", new JsonObject());
        for (Iterator<Map.Entry<String, Object>> it = configMultiClient.stream().iterator(); it.hasNext(); ) {
            JsonObject moodleClient = (JsonObject) it.next().getValue();
            moodleClient.put("wsToken", "**********");
        }
        renderJson(request, safeConfig);
    }

    @Get("/config/share")
    @ApiDoc("Get the sharing configuration (for example: default actions to check in share panel.")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getConfigShare(final HttpServerRequest request) {
        JsonObject shareConfig = moodleConfig.getJsonObject(Config.SHARE);
        if (shareConfig != null) {
            renderJson(request, shareConfig, 200);
        } else {
            notFound(request, "No platform sharing configuration found");
        }
    }

    @ApiDoc("public Get picture for moodle website")
    @Get("/files/:id")
    public void getFile(HttpServerRequest request) {
        String idImage = request.getParam("id")
                .substring(0, request.getParam("id").lastIndexOf('.'));
        moodleEventBus.getImage(idImage, event -> {
            if (event.isRight()) {
                JsonObject document = event.right().getValue();
                JsonObject metadata = document.getJsonObject("metadata");
                String contentType = metadata.getString("content-type");
                storage.readFile(document.getString("file"), buffer ->
                        request.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", contentType)
                                .end(buffer));
            } else {
                badRequest(request);
            }
        });
    }

    @ApiDoc("get info image workspace")
    @Get("/info/image/:id")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getInfoImg(final HttpServerRequest request) {
        try {
            moodleEventBus.getImage(request.getParam("id"),
                    DefaultResponseHandler.defaultResponseHandler(request));
        } catch (Exception e) {
            log.error("Gail to get image workspace", e);
        }
    }

    @Get("/choices")
    @ApiDoc("get a choice")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void getChoices(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                String userId = user.getUserId();
                moduleSQLRequestService.getChoices(userId, arrayResponseHandler(request));
            } else {
                log.error("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Put("/choices/:view")
    @ApiDoc("set a choice")
    @ResourceFilter(AccessRight.class)
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    public void setChoice(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "courses", courses ->
                UserUtils.getUserInfos(eb, request, user -> {
                    if (user != null) {
                        courses.put("userId", user.getUserId());
                        String view = request.getParam("view");
                        moduleSQLRequestService.setChoice(courses, view, defaultResponseHandler(request));
                    } else {
                        log.error("User not found in session.");
                        unauthorized(request);
                    }
                }));
    }

    @Post("/convert")
    @ApiDoc("get infos to connect to the python transfo server")
    @SecuredAction(workflow_convert)
    public void convert(final HttpServerRequest request) {
        request.setExpectMultipart(true);
        final Buffer buff = Buffer.buffer();
        request.uploadHandler(upload -> {
            upload.handler(buff::appendBuffer);
            upload.endHandler(end -> {
                log.info("File received  : " + upload.filename());
                JsonObject serverConfig = Moodle.moodleConfig.getJsonObject("transfoLMSServer");
                String serverTransfoUrl = serverConfig.getString("ip_adress") + "/convert/";
                MultipartForm form = MultipartForm.create()
                        .binaryFileUpload("file",upload.filename(),buff,"application/octet-stream");
                client.postAbs(serverTransfoUrl)
                        .sendMultipartForm(form, resp -> {
                            if (resp.succeeded()) {
                                log.info("File sent and transform with success ");
                                JsonObject result = new JsonObject().put("message",resp.result().getHeader("message"))
                                                .put("xml",resp.result().bodyAsString());
                                renderJson(request, result);
                            } else {
                                log.error("Failed to contact server transfoLMS : " + resp.result().bodyAsString());
                                renderError(request);
                            }
                        });
            });
        });
    }
}
