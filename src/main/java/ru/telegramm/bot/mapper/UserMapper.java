package ru.telegramm.bot.mapper;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.telegramm.bot.data.BotState;
import ru.telegramm.bot.model.UserData;

@Component
public class UserMapper {

    public UserData toUserData(Update update, BotState botState) {
        UserData userData = new UserData();
        userData.setUsername(update.getMessage().getFrom().getUserName());
        userData.setChatId(update.getMessage().getChatId());
        userData.setFirstName(update.getMessage().getFrom().getFirstName());
        userData.setLastName(update.getMessage().getFrom().getLastName());
        userData.setBotState(botState);
        return userData;
    }
}
