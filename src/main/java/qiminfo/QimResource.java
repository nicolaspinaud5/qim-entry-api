package qiminfo;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestStreamElementType;

import io.micrometer.core.annotation.Timed;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import qiminfo.Entry.Service;

@Path("/api/entries/v1/")
@Tag(name = "entries", description = "Operations on entries resource.")
public class QimResource {

  @Inject
  private Service service;

  Logger logger = Logger.getLogger(QimResource.class);

  @GET
  @Operation(summary = "Get all entries")
  @APIResponse(responseCode = "200", description = "List of entries", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Entry.class)))
  @Timed(value = "entry-api", description = "A measure of how long it takes to get entries from database.")
  public Uni<List<Entry>> getAll() {
    return service.listAll();
  }

  @GET
  @Path("/stream")
  @Operation(summary = "Get all entries via a stream")
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Timed(value = "entry-api-stream", description = "A measure of how long it takes to get entries from database.")
  public Multi<Entry> getAllStream() {
    return service.listAllStream();
  }

  @GET
  @Path("/api/{api}")
  @APIResponse(responseCode = "200")
  @Operation(summary = "Get entries by api")
  public Multi<Entry> getByApi(@PathParam("api") String api) {
    return service.findByAPI(api);
  }

  @GET
  @Path("/https")
  @APIResponse(responseCode = "200")
  @Operation(summary = "Get entries by https value")
  public Uni<List<Entry>> getByHttpsValue(@QueryParam("isHttps") boolean isHttps) {
    return service.findByHttps(isHttps);
  }

  @POST
  @Transactional
  @APIResponse(responseCode = "201", description = "Entry created", content = @Content(schema = @Schema(implementation = Entry.class)))
  @APIResponse(responseCode = "406", description = "Invalid data")
  @APIResponse(responseCode = "409", description = "Entry already exists")
  @Operation(summary = "Create new entry")
  public Uni<Response> create(@Valid List<Entry> entry) {
    return Uni.createFrom().item(Response.ok(entry).status(Status.CREATED).build());
  }

  @POST
  @Path("/bulk")
  @Transactional
  @APIResponse(responseCode = "201", description = "Entries created or updated", content = @Content(schema = @Schema(implementation = Entry.class)))
  @APIResponse(responseCode = "406", description = "Invalid data")
  @APIResponse(responseCode = "500", description = "Server error")
  @Operation(summary = "Create new entries")
  public Uni<Response> createBulk(@Valid List<Entry> entries) {
    MDC.put("entries", entries);
    logger.info("Entries: " + entries);
    return service.saveEntries(entries)
        .onItem()
        .transformToUni(data -> Uni.createFrom()
            .item(
                Response.ok(entries)
                    .status(Status.OK)
                    .build())
            .onFailure()
            .recoverWithUni(failure -> Uni.createFrom()
                .item(
                    Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode(), failure.getMessage())
                        .build())));
  }
}
