package fr.openent.moodle.service.impl;

import fr.openent.moodle.service.ModuleNeoRequestService;
import fr.openent.moodle.service.PostShareProcessingService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.migration.AppMigrationConfiguration;

import java.util.List;
import java.util.Map;

import static fr.openent.moodle.Moodle.moodleConfig;
import static java.util.Objects.isNull;

public class DefaultPostShareProcessingService extends ControllerHelper implements PostShareProcessingService {

    private final ModuleNeoRequestService moduleNeoRequestService;

    public DefaultPostShareProcessingService(final Vertx vertx) {
        super();
        this.moduleNeoRequestService = new BrokerSwitchNeoRequestService(
          new DefaultModuleNeoRequestService(),
          AppMigrationConfiguration.fromVertx("referential", vertx, null),
          vertx.eventBus());
    }

    public void getResultUsers(JsonObject shareCourseObject, JsonArray usersIds, Map<String, Object> idUsers,
                               JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler) {
        if (!shareCourseObject.getJsonObject("users").isEmpty() && shareCourseObject.getJsonObject("users").size() > 1) {
            getIdFromMap(idUsers, idFront, keyShare);
        } else if (!shareCourseObject.getJsonObject("users").isEmpty() && shareCourseObject.getJsonObject("users").size() == 1) {
            if (shareCourseObject.getJsonObject("users").getJsonArray(usersIds.getValue(0).toString()).size() == 2) {
                keyShare.put(usersIds.getString(0), moodleConfig.getInteger("idEditingTeacher"));
            } else if (shareCourseObject.getJsonObject("users").getJsonArray(usersIds.getValue(0).toString()).size() == 1) {
                keyShare.put(usersIds.getString(0), moodleConfig.getInteger("idStudent"));
            }
        }
        handler.handle(new Either.Right<>(usersIds));
    }

    public void getResultGroups(JsonObject shareCourseObject, JsonArray groupsIds, Map<String, Object> idGroups,
                                JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler) {
        if (!shareCourseObject.getJsonObject("groups").isEmpty() && shareCourseObject.getJsonObject("groups").size() > 1) {
            getIdFromMap(idGroups, idFront, keyShare);
        } else if (!shareCourseObject.getJsonObject("groups").isEmpty() && shareCourseObject.getJsonObject("groups").size() == 1) {
            if (shareCourseObject.getJsonObject("groups").getJsonArray(groupsIds.getValue(0).toString()).size() == 2) {
                keyShare.put(groupsIds.getString(0), moodleConfig.getInteger("idEditingTeacher"));
            } else if (shareCourseObject.getJsonObject("groups").getJsonArray(groupsIds.getValue(0).toString()).size() == 1) {
                keyShare.put(groupsIds.getString(0), moodleConfig.getInteger("idStudent"));
            }
        }
        handler.handle(new Either.Right<>(groupsIds));
    }

    public void getResultBookmarks(JsonObject shareCourseObject, JsonArray bookmarksIds, Map<String, Object> idBookmarks,
                                   JsonObject idFront, JsonObject keyShare, Handler<Either<String, JsonArray>> handler) {
        if (!shareCourseObject.getJsonObject("bookmarks").isEmpty() && shareCourseObject.getJsonObject("bookmarks").size() > 1) {
            getIdFromMap(idBookmarks, idFront, keyShare);
        } else if (!shareCourseObject.getJsonObject("bookmarks").isEmpty() && shareCourseObject.getJsonObject("bookmarks").size() == 1) {
            if (shareCourseObject.getJsonObject("bookmarks").getJsonArray(bookmarksIds.getValue(0).toString()).size() == 2) {
                keyShare.put(bookmarksIds.getString(0), moodleConfig.getInteger("idEditingTeacher"));
            } else if (shareCourseObject.getJsonObject("bookmarks").getJsonArray(bookmarksIds.getValue(0).toString()).size() == 1) {
                keyShare.put(bookmarksIds.getString(0), moodleConfig.getInteger("idStudent"));
            }
        }
        handler.handle(new Either.Right<>(bookmarksIds));
    }

    private void getIdFromMap(Map<String, Object> idBookmarks, JsonObject idFront, JsonObject keyShare) {
        for (Map.Entry<String, Object> mapShareGroups : idBookmarks.entrySet()) {
            idFront.put(mapShareGroups.getKey(), mapShareGroups.getValue());
            if (idFront.getJsonArray(mapShareGroups.getKey()).size() == 2) {
                keyShare.put(mapShareGroups.getKey(), moodleConfig.getInteger("idEditingTeacher"));
            } else if (idFront.getJsonArray(mapShareGroups.getKey()).size() == 1) {
                keyShare.put(mapShareGroups.getKey(), moodleConfig.getInteger("idStudent"));
            }
        }
    }

