package ru.telegramm.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.telegramm.bot.data.BotState;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserData {
    @Id
    private Long chatId;
    private String username;
    private String firstName;
    private String lastName;
    @Enumerated(EnumType.STRING)
    private BotState botState;

}
