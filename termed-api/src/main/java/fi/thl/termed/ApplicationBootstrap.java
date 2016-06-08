package fi.thl.termed;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.List;

import javax.annotation.Resource;

import fi.thl.termed.domain.Property;
import fi.thl.termed.domain.User;
import fi.thl.termed.repository.Repository;
import fi.thl.termed.util.ResourceUtils;

@Component
public class ApplicationBootstrap implements ApplicationListener<ContextRefreshedEvent> {

  @Resource
  private Repository<String, User> userRepository;

  @Resource
  private Repository<String, Property> propertyRepository;

  @Resource
  private Gson gson;

  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
    saveDefaultUser();
    saveDefaultProperties();
  }

  private void saveDefaultUser() {
    userRepository.save(new User("admin", new BCryptPasswordEncoder().encode("admin"), "ADMIN"));
  }

  private void saveDefaultProperties() {
    Type propertyListType = new TypeToken<List<Property>>() {
    }.getType();

    List<Property> properties = gson.fromJson(ResourceUtils.getResourceToString(
        "default/properties.json"), propertyListType);

    int index = 0;
    for (Property property : properties) {
      property.setIndex(index++);
    }

    propertyRepository.save(properties);
  }

}
