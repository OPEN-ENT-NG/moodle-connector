package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleEventBusService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.entcore.common.service.impl.SqlCrudService;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class DefaultMoodleEventBusService extends SqlCrudService implements MoodleEventBusService {

    private EventBus eb;

    public DefaultMoodleEventBusService(String schema, String table, EventBus eb) {
        super(schema, table);
        this.eb = eb;
    }

    @Override
    public void getParams (JsonObject action, Handler<Either<String, JsonObject>> handler) {
        eb.send(Moodle.DIRECTORY_BUS_ADDRESS, action, handlerToAsyncHandler(new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> message){
                JsonObject body = message.body();
                JsonObject results = body.getJsonObject("result");
                String email = results.getString("email");
                String lastName = results.getString("lastName");
                String firstName = results.getString("firstName");
                JsonObject info = new JsonObject();
                info.put("email", email).put("lastname", lastName).put("firstname", firstName);
                handler.handle(new Either.Right<>(info));
            }
        }));
    }
}