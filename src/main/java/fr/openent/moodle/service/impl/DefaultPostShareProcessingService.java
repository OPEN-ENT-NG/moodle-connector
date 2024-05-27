package fr.openent.moodle.service.impl;

import fr.openent.moodle.service.ModuleNeoRequestService;
import fr.openent.moodle.service.PostShareProcessingService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;

import java.util.List;
import java.util.Map;

import static fr.openent.moodle.Moodle.moodleConfig;
import static java.util.Objects.isNull;

public class DefaultPostShareProcessingService extends ControllerHelper implements PostShareProcessingService {

    private final ModuleNeoRequestService moduleNeoRequestService;

    public DefaultPostShareProcessingService() {
        super();
        this.moduleNeoRequestService = new DefaultModuleNeoRequestService();
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

    public void getUsersPromise(JsonArray usersIds, Promise<JsonArray> getUsersPromise){
        moduleNeoRequestService.getUsers(usersIds, eventUsers -> {
            if (eventUsers.isRight()) {
                getUsersPromise.complete(eventUsers.right().getValue());
            } else {
                getUsersPromise.fail("Users not found");
            }
        });
    }

    public void getUsersInGroupsPromise(JsonArray groupsIds, Promise<JsonArray> getUsersInGroupsPromise) {
        moduleNeoRequestService.getGroups(groupsIds, eventGroups -> {
            if (eventGroups.isRight()) {
                getUsersInGroupsPromise.complete(eventGroups.right().getValue());
            } else {
                getUsersInGroupsPromise.fail("Groups not found");
            }
        });
    }

    public void getUsersInBookmarksPromise(JsonArray bookmarksIds, Promise<JsonArray> getBookmarksPromise) {
        moduleNeoRequestService.getSharedBookMark(bookmarksIds, eventBookmarks -> {
            if (eventBookmarks.isRight()) {
                getBookmarksPromise.complete(eventBookmarks.right().getValue());
            } else {
                getBookmarksPromise.fail("Bookmarks not found");
            }
        });
    }

    public void getUsersInBookmarksFutureLoop(JsonObject shareObjectToFill, Map<String, Object> mapInfo,
                                              JsonArray bookmarksFutureResult, List<Future<JsonArray>> listUsersFutures,
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
                Promise<JsonArray> getUsersGroupsPromise = Promise.promise();
                Handler<Either<String, JsonArray>> getUsersGroupsHandler = finalUsersGroups -> {
                    if (finalUsersGroups.isRight()) {
                        getUsersGroupsPromise.complete(finalUsersGroups.right().getValue());
                    } else {
                        getUsersGroupsPromise.fail("Bookmarks problem");
                    }
                };
                listUsersFutures.add(getUsersGroupsPromise.future());
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

    public void processUsersInBookmarksFutureResult(JsonObject shareObjectToFill, List<Future<JsonArray>> listUsersFutures,
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
