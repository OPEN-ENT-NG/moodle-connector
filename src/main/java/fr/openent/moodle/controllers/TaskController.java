package fr.openent.moodle.controllers;

import fr.openent.moodle.cron.NotifyMoodle;
import fr.openent.moodle.cron.SynchDuplicationMoodle;
import fr.wseduc.rs.Post;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class TaskController extends BaseController {
	protected static final Logger log = LoggerFactory.getLogger(TaskController.class);

	final SynchDuplicationMoodle synchDuplicationMoodle;

	final NotifyMoodle defaultNotifyMoodle;


	public TaskController(SynchDuplicationMoodle synchDuplicationMoodle, NotifyMoodle defaultNotifyMoodle) {
		this.synchDuplicationMoodle = synchDuplicationMoodle;
		this.defaultNotifyMoodle = defaultNotifyMoodle;
	}

	@Post("api/internal/sync-duplication")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void syncDuplication(HttpServerRequest request) {
		log.info("Triggered sync duplication task");
		synchDuplicationMoodle.handle(0L);
		render(request, null, 202);
	}

	@Post("api/internal/notify-moodle")
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void notifyMoodle(HttpServerRequest request) {
		log.info("Triggered notify moodle task");
		defaultNotifyMoodle.handle(0L);
		render(request, null, 202);
	}


}
