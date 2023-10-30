package com.manning.apisecurityinaction.model;

import java.time.Instant;
import org.json.JSONObject;

public class Message {
  private String author;
  private String messageText;

  private Instant messageTime;

  public Message() {

  }

  public Message(String author, Instant messageTime, String messageText) {
    this.author = author;
    this.messageTime = messageTime;
    this.messageText = messageText;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getMessageText() {
    return messageText;
  }

  public void setMessageText(String message) {
    this.messageText = message;
  }

  public Instant getMessageTime() {
    return messageTime;
  }

  public void setMessageTime(Instant messageTime) {
    this.messageTime = messageTime;
  }

  @Override
  public String toString() {
    JSONObject msg = new JSONObject();
    msg.put("author", author);
    msg.put("time", messageTime.toString());
    msg.put("message", messageText);
    return msg.toString();
  }
}