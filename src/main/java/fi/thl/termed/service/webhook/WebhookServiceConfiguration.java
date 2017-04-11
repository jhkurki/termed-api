package fi.thl.termed.service.webhook;

import static fi.thl.termed.util.dao.AuthorizedDao.ReportLevel.THROW;

import com.google.common.eventbus.EventBus;
import com.google.gson.Gson;
import fi.thl.termed.domain.AppRole;
import fi.thl.termed.domain.Webhook;
import fi.thl.termed.service.webhook.internal.EventPostingWebhookService;
import fi.thl.termed.service.webhook.internal.JdbcWebhookDao;
import fi.thl.termed.util.dao.AuthorizedDao;
import fi.thl.termed.util.dao.CachedSystemDao;
import fi.thl.termed.util.dao.SystemDao;
import fi.thl.termed.util.permission.PermissionEvaluator;
import fi.thl.termed.util.service.DaoForwardingRepository;
import fi.thl.termed.util.service.Service;
import fi.thl.termed.util.service.TransactionalService;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WebhookServiceConfiguration {

  @Autowired
  private EventBus eventBus;

  @Autowired
  private Gson gson;

  @Bean
  public Service<UUID, Webhook> webhookService(
      DataSource dataSource, PlatformTransactionManager transactionManager) {

    SystemDao<UUID, Webhook> dao =
        new CachedSystemDao<>(new JdbcWebhookDao(dataSource));

    PermissionEvaluator<UUID> permissionEvaluator =
        (u, o, p) -> u.getAppRole() == AppRole.ADMIN || u.getAppRole() == AppRole.SUPERUSER;

    Service<UUID, Webhook> service =
        new DaoForwardingRepository<>(
            new AuthorizedDao<>(dao, permissionEvaluator, THROW));

    service = new EventPostingWebhookService(service, gson);
    eventBus.register(service);

    return new TransactionalService<>(service, transactionManager);
  }

}