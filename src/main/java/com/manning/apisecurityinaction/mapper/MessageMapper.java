package com.manning.apisecurityinaction.mapper;

import com.manning.apisecurityinaction.model.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.dalesbred.result.RowMapper;

public class MessageMapper implements RowMapper<Message> {

  @Override
  public Message mapRow(ResultSet resultSet) throws SQLException {
    Message message = new Message();
    message.setAuthor(resultSet.getString("author"));
    message.setMessageText(resultSet.getString("msg_text"));
    message.setMessageTime(resultSet.getTimestamp("msg_time").toInstant());
    return message;
  }
}
