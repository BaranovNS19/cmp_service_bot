package ru.telegramm.bot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.telegramm.bot.data.BotState;
import ru.telegramm.bot.data.StatusApplication;
import ru.telegramm.bot.data.TextData;
import ru.telegramm.bot.model.Application;

import java.util.List;

@Service
public class CallBackService {
    private final UserService userService;
    private final ApplicationService applicationService;
    private final TelegramBotService telegramBotService;

    @Autowired
    public CallBackService(UserService userService,
                           ApplicationService applicationService,
                           @Lazy TelegramBotService telegramBotService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.telegramBotService = telegramBotService;
    }

    public void handleCallback(String callbackData, Update update) throws TelegramApiException {
        // Получаем chatId из callbackQuery
        Long chatId = getChatIdFromUpdate(update);
        if (chatId == null) return;

        switch (callbackData) {
            case "answerQuestionsAndSubmitAnApplication":
                userService.updateUserState(update, BotState.START_OF_THE_SURVEY);
                break;

            case "myApplications":
                List<Application> applications = applicationService.getApplicationsByChatId(chatId);
                telegramBotService.sendFormattedMessage(chatId, TextData.COUNT_APPLICATION + applications.size());
                if (applications.isEmpty()) {
                    telegramBotService.sendMessage(chatId, TextData.NO_APPLICATIONS);
                } else {
                    for (Application a : applications) {
                        telegramBotService.sendApplicationToUser(chatId, a);
                    }
                }
                break;

            case "applicationByAdmin":
                List<Application> applicationsByAdmin = applicationService.getApplicationsByStatus(StatusApplication.IN_PROCESS);
                if (applicationsByAdmin.isEmpty()) {
                    telegramBotService.sendAnswerMessage(TextData.NO_APPLICATION_ADMIN, update);
                } else {
                    for (Application a : applicationsByAdmin) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;

            case "updateStatusApplication":
                List<Application> allApplications = applicationService.getAllApplication();
                if (allApplications.isEmpty()) {
                    telegramBotService.sendAnswerMessage(TextData.NO_ALL_APPLICATION, update);
                } else {
                    userService.updateUserState(update, BotState.UPDATE_ADMIN);
                    telegramBotService.sendFormattedMessage(chatId, TextData.INPUT_NUMBER_APPLICATION);
                }
                break;

            case "doneApplication":
                List<Application> doneApplications = applicationService.getApplicationsByStatus(StatusApplication.PROCESSED);
                if (doneApplications.isEmpty()) {
                    telegramBotService.sendAnswerMessage(TextData.NO_DONE_APPLICATION, update);
                } else {
                    for (Application a : doneApplications) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;

            case "allApplication":
                List<Application> allApplications12 = applicationService.getAllApplication();
                if (allApplications12.isEmpty()) {
                    telegramBotService.sendAnswerMessage(TextData.NO_ALL_APPLICATION, update);
                } else {
                    for (Application a : allApplications12) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;
        }
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        } else if (update.hasMessage() && update.getMessage() != null) {
            return update.getMessage().getChatId();
        }
        return null;
    }
}