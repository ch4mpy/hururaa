package pf.hururaa.direction.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Paging-only parameter object for endpoints returning a
 * {@link org.springframework.data.web.PagedModel PagedModel}.
 *
 * <p>
 * Deliberately carries no sort criteria: the data behind those endpoints either comes from the
 * Keycloak Admin REST API, which exposes no ordering parameter (only {@code first} / {@code max}),
 * or has a fixed order (the delegation history, newest first). Accepting a {@link Pageable} would
 * advertise a {@code sort} query parameter in the OpenAPI spec that could only ever be silently
 * discarded.
 * </p>
 *
 * @param page zero-based page index, defaults to {@code 0} when absent
 * @param size page size, defaults to {@value #DEFAULT_SIZE} when absent, capped at
 *        {@value #MAX_SIZE}
 */
public record PageParams(@Min(0) Integer page, @Min(1) @Max(MAX_SIZE) Integer size) {

  public static final int DEFAULT_SIZE = 20;

  public static final int MAX_SIZE = 200;

  public PageParams {
    page = page == null ? 0 : page;
    size = size == null ? DEFAULT_SIZE : size;
  }

  /**
   * @return an unsorted {@link Pageable} for this page index and size
   */
  public Pageable toPageable() {
    return PageRequest.of(page, size);
  }
}