    public Future<JsonArray> getUsersFuture(JsonArray usersIds) {
        final Promise<JsonArray> promise = Promise.promise();
        moduleNeoRequestService.getUsers(usersIds, eventUsers -> {
            if (eventUsers.isRight()) {
                promise.complete(eventUsers.right().getValue());
            } else {
                promise.fail("Users not found");
            }
        });
        return promise.future();
    }

    public Future<JsonArray> getUsersInGroupsFuture(JsonArray groupsIds) {
        final Promise<JsonArray> promise = Promise.promise();
        moduleNeoRequestService.getGroups(groupsIds, eventGroups -> {
            if (eventGroups.isRight()) {
                promise.complete(eventGroups.right().getValue());
            } else {
                promise.fail("Groups not found");
            }
        });
        return promise.future();
    }

    public Future<JsonArray> getUsersInBookmarksFuture(JsonArray bookmarksIds) {
        final Promise<JsonArray> promise = Promise.promise();
        moduleNeoRequestService.getSharedBookMark(bookmarksIds, eventBookmarks -> {
            if (eventBookmarks.isRight()) {
                promise.complete(eventBookmarks.right().getValue());
            } else {
                promise.fail("Bookmarks not found");
            }
        });
        return promise.future();
    }

    public void getUsersInBookmarksFutureLoop(JsonObject shareObjectToFill, Map<String, Object> mapInfo,
                                              JsonArray bookmarksFutureResult, List<Future> listUsersFutures,
                                              List<Integer> listRankGroup, int i) {
        for (Object bookmark : bookmarksFutureResult.getJsonObject(0).getJsonArray("bookmarks")) {
            JsonArray shareJson = ((JsonObject) bookmark).getJsonObject("group").getJsonArray("users");
            JsonArray groupsId = new JsonArray();
            for (Object userOrGroup : shareJson) {
                JsonObject userJson = ((JsonObject) userOrGroup);
                if (isNull(userJson.getValue("email")))
                    groupsId.add(userJson.getValue("id"));
            }
            if (groupsId.size() > 0) {
                final Promise<JsonArray> getUsersGroupsFuture = Promise.promise();
                Handler<Either<String, JsonArray>> getUsersGroupsHandler = finalUsersGroups -> {
                    if (finalUsersGroups.isRight()) {
                        getUsersGroupsFuture.complete(finalUsersGroups.right().getValue());
                    } else {
                        getUsersGroupsFuture.fail("Bookmarks problem");
                    }
                };
                listUsersFutures.add(getUsersGroupsFuture.future());
                listRankGroup.add(i);
                moduleNeoRequestService.getGroups(groupsId, getUsersGroupsHandler);
            }
            ((JsonObject) bookmark).getJsonObject("group").put("role",
                    mapInfo.get(((JsonObject) bookmark).getJsonObject("group").getString("id").substring(2)));
            if (shareObjectToFill.containsKey("groups"))
                shareObjectToFill.getJsonArray("groups").add(((JsonObject) bookmark).getJsonObject("group"));
            else {
                JsonArray jsonArrayToAdd = new JsonArray();
                jsonArrayToAdd.add(((JsonObject) bookmark).getJsonObject("group"));
                shareObjectToFill.put("groups", jsonArrayToAdd);
            }
            i++;
        }
    }

    public void processUsersInBookmarksFutureResult(JsonObject shareObjectToFill, List<Future> listUsersFutures,
                                                    List<Integer> listRankGroup) {
        JsonArray usersBookmarkGroups;
        for (int j = 0; j < listUsersFutures.size(); j++) {
            usersBookmarkGroups = (JsonArray) listUsersFutures.get(j).result();
            for (Object group : usersBookmarkGroups) {
                shareObjectToFill.getJsonArray("groups").getJsonObject(listRankGroup.get(j)).getJsonArray("users")
                        .addAll(((JsonObject) group).getJsonArray("users"));
                JsonArray duplicatesUsers = shareObjectToFill.getJsonArray("groups").getJsonObject(listRankGroup.get(j))
                        .getJsonArray("users").copy();
                shareObjectToFill.getJsonArray("groups").getJsonObject(listRankGroup.get(j)).remove("users");
                JsonArray usersArray = new JsonArray();
                for (Object userToShare : duplicatesUsers) {
                    boolean findDuplicate = false;
                    for (int k = 0; k < usersArray.size(); k++) {
                        if (((JsonObject) userToShare).getValue("id").toString()
                                .equals(usersArray.getJsonObject(k).getValue("id").toString()))
                            findDuplicate = true;
                    }
                    if (!findDuplicate) {
                        if (!isNull(((JsonObject) userToShare).getValue("firstname")))
                            usersArray.add(userToShare);
                    }
                }
                shareObjectToFill.getJsonArray("groups").getJsonObject(listRankGroup.get(j)).put("users", usersArray);
            }
        }
    }
}
