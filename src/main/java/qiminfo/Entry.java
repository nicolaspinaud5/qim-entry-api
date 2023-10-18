package qiminfo;

import java.util.List;
import java.util.Objects;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micrometer.core.annotation.Timed;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@MongoEntity
@RegisterForReflection
public class Entry extends ReactivePanacheMongoEntity {
  @JsonProperty("API")
  public String api;
  @JsonProperty("Description")
  public String description;
  @JsonProperty("Auth")
  public String auth;
  @JsonProperty("HTTPS")
  public boolean https;
  @JsonProperty("Cors")
  public String cors;
  @JsonProperty("Link")
  public String link;
  @JsonProperty("Category")
  public String category;

  public Entry() {
  }

  @Override
  public String toString() {
    return "Entry [api=" + api + ", description=" + description + ", auth=" + auth + ", https=" + https + ", cors="
        + cors + ", link=" + link + ", category=" + category + "]";
  }

  public static Multi<Entry> findByAPI(String api) {
    return Entry.stream("api", api);
  }

  public static Uni<List<Entry>> findByIsHttps(boolean isHttps) {
    return Entry.list("https", isHttps);
  }

  public static boolean exists(Entry entry) {
    return Entry.count("api = ?1 and description = ?2 and category = ?3", entry.api, entry.description, entry.category)
        .await()
        .indefinitely() > 0;
  }

  @ApplicationScoped
  public static class Service {

    Logger logger = Logger.getLogger(Service.class);

    public Uni<List<Entry>> listAll() {
      Uni<List<Entry>> entries = Entry.listAll();
      return entries;
    }

    public Multi<Entry> listAllStream() {
      Multi<Entry> entries = Entry.streamAll();
      return entries;
    }

    public Multi<Entry> findByAPI(String api) {
      if (Objects.isNull(api) || api.isEmpty()) {
        return Multi.createFrom().empty();
      }
      return Entry.findByAPI(api);
    }

    public Uni<List<Entry>> findByHttps(boolean isHttps) {
      return Entry.findByIsHttps(isHttps);
    }

    @Timed(value = "entry-api-save", description = "A measure of how long it takes to save entries to database.")
    public Uni<Void> saveEntries(List<Entry> entries) {
      logger.info("Saving Entries: " + entries);
      for (Entry entry : entries) {
        if (Entry.exists(entry)) {
          MDC.put("current-entry", entry);
          logger.info("Entry exist: " + entry);
          try {
            Entry.update(entry).await().indefinitely();
          } catch (Exception e) {
            MDC.clear();
            return Uni.createFrom().failure(e);
          }
        } else {
          MDC.put("current-entry", entry);
          logger.info("Entry does not exist: " + entry);
          try {
            Entry.persist(entry).await().indefinitely();
          } catch (Exception e) {
            MDC.clear();
            return Uni.createFrom().failure(e);
          }
        }
      }
      MDC.clear();
      return Uni.createFrom().voidItem();
    }
  }
}
