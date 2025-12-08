package ru.telegramm.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.telegramm.bot.data.BotState;
import ru.telegramm.bot.mapper.UserMapper;
import ru.telegramm.bot.model.UserData;
import ru.telegramm.bot.repository.UserRepository;

@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserMapper userMapper, UserRepository userRepository) {
        this.userMapper = userMapper;
        this.userRepository = userRepository;
    }

    public void saveUser(Update update, BotState botState) {
        log.info("сохранение пользователя");
        userRepository.save(userMapper.toUserData(update, botState));
    }

    public BotState getBotStateByChatId(Update update) {
        Long chatId = null;
        if (update.hasMessage() && update.getMessage() != null) {
            chatId = update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        }
        UserData userData = userRepository.findByChatId(chatId);
        log.info("получен пользователь {}", userData);
        return userData.getBotState();
    }

    public UserData getUserByChatId(Update update) {
        Long chatId = null;
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        }
        return userRepository.findByChatId(chatId);
    }

    public UserData updateUserState(Update update, BotState botState) {
        UserData userData = getUserByChatId(update);
        userData.setBotState(botState);
        userRepository.save(userData);
        return getUserByChatId(update);
    }
}
