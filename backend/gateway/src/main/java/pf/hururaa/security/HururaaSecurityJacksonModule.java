package pf.hururaa.security;

import java.util.List;
import org.springframework.security.jackson.SecurityJacksonModule;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.core.Version;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Jackson (3) module to (de)serialize a security context holding a {@link HururaaOidcUser} — for
 * instance when the session is stored as JSON. Use {@link #getModules(ClassLoader)} rather than
 * {@link SecurityJacksonModules#getModules(ClassLoader)}: it registers this module on top of Spring
 * Security's ones and whitelists {@link HururaaOidcUser} and the "exotic" claim value types found in
 * Keycloak tokens (nested claims parsed by Nimbus' shaded Gson, immutable collections).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class HururaaSecurityJacksonModule extends SecurityJacksonModule {

  /** Nested JSON objects in Nimbus-parsed JWT claims (e.g. the {@code organization} claim). */
  static final String NIMBUS_GSON_MAP = "com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap";

  /** {@code List.of()}, {@code Set.copyOf()}, {@code Map.copyOf()} & co. */
  static final String JDK_IMMUTABLE_COLLECTIONS = "java.util.ImmutableCollections$";

  public HururaaSecurityJacksonModule() {
    super(HururaaSecurityJacksonModule.class.getName(), new Version(1, 0, 0, null, null, null));
  }

  /**
   * @param loader the ClassLoader to use
   * @return Spring Security's modules (with their polymorphic type validator extended for Hururaa
   *         types) plus this module
   */
  public static List<JacksonModule> getModules(ClassLoader loader) {
    final var hururaaModule = new HururaaSecurityJacksonModule();
    final var typeValidatorBuilder = BasicPolymorphicTypeValidator.builder();
    hururaaModule.configurePolymorphicTypeValidator(typeValidatorBuilder);
    final var modules = SecurityJacksonModules.getModules(loader, typeValidatorBuilder);
    modules.add(hururaaModule);
    return modules;
  }

  @Override
  public void configurePolymorphicTypeValidator(BasicPolymorphicTypeValidator.Builder builder) {
    builder
        .allowIfSubType(HururaaOidcUser.class)
        .allowIfSubType(NIMBUS_GSON_MAP)
        .allowIfSubType(JDK_IMMUTABLE_COLLECTIONS);
  }

  @Override
  public void setupModule(SetupContext context) {
    context.setMixIn(HururaaOidcUser.class, HururaaOidcUserMixin.class);
  }
}
