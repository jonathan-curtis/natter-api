package com.manning.apisecurityinaction.controller;

import static java.util.Objects.nonNull;

import com.manning.apisecurityinaction.model.Message;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.dalesbred.Database;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

public class SpaceController {

  private final Database database;

  public SpaceController(Database database) {
    this.database = database;
  }

  public JSONObject createSpace(Request request, Response response) {
    var json = new JSONObject(request.body());
    var spaceName = json.getString("name");
    if (spaceName.length() > 255) {
      throw new IllegalArgumentException("space name too long");
    }
    var owner = json.getString("owner");
    var subject = request.attribute("subject");
    if (!owner.equals(subject)) {
      throw new IllegalArgumentException("owner must match authenticated user");
    }
    return database.withTransaction(tx -> {
      var spaceId = database.findUniqueLong("SELECT NEXT VALUE FOR space_id_seq;");

      database.updateUnique("INSERT INTO spaces(space_id, name, owner) VALUES(?, ?, ?);", spaceId, spaceName, owner);
      database.updateUnique("INSERT INTO permissions(space_id, user_id, perms) VALUES(?, ?, ?)", spaceId, owner, "rwd");
      response.status(201);
      response.header("Location", "/spaces/" + spaceId);

      return new JSONObject().put("name", spaceName).put("uri", "/spaces/" + spaceId);
    });
  }

  public JSONObject createMessage(Request request, Response response) {
    var spaceId = Long.parseLong(request.params(":spaceId"));

    var json = new JSONObject(request.body());
    var author = json.getString("author");
    var subject = request.attribute("subject");
    if (!author.equals(subject)) {
      throw new IllegalArgumentException("author must match authenticated user");
    }

    var message = json.getString("message");
    if (message.length() > 1024) {
      throw new IllegalArgumentException("message is too long");
    }

    return database.withTransaction(tx -> {
      var msgId = database.findUniqueLong("SELECT NEXT VALUE FOR msg_id_seq;");
      database.updateUnique("INSERT INTO messages(space_id, msg_id, author, msg_time, msg_text) " +
          "VALUES(?, ?, ?, current_timestamp, ?);", spaceId, msgId, author, message);

      response.status(201);
      var uri = "/spaces/" + spaceId + "/messages/" + msgId;
      response.header("Location", uri);
      return new JSONObject().put("uri", uri);
    });

  }

  public JSONArray getMessages(Request request, Response response) {
    var spaceId = Long.parseLong(request.params(":spaceId"));

    var since = Instant.now().minus(1, ChronoUnit.DAYS);
    var query = request.queryParams("since");
    if (nonNull(query)) {
      since = Instant.parse(query);
    }

    var messages = database.findAll(Message.class, "SELECT author, msg_time, msg_text FROM messages " +
        "WHERE space_id = ? AND msg_time >= ?;", spaceId, since);
    response.status(200);
    return new JSONArray(messages);
  }

  public Message getMessage(Request request, Response response) {
    var spaceId = Long.parseLong(request.params(":spaceId"));
    var msgId = Long.parseLong(request.params(":msgId"));
    var message = database.findUnique(Message.class, "SELECT author, msg_time, msg_text FROM messages " +
        "WHERE space_id = ? AND msg_id = ?;", spaceId, msgId);
    response.status(200);
    return message;
  }

  public JSONObject addMember(Request request, Response response) {
    var json = new JSONObject(request.body());
    var spaceId = Long.parseLong(request.params(":spaceId"));
    var userToAdd = json.getString("username");
    var perms = json.getString("permissions");

    if (!perms.matches("r?w?d?")) {
      throw new IllegalArgumentException("invalid permissions");
    }

    database.updateUnique("INSERT INTO permissions(space_id, user_id, perms) VALUES(?, ?, ?);", spaceId, userToAdd,
        perms);

    response.status(200);
    return new JSONObject().put("username", userToAdd).put("permissions", perms);
  }
}
