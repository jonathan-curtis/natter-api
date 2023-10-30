package com.manning.apisecurityinaction;

import static spark.Spark.afterAfter;
import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;
import static spark.Spark.post;
import static spark.Spark.secure;

import com.google.common.util.concurrent.RateLimiter;
import com.manning.apisecurityinaction.controller.AuditController;
import com.manning.apisecurityinaction.controller.ModeratorController;
import com.manning.apisecurityinaction.controller.SpaceController;
import com.manning.apisecurityinaction.controller.TokenController;
import com.manning.apisecurityinaction.controller.UserController;
import com.manning.apisecurityinaction.token.CookieTokenStore;
import com.manning.apisecurityinaction.token.TokenStore;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.dalesbred.Database;
import org.dalesbred.result.EmptyResultException;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

public class Main {

  public static void main(String... args) throws URISyntaxException, IOException {
    Spark.staticFiles.location("/public");
    secure("localhost.p12", "changeit", null, null);
    var datasource = JdbcConnectionPool.create("jdbc:h2:mem:natter", "natter", "password");
    var database = Database.forDataSource(datasource);
    createTables(database);
    datasource = JdbcConnectionPool.create("jdbc:h2:mem:natter", "natter_api_user", "password");
    database = Database.forDataSource(datasource);

    var spaceController = new SpaceController(database);
    var moderatorController = new ModeratorController(database);
    var userController = new UserController(database);
    var auditController = new AuditController(database);

    // attempt to authenticate the user
    before(userController::authenticate);

    // log the request and responses
    before(auditController::auditRequestStart);
    afterAfter(auditController::auditRequestEnd);

    TokenStore tokenStore = new CookieTokenStore();
    TokenController tokenController = new TokenController(tokenStore);

    post("/users", userController::registerUser);

    before("/sessions", userController::requireAuthentication);
    post("/sessions", tokenController::login);

    get("/logs", auditController::readAuditLog);

    var rateLimiter = RateLimiter.create(2.0d);
    before((request, response) -> {
      if (!rateLimiter.tryAcquire()) {
        response.header("Retry-After", "2");
        halt(429);
      }
    });

    before((request, response) -> {
      if (request.requestMethod().equals("POST") && !"application/json".equals(request.contentType())) {
        halt(415, new JSONObject().put("error", "Only application/json supported").toString());
      }
    });

    before("/spaces", userController::requireAuthentication);
    post("/spaces", spaceController::createSpace);

    before("/spaces/:spaceId/messages", userController.requirePermission("POST", "w"));
    post("/spaces/:spaceId/messages", spaceController::createMessage);

    before("/spaces/:spaceId/messages", userController.requirePermission("GET", "r"));
    get("/spaces/:spaceId/messages", spaceController::getMessages);

    before("/spaces/:spaceId/messages/*", userController.requirePermission("GET", "r"));
    get("/spaces/:spaceId/messages/:msgId", spaceController::getMessage);

    before("/spaces/:spaceId/messages/*", userController.requirePermission("DELETE", "d"));
    delete("/spaces/:spaceId/messages/:msgId", moderatorController::deletePost);

    before("/spaces/:spaceId/members", userController.requirePermission("POST", "rwd"));
    post("/spaces/:spaceId/members", spaceController::addMember);

    afterAfter((request, response) -> {
      response.type("application/json;charset=utf-8");
      response.header("X-Content-Type-Options", "nosniff");
      response.header("X-Frame-Options", "DENY");
      response.header("X-XSS-Protection", "0");
      response.header("Cache-Control", "no-store");
      response.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox");
      response.header("Server", "");
    });

    internalServerError(new JSONObject().put("error", "internal server error").toString());
    notFound(new JSONObject().put("error", "not found").toString());

    exception(IllegalArgumentException.class, Main::badRequest);
    exception(JSONException.class, Main::badRequest);
    exception(EmptyResultException.class, (e, request, response) -> response.status(404));
  }

  private static void badRequest(Exception ex, Request request, Response response) {
    response.status(400);
    response.body(new JSONObject().put("error", ex.getMessage()).toString());
  }

  private static void createTables(Database database) throws URISyntaxException, IOException {
    var path = Paths.get(Main.class.getResource("/schema.sql").toURI());
    database.update(Files.readString(path));
  }
}