package ru.telegramm.bot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.telegramm.bot.data.BotState;

@Service
public class CheckService {

    private final UserService userService;

    @Autowired
    public CheckService(UserService userService) {
        this.userService = userService;
    }

    public boolean checkMessageIsText(Update update) {
        return update.hasMessage() && update.getMessage().hasText();
    }

    public boolean checkMessageIsNotStartAndBotStateNew(String messageText, Update update) {
        return !messageText.equals("/start") && userService.getBotStateByChatId(update).equals(BotState.NEW);
    }

    public boolean checkMessageIsNotStartAndBotStateFinish(String messageText, Update update) {
        return !messageText.equals("/start") && userService.getBotStateByChatId(update).equals(BotState.FINISH);
    }

    public boolean checkUserAlreadyExist(Update update) {
        return userService.getUserByChatId(update) != null;
    }

    public boolean checkMessageIsCallbackQuery(Update update) {
        return update.hasCallbackQuery();
    }

    public boolean checkMessageIsPhoto(Update update) {
        if (update.getMessage() != null) {
            return update.getMessage().hasPhoto();
        }
        return false;
    }

    public boolean checkMessageIsVideo(Update update) {
        if (update.getMessage() != null) {
            return update.getMessage().hasVideo();
        }
        return false;
    }

    public boolean checkMessageIsTextAndBotStateAdminUpdate(Update update) {
        return update.getMessage().hasText() && userService.getBotStateByChatId(update).equals(BotState.UPDATE_ADMIN);
    }
}
