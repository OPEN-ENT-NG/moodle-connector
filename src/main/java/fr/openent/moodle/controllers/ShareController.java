package fr.openent.moodle.controllers;

import fr.openent.moodle.helper.HttpClientHelper;
import fr.openent.moodle.security.CanShareResourceFilter;
import fr.openent.moodle.service.GetShareProcessingService;
import fr.openent.moodle.service.impl.DefaultGetShareProcessingService;
import fr.openent.moodle.service.impl.DefaultMoodleService;
import fr.openent.moodle.service.impl.DefaultPostShareProcessingService;
import fr.openent.moodle.service.MoodleService;
import fr.openent.moodle.service.PostShareProcessingService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static fr.openent.moodle.Moodle.*;
import static fr.openent.moodle.core.Field.*;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class ShareController extends ControllerHelper {

    private final GetShareProcessingService getShareProcessingService;
    private final PostShareProcessingService postShareProcessingService;
    private final MoodleService moodleService;

    private final TimelineHelper timelineHelper;


    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    public ShareController(EventBus eb, final Vertx vertx, TimelineHelper timelineHelper) {
        super();
        this.eb = eb;

        this.getShareProcessingService = new DefaultGetShareProcessingService();
        this.postShareProcessingService = new DefaultPostShareProcessingService(vertx);
        this.moodleService = new DefaultMoodleService(vertx);

        this.timelineHelper = timelineHelper;
    }

    @Get("/share/json/:id")
    @ApiDoc("Lists rights for a given course.")
    @ResourceFilter(CanShareResourceFilter.class)
    @SecuredAction(value = resource_read, type = ActionType.RESOURCE)
    public void share(final HttpServerRequest request) {
        final Handler<Either<String, JsonObject>> handler = defaultResponseHandler(request);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                Future<JsonObject> getShareJsonInfosFuture = getShareJsonInfos(request, user);
                Future<JsonArray> getUsersEnrolmentsFuture = getUsersEnrolmentsFromMoodle(request);

                Future.all(getShareJsonInfosFuture, getUsersEnrolmentsFuture).onComplete(event -> {
                    if (event.succeeded()) {
                        generateShareJson(request, handler, user, getShareJsonInfosFuture.result(), getUsersEnrolmentsFuture.result());
                    } else {
                        badRequest(request, event.cause().getMessage());
                    }
                });
            } else {
                log.error("User or group not found.");
                unauthorized(request);
            }
        });
    }

    /**
     * Get the shareJson model with a future
     *
     * @param request Http request
     * @param user    User infos
     */
    private Future<JsonObject> getShareJsonInfos(HttpServerRequest request, UserInfos user) {
        final Promise<JsonObject> promise = Promise.promise();
        shareService.shareInfos(user.getUserId(), request.getParam("id"), I18n.acceptLanguage(request),
                request.params().get("search"), event -> {
            if (event.isRight()) {
                promise.complete(event.right().getValue());
            } else {
                promise.fail("Share infos not found");
            }
        });
        return promise.future();
    }

    /**
     * Get the Moodle users with the Web-Service from Moodle
     *
     * @param request Http request
     */
    private Future<JsonArray> getUsersEnrolmentsFromMoodle(HttpServerRequest request) {
        final Promise<JsonArray> promise = Promise.promise();
        JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());
        final HttpClient httpClient = HttpClientHelper.createHttpClient(vertx, moodleClient);
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        Buffer wsResponse = new BufferImpl();
        final String moodleUrl = (moodleClient.getString("address_moodle") + moodleClient.getString("ws-path")) +
                "?wstoken=" + moodleClient.getString("wsToken") +
                "&wsfunction=" + WS_GET_SHARECOURSE +
                "&parameters[courseid]=" + request.getParam("id") +
                "&moodlewsrestformat=" + JSON;
        Handler<HttpClientResponse> getUsersEnrolmentsHandler = response -> {
            if (response.statusCode() == 200) {
                response.handler(wsResponse::appendBuffer);
                response.endHandler(end -> {
                    JsonArray finalGroups = new JsonArray(wsResponse);
                    promise.complete(finalGroups);
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            } else {
                log.error("Fail to call get share course right webservice" + response.statusMessage());
                response.bodyHandler(event -> {
                    log.error("Returning body after GET CALL : " + moodleUrl + ", Returning body : " + event.toString("UTF-8"));
                    promise.fail(response.statusMessage());
                    if (!responseIsSent.getAndSet(true)) {
                        httpClient.close();
                    }
                });
            }
        };
        httpClient.request(new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setAbsoluteURI(moodleUrl)
                .addHeader("Content-Length", "0"))
                .flatMap(HttpClientRequest::send)
                .onSuccess(getUsersEnrolmentsHandler)
                .onFailure(event -> {
                    //Typically an unresolved Address, a timeout about connection or response
                    log.error(event.getMessage(), event);
                    promise.fail(event.getMessage());
                    if (!responseIsSent.getAndSet(true)) {
                        renderError(request);
                        httpClient.close();
                    }
                });
        return promise.future();
    }

    /**
     * Creation and implementation of shareJson model
     *
     * @param request             Http request
     * @param handler             function handler returning data
     * @param user                UserInfos
     * @param shareJsonInfosFinal JsonObject to fill
     * @param usersEnrolments     JsonArray with Moodle users
     */
    private void generateShareJson(HttpServerRequest request, Handler<Either<String, JsonObject>> handler, UserInfos user,
                                   JsonObject shareJsonInfosFinal, JsonArray usersEnrolments) {
        JsonObject checkedInherited = new JsonObject();
        shareJsonInfosFinal.getJsonObject("users").put("checkedInherited", checkedInherited);
        JsonObject userEnrolmentsArray = usersEnrolments.getJsonObject(0).getJsonArray("enrolments").getJsonObject(0);
        JsonArray rightToAdd = new JsonArray().add(MOODLE_READ).add(MOODLE_CONTRIB).add(MOODLE_MANAGER);

        if (!usersEnrolments.isEmpty() && !shareJsonInfosFinal.isEmpty()) {
            getShareProcessingService.shareTreatmentForGroups(userEnrolmentsArray, shareJsonInfosFinal, request, rightToAdd,
                    groupsTreatmentEvent -> {
                if (groupsTreatmentEvent.isRight()) {
                    log.info("Groups treatment OK");
                } else {
                    log.error("Groups treatment KO");
                }
            });

            getShareProcessingService.shareTreatmentForUsers(userEnrolmentsArray, shareJsonInfosFinal, user, rightToAdd,
                    usersTreatmentEvent -> {
                if (usersTreatmentEvent.isRight()) {
                    log.info("Users treatment OK");
                } else {
                    log.error("Users treatment KO");
                }
            });

            handler.handle(new Either.Right<>(shareJsonInfosFinal));
        } else {
            log.error("User future or share infos future is empty");
            unauthorized(request);
        }
    }

    @Put("/contrib")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(CanShareResourceFilter.class)
    @SecuredAction(value = resource_contrib, type = ActionType.RESOURCE)
    public void contrib(final HttpServerRequest request) {

    }

    @Put("/share/resource/:id")
    @ApiDoc("Adds rights for a given course.")
    @ResourceFilter(CanShareResourceFilter.class)
    @SecuredAction(value = resource_manager, type = ActionType.RESOURCE)
    public void shareSubmit(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "share", shareCourseObject -> {
            JsonObject shareObjectToFill = new JsonObject();
            shareObjectToFill.put("courseid", request.params().entries().get(0).getValue());
            UserUtils.getUserInfos(eb, request, user -> {
                if (user != null) {
                    for (Object idGroup : shareCourseObject.copy().getJsonObject("groups").getMap().keySet().toArray()) {
                        if (idGroup.toString().startsWith("GR_")) {
                            shareCourseObject.getJsonObject("groups")
                                    .put(idGroup.toString().substring(3), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
                            shareCourseObject.getJsonObject("groups").remove(idGroup.toString());
                        }
                        if (idGroup.toString().startsWith("SB_")) {
                            shareCourseObject.getJsonObject("bookmarks")
                                    .put(idGroup.toString().substring(2), shareCourseObject.getJsonObject("groups").getValue(idGroup.toString()));
                            shareCourseObject.getJsonObject("groups").remove(idGroup.toString());
                        }
                    }

                    Map<String, Object> idUsers = shareCourseObject.getJsonObject("users").getMap();
                    Map<String, Object> idGroups = shareCourseObject.getJsonObject("groups").getMap();
                    Map<String, Object> idBookmarks = shareCourseObject.getJsonObject("bookmarks").getMap();

                    JsonObject IdFront = new JsonObject();
                    JsonObject keyShare = new JsonObject();

                    JsonArray usersIds = new JsonArray(new ArrayList<>(idUsers.keySet()));
                    postShareProcessingService.getResultUsers(shareCourseObject, usersIds, idUsers, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Users treatment for Post OK");
                        } else {
                            log.info("Users treatment for Post KO");
                        }
                    });

                    JsonArray groupsIds = new JsonArray(new ArrayList<>(idGroups.keySet()));
                    postShareProcessingService.getResultGroups(shareCourseObject, groupsIds, idGroups, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Groups treatment for Post OK");
                        } else {
                            log.info("Groups treatment for Post KO");
                        }
                    });

                    JsonArray bookmarksIds = new JsonArray(new ArrayList<>(idBookmarks.keySet()));
                    postShareProcessingService.getResultBookmarks(shareCourseObject, bookmarksIds, idBookmarks, IdFront, keyShare, event -> {
                        if (event.isRight()) {
                            log.info("Bookmarks treatment for Post OK");
                        } else {
                            log.info("Bookmarks treatment for Post KO");
                        }
                    });

                    usersIds.add(user.getUserId());

                    Future<JsonArray> getUsersFuture = postShareProcessingService.getUsersFuture(usersIds);
                    Future<JsonArray> getUsersInGroupsFuture = postShareProcessingService.getUsersInGroupsFuture(groupsIds);
                    Future<JsonArray> getBookmarksFuture = postShareProcessingService.getUsersInBookmarksFuture(bookmarksIds);

                    Future<JsonArray> getTheAuditeurIdFuture = getUsersEnrolmentsFromMoodle(request);

                    final Map<String, Object> mapInfo = keyShare.getMap();
                    mapInfo.put(user.getUserId(), moodleConfig.getInteger("idEditingTeacher"));

                    Future.all(getUsersFuture, getUsersInGroupsFuture, getBookmarksFuture, getTheAuditeurIdFuture)
                    .onComplete(event -> {
                        if (event.succeeded()) {
                            JsonArray usersFutureResult = getUsersFuture.result();
                            JsonArray groupsFutureResult = getUsersInGroupsFuture.result();
                            JsonArray bookmarksFutureResult = getBookmarksFuture.result();
                            JsonArray getTheAuditeurIdFutureResult = getTheAuditeurIdFuture.result().getJsonObject(0)
                                    .getJsonArray("enrolments").getJsonObject(0).getJsonArray("users");

                            JsonObject auditeur = new JsonObject();
                            for (int i = 0; i < getTheAuditeurIdFutureResult.size(); i++) {
                                if (getTheAuditeurIdFutureResult.getJsonObject(i).getInteger("role") == moodleConfig.getValue("idAuditeur")) {
                                    auditeur = getTheAuditeurIdFutureResult.getJsonObject(i);
                                    break;
                                }
                            }
                            if (auditeur.size() == 0) {
                                badRequest(request, "No auditor role found for course : " + shareObjectToFill.getValue("courseid"));
                                return;
                            }

                            if (usersFutureResult != null && !usersFutureResult.isEmpty()) {
                                shareObjectToFill.put("users", usersFutureResult);
                                for (Object userObject : usersFutureResult) {
                                    JsonObject userJson = ((JsonObject) userObject);
                                    if (userJson.getString("id").equals(user.getUserId()) &&
                                            userJson.getString("id").equals(auditeur.getString("id"))) {
                                        userJson.put("role", moodleConfig.getInteger("idAuditeur"));
                                    } else {
                                        userJson.put("role", mapInfo.get(userJson.getString("id")));
                                    }
                                }
                            }
                            if (groupsFutureResult != null && !groupsFutureResult.isEmpty()) {
                                shareObjectToFill.put("groups", groupsFutureResult);
                                for (Object groupObject : groupsFutureResult) {
                                    JsonObject groupJson = ((JsonObject) groupObject);
                                    groupJson.put("role", mapInfo.get(groupJson.getString("id").substring(3)));
                                    UserUtils.groupDisplayName(groupJson, I18n.acceptLanguage(request));
                                }
                            }

                            // Deal with uncharing
                            List<String> enrolledUsersId = shareObjectToFill.getJsonArray(USERS).stream()
                                    .map(JsonObject.class::cast)
                                    .map(userJson -> userJson.getString(ID))
                                    .collect(Collectors.toList());
                            List<JsonObject> usersToUnroll = getTheAuditeurIdFutureResult.stream()
                                    .filter(Objects::nonNull)
                                    .filter(JsonObject.class::isInstance)
                                    .map(JsonObject.class::cast)
                                    .filter(enrolledUser -> enrolledUser.getInteger(ROLE).equals(ROLE_APPRENANT)
                                            && enrolledUsersId.contains(enrolledUser.getString(ID)))
                                    .map(enrolledUser -> new JsonObject().put(ID, enrolledUser.getString(ID)))
                                    .collect(Collectors.toList());
                            if (!usersToUnroll.isEmpty()) shareObjectToFill.put(UNENROL_USERSUSERS, usersToUnroll);

                            final List<Future> listUsersFutures = new ArrayList<>();
                            final List<Integer> listRankGroup = new ArrayList<>();
                            int i = 0;
                            if (bookmarksFutureResult != null && !bookmarksFutureResult.isEmpty()) {
                                postShareProcessingService.getUsersInBookmarksFutureLoop(shareObjectToFill, mapInfo,
                                        bookmarksFutureResult, listUsersFutures, listRankGroup, i);
                            }
                            if (!listUsersFutures.isEmpty()) {
                                Future.all((List)listUsersFutures).onComplete(finished -> {
                                    if (finished.succeeded()) {
                                        postShareProcessingService.processUsersInBookmarksFutureResult(shareObjectToFill,
                                                listUsersFutures, listRankGroup);
                                        sendRightShare(shareObjectToFill, request);
                                    } else {
                                        badRequest(request, event.cause().getMessage());
                                    }
                                });
                            } else {
                                sendRightShare(shareObjectToFill, request);
                            }
                        } else {
                            badRequest(request, event.cause().getMessage());
                        }
                    });
                } else {
                    log.error("User not found.");
                    unauthorized(request);
                }
            });
        });
    }

    private void sendRightShare(JsonObject shareObjectToFill, HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            JsonArray zimbraEmail = new JsonArray();
            if (shareObjectToFill.getJsonArray("users") != null) {
                for (int i = 0; i < shareObjectToFill.getJsonArray("users").size(); i++) {
                    zimbraEmail.add(shareObjectToFill.getJsonArray("users").getJsonObject(i).getString("id"));
                }
            }
            if (shareObjectToFill.getJsonArray("groups") != null) {
                for (int i = 0; i < shareObjectToFill.getJsonArray("groups").size(); i++) {
                    for (int j = 0; j < shareObjectToFill.getJsonArray("groups").getJsonObject(i).getJsonArray("users").size(); j++) {
                        zimbraEmail.add(shareObjectToFill.getJsonArray("groups").getJsonObject(i).getJsonArray("users")
                                .getJsonObject(j).getString("id"));
                    }
                }
            }

            JsonArray users = shareObjectToFill.getJsonArray("users");
            if (users != null) {
                for (int i = 0; i < users.size(); i++) {
                    JsonObject manualUser = users.getJsonObject(i);
                    manualUser.put("email", manualUser.getString("id") + "@moodle.net");
                }
            }

            JsonArray groupsUsers = shareObjectToFill.getJsonArray("groups");
            if (groupsUsers != null) {
                for (int i = 0; i < groupsUsers.size(); i++) {
                    JsonArray usersInGroup = groupsUsers.getJsonObject(i).getJsonArray("users");
                    if (usersInGroup != null) {
                        for (int j = 0; j < usersInGroup.size(); j++) {
                            JsonObject userInGroup = usersInGroup.getJsonObject(j);
                            userInGroup.put("email", userInGroup.getString("id") + "@moodle.net");
                        }
                    }
                }
            }

            JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());


            JsonObject shareSend = new JsonObject();
            shareSend.put("parameters", shareObjectToFill)
                    .put("wstoken", moodleClient.getString("wsToken"))
                    .put("wsfunction", WS_CREATE_SHARECOURSE)
                    .put("moodlewsrestformat", JSON);
            URI moodleUri = null;
            try {
                final String service = (moodleClient.getString("address_moodle") + moodleClient.getString("ws-path"));
                final String urlSeparator = "";
                moodleUri = new URI(service + urlSeparator);
            } catch (URISyntaxException e) {
                log.error("Invalid moodle web service sending right share uri", e);
            }
            if (moodleUri != null) {
                final String moodleUrl = moodleUri.toString();
                log.info("CALL WS_CREATE_SHARECOURSE");
                try {
                    HttpClientHelper.webServiceMoodlePost(shareSend, moodleUrl, vertx, moodleClient, event -> {
                        if (event.isRight()) {
                            log.info("SUCCESS WS_CREATE_SHARECOURSE");
                            enrolNotify(event.right().getValue().toJsonArray().getJsonObject(0)
                                    .getJsonArray("response").getJsonObject(0), user, moodleClient);
                            request.response()
                                    .setStatusCode(200)
                                    .end();
                        } else {
                            log.error("FAIL WS_CREATE_SHARECOURSE" + event.left().getValue());
                            unauthorized(request);
                        }
                    });
                } catch (UnsupportedEncodingException e) {
                    log.error("UnsupportedEncodingException",e);
                    renderError(request);
                }
            }
        });
    }

    private void enrolNotify(JsonObject response, UserInfos user, JsonObject moodleClient) {
        int courseId = response.getInteger("courseid");
        JsonArray usersEnrolled = response.getJsonArray("users");
        JsonArray groupsEnrolled = response.getJsonArray("groups");

        String timelineSender = user.getUsername() != null ? user.getUsername() : null;
        List<String> recipients = new ArrayList<>();
        final JsonObject params = new JsonObject()
                .put("courseUri", moodleClient.getString("address_moodle") + "/course/view.php?id=" + courseId)
                .put("disableAntiFlood", true);
        params.put("username", timelineSender).put("uri", "/userbook/annuaire#" + user.getUserId());

        for (Object userEnrolled : usersEnrolled) {
            JsonObject userJson = (JsonObject) userEnrolled;
            if (!userJson.getString("result").contains("already") && !userJson.getValue("idnumber").toString().equals("0")) {
                recipients.add(userJson.getString("idnumber"));
            }
        }
        for (Object group : groupsEnrolled) {
            JsonObject groupJson = (JsonObject) group;
            if (!groupJson.getString("result").contains("already") && !groupJson.getValue("idnumber").toString().equals("0")) {
                usersEnrolled = groupJson.getJsonArray("users");
                for (Object userEnrolled : usersEnrolled) {
                    JsonObject userJson = (JsonObject) userEnrolled;
                    if (!userJson.getString("result").contains("already") && !userJson.getValue("idnumber").toString().equals("0")) {
                        recipients.add(userJson.getString("idnumber"));
                    }
                }
            }
        }
        params.put("pushNotif", new JsonObject().put("title", "push.notif.moodle").put("body", ""));

        if(!recipients.isEmpty()) {
            timelineHelper.notifyTimeline(null, "moodle.enrol_notification", user, recipients, params);
        }
    }

    @Get("/course/share/BP/:id")
    @ApiDoc("Enroll the user on the course as a guest")
    @SecuredAction(workflow_accessPublicCourse)
    public void accessPublicCourse(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {

            int courseId;
            try {
                courseId = Integer.parseInt(request.getParam("id"));
            } catch (NumberFormatException n){
                Renders.badRequest(request);
                return;
            }

            JsonObject moodleClient = moodleMultiClient.getJsonObject(request.host());


            moodleService.getAuditeur(courseId, vertx, moodleClient, getAuditeurEvent -> {
                if (getAuditeurEvent.isRight()) {
                    JsonArray usersId = new JsonArray();

                    usersId.add(getAuditeurEvent.right().getValue().getJsonObject(0).getString("id"))
                            .add(user.getUserId());
                    if (!getAuditeurEvent.right().getValue().getJsonObject(0).getString("id").equals(user.getUserId())) {
                        moodleService.registerUserInPublicCourse(usersId, courseId, vertx, moodleClient, registerEvent -> {
                            if (registerEvent.isRight()) {
                                redirect(request, moodleClient.getString("address_moodle"), "/course/view.php?id=" +
                                        request.getParam("id") + "&notifyeditingon=1");
                            } else {
                                log.error("FAIL WS_CREATE_SHARECOURSE" + registerEvent.left().getValue());
                                unauthorized(request);
                            }
                        });
                    } else {
                        redirect(request, moodleClient.getString("address_moodle"), "/course/view.php?id=" +
                                request.getParam("id") + "&notifyeditingon=1");
                    }
                } else {
                    Renders.badRequest(request);
                }
            });
        });
    }
}
