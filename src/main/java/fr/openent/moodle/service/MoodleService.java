package fr.openent.moodle.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.io.UnsupportedEncodingException;

public interface MoodleService {

    void registerUserInPublicCourse(JsonArray usersId, Integer courseId, Vertx vertx, JsonObject moodleClient,
                                    Handler<Either<String, JsonArray>> handler);

    void getAuditeur(Integer courseId, Vertx vertx, JsonObject moodleClient, Handler<Either<String, JsonArray>> handler);
    void createOrUpdateUser(UserInfos user, JsonObject moodleClient, Vertx vertx, Handler<Either<String, Buffer>> handlerUpdateUser) throws UnsupportedEncodingException;
}
