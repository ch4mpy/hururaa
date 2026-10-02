package pf.hururaa.history;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;
import pf.hururaa.history.domain.PermissionChange;
import pf.hururaa.history.domain.PermissionChangeCategory;
import pf.hururaa.history.domain.PermissionChangeType;

@Mapper(componentModel = ComponentModel.SPRING)
public interface PermissionHistoryMapper {

  @Mapping(target = "authorId", source = "author.id")
  @Mapping(target = "authorUsername", source = "author.username")
  @Mapping(target = "authorFirstName", source = "author.firstName")
  @Mapping(target = "authorLastName", source = "author.lastName")
  @Mapping(target = "subjectId", source = "subject.id")
  @Mapping(target = "subjectUsername", source = "subject.username")
  @Mapping(target = "subjectFirstName", source = "subject.firstName")
  @Mapping(target = "subjectLastName", source = "subject.lastName")
  @Mapping(target = "category", source = "type")
  PermissionChangeResponse toPermissionChangeResponse(PermissionChange domain);

  default PermissionChangeCategory toCategory(PermissionChangeType type) {
    return type.category();
  }
}
