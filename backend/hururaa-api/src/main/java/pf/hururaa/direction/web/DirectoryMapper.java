package pf.hururaa.direction.web;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;

@Mapper(componentModel = ComponentModel.SPRING)
public interface DirectoryMapper {

  DirectionResponse toDirectionResponse(Direction domain);

  GroupResponse toGroupResponse(Group domain);

  UserResponse toUserResponse(User domain);
}
