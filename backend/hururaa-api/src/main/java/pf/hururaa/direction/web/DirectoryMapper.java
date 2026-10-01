package pf.hururaa.direction.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import pf.hururaa.direction.domain.DelegationChange;
import pf.hururaa.direction.domain.Direction;
import pf.hururaa.direction.domain.Group;
import pf.hururaa.direction.domain.User;

@Mapper(componentModel = ComponentModel.SPRING)
public interface DirectoryMapper {

  DirectionResponse toDirectionResponse(Direction domain);

  GroupResponse toGroupResponse(Group domain);

  UserResponse toUserResponse(User domain);

  @Mapping(target = "authorId", source = "author.id")
  @Mapping(target = "authorUsername", source = "author.username")
  @Mapping(target = "authorFirstName", source = "author.firstName")
  @Mapping(target = "authorLastName", source = "author.lastName")
  @Mapping(target = "delegateId", source = "delegate.id")
  @Mapping(target = "delegateUsername", source = "delegate.username")
  @Mapping(target = "delegateFirstName", source = "delegate.firstName")
  @Mapping(target = "delegateLastName", source = "delegate.lastName")
  DelegationChangeResponse toDelegationChangeResponse(DelegationChange domain);
}
