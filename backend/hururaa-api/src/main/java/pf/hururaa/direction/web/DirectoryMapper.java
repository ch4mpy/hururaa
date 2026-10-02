package pf.hururaa.direction.web;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import pf.hururaa.application.domain.Application;
import pf.hururaa.direction.domain.DelegatedGroup;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;

@Mapper(componentModel = ComponentModel.SPRING)
public interface DirectoryMapper {

  DirectionResponse toDirectionResponse(Direction domain);

  /**
   * @param application the application the group belongs to, if any
   */
  @Mapping(target = "id", source = "domain.id")
  @Mapping(target = "name", source = "domain.name")
  @Mapping(target = "applicationId", source = "application.id")
  @Mapping(target = "applicationName", source = "application.name")
  GroupResponse toGroupResponse(Group domain, @Nullable Application application);

  @Mapping(target = "applicationId", source = "application.id")
  @Mapping(target = "applicationName", source = "application.name")
  GroupResponse toGroupResponse(DelegatedGroup domain);

  UserResponse toUserResponse(User domain);
}
