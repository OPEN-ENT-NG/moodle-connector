package fr.openent.moodle.service.impl;

import fr.openent.moodle.service.ModuleNeoRequestService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import org.entcore.broker.api.dto.directory.GetGroupsRequestDTO;
import org.entcore.broker.api.dto.directory.GetGroupsResponseDTO;
import org.entcore.broker.api.dto.directory.GetUsersByIdsRequestDTO;
import org.entcore.broker.api.dto.directory.GetUsersByIdsResponseDTO;
import org.entcore.common.migration.AppMigrationConfiguration;
import org.entcore.common.migration.BrokerSwitchConfiguration;
import org.entcore.common.utils.CollectionUtils;

public class BrokerSwitchNeoRequestService implements ModuleNeoRequestService {
  private final ModuleNeoRequestService delegate;
  private final AppMigrationConfiguration appMigrationConfiguration;
  private final EventBus eventBus;

  public BrokerSwitchNeoRequestService(ModuleNeoRequestService delegate, AppMigrationConfiguration appMigrationConfiguration, final EventBus eventBus) {
    this.delegate = delegate;
    this.appMigrationConfiguration = appMigrationConfiguration;
    this.eventBus = eventBus;
  }

  @Override
  public void getUsers(JsonArray usersIds, Handler<Either<String, JsonArray>> handler) {
    if(appMigrationConfiguration.isReadEnabled("getUsers")) {
      final GetUsersByIdsRequestDTO request = new GetUsersByIdsRequestDTO(CollectionUtils.toList(usersIds, String.class));
      BrokerSwitchConfiguration.sendToBroker("getUsers", "referential", request, GetUsersByIdsResponseDTO.class, eventBus);
    } else {
      delegate.getUsers(usersIds, handler);
    }
  }

  @Override
  public void getGroups(JsonArray groupsIds, Handler<Either<String, JsonArray>> handler) {
    if(appMigrationConfiguration.isReadEnabled("getGroups")) {
      final GetGroupsRequestDTO request = new GetGroupsRequestDTO(CollectionUtils.toSet(groupsIds, String.class));
      BrokerSwitchConfiguration.sendToBroker("getGroups", "referential", request, GetGroupsResponseDTO.class, eventBus);
    } else {
      delegate.getGroups(groupsIds, handler);
    }
  }

  @Override
  public void getSharedBookMark(JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler) {
    delegate.getSharedBookMark(bookmarksIds, handler);
  }

  @Override
  public void getSharedBookMarkUsers(JsonArray bookmarksIds, Handler<Either<String, JsonArray>> handler) {
    delegate.getSharedBookMarkUsers(bookmarksIds, handler);
  }
}
