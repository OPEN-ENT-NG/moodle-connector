package fr.openent.moodle.service.impl;

import fr.openent.moodle.Moodle;
import fr.openent.moodle.service.MoodleWebService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;


public class DefaultMoodleWebService extends SqlCrudService implements MoodleWebService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMoodleWebService.class);

    public DefaultMoodleWebService(String schema, String table) {
        super(schema, table);
    }

    @Override
    public void createFolder(final JsonObject folder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        values.add(folder.getValue("userId"));
        values.add(folder.getValue("structureId"));
        values.add(folder.getValue("name"));
        values.add(folder.getValue("parentid"));

        String createFolder = "INSERT INTO " + Moodle.moodleSchema + ".folder(user_id, structure_id, name, parent_id)" +
                    " VALUES (?, ?, ?, ?)";
            sql.prepared(createFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void moveFolder(final JsonObject folder, final long id_targetFolder, final Handler<Either<String, JsonObject>> handler){
        JsonArray values = new JsonArray();
        JsonArray folders = folder.getJsonArray("selectedFolders");
        values.add(id_targetFolder);

        for (int i = 0;i<folders.size();i++){
            values.add(folders.getValue(i));
        }

        String moveFolder = "UPDATE " + Moodle.moodleSchema + ".folder SET parent_id = ? WHERE id IN " + Sql.listPrepared(folders.getList());

        sql.prepared(moveFolder,values , SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void createCourse(final JsonObject course, final Handler<Either<String, JsonObject>> handler){
        String createCourse = "INSERT INTO " + Moodle.moodleSchema + ".course(moodle_id, folder_id, user_id)" +
                " VALUES (?, ?, ?)";

        JsonArray values = new JsonArray();
        values.add(course.getValue("moodleid"));
        values.add(1);
        values.add(course.getString("userid"));

        sql.prepared(createCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(final JsonObject course, final Handler<Either<String, JsonObject>> handler) {
        String deleteCourse = "DELETE FROM " + Moodle.moodleSchema + ".course WHERE moodle_id = ?;";

        JsonArray values = new JsonArray();
        values.add(course.getValue("courseids"));

        sql.prepared(deleteCourse, values, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getCoursInEnt(final long id_folder, String id_user, final Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT moodle_id, folder_id  " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE folder_id = ? and  user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(id_folder)
                .add(id_user);
        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public boolean getValueMoodleIdinEnt(Integer courid, JsonArray object) {
        for(int i=0;i<object.size();i++){
            JsonObject  o=object.getJsonObject(i);
            if(o.getInteger("moodle_id").equals(courid)){
                return true;
            }
        }
        return false;
    }

    @Override
    public void getFoldersInEnt(String id_user, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * " +
                "FROM " + Moodle.moodleSchema + ".folder " +
                "WHERE user_id = ?;";
        JsonArray values = new JsonArray();
        values.add(id_user);
        sql.prepared(query, values, SqlResult.validResultHandler(handler));
    }

    @Override
    public void countItemInfolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler) {
        String query = "SELECT  count(*) " +
                "FROM " + Moodle.moodleSchema + ".folder " +
                "WHERE user_id = ? AND parent_id = ?;";
        JsonArray values = new JsonArray();
        values.add(userId).add(id_folder);
        sql.prepared(query, values, SqlResult.validUniqueResultHandler(defaultResponseHandler));
    }

    @Override
    public void countCoursesItemInfolder(long id_folder, String userId, Handler<Either<String, JsonObject>> defaultResponseHandler) {
        String query = "SELECT  count(*) " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE user_id = ? AND folder_id = ?;";
        JsonArray values = new JsonArray();
        values.add(userId).add(id_folder);
        sql.prepared(query, values, SqlResult.validUniqueResultHandler(defaultResponseHandler));
    }

    @Override
    public void getCoursesByUserInEnt(String userId, Handler<Either<String, JsonArray>> eitherHandler) {
        String query = "SELECT moodle_id, folder_id  " +
                "FROM " + Moodle.moodleSchema + ".course " +
                "WHERE user_id = ?;";

        JsonArray values = new JsonArray();
        values.add(userId);
        sql.prepared(query, values, SqlResult.validResultHandler(eitherHandler));
    }


}
