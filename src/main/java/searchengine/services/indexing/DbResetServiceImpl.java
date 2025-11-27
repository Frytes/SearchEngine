package searchengine.services.indexing;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbResetServiceImpl implements DbResetService {

    private final DataSource dataSource;

    @Override
    public void resetDatabase() {
        log.info("Начата полная очистка базы данных...");
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.xml", new ClassLoaderResourceAccessor(), database);

            liquibase.dropAll();
            liquibase.update("");

            log.info("Очистка и пересоздание таблиц успешно завершены.");
        } catch (Exception e) {
            log.error("Ошибка при сбросе базы данных", e);
            throw new RuntimeException("Не удалось выполнить сброс базы данных", e);
        }
    }
}