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
    public CallBackService(UserService userService, ApplicationService applicationService, @Lazy TelegramBotService telegramBotService) {
        this.userService = userService;
        this.applicationService = applicationService;
        this.telegramBotService = telegramBotService;
    }

    public void handleCallback(String callbackData, Update update) throws TelegramApiException {
        switch (callbackData) {
            case "answerQuestionsAndSubmitAnApplication":
                userService.updateUserState(update, BotState.START_OF_THE_SURVEY);
                break;
            case "myApplications":
                List<Application> applications = applicationService.getApplicationsByChatId(telegramBotService.getChatId());
                telegramBotService.sendFormattedMessage(telegramBotService.getChatId(), TextData.COUNT_APPLICATION + applications.size());
                if (applications.isEmpty()) {
                    telegramBotService.sendMessage(telegramBotService.getChatId(), TextData.NO_APPLICATIONS);
                } else {
                    for (Application a : applications) {
                        telegramBotService.sendApplicationToUser(telegramBotService.getChatId(), a);
                    }
                }
                break;
            case "applicationByAdmin":
                List<Application> applicationsByAdmin = applicationService.getApplicationsByStatus(StatusApplication.IN_PROCESS);
                telegramBotService.sendFormattedMessage(telegramBotService.getChatId(), TextData.COUNT_APPLICATION_IN_PROCESS + applicationsByAdmin.size());
                if (applicationsByAdmin.isEmpty()) {
                    telegramBotService.sendMessage(telegramBotService.getChatId(), TextData.NO_APPLICATION_ADMIN);
                } else {
                    for (Application a : applicationsByAdmin) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;
            case "updateStatusApplication":
                userService.updateUserState(update, BotState.UPDATE_ADMIN);
                telegramBotService.sendFormattedMessage(telegramBotService.getChatId(), TextData.INPUT_NUMBER_APPLICATION);
                break;

            case "doneApplication":
                List<Application> doneApplications = applicationService.getApplicationsByStatus(StatusApplication.PROCESSED);
                telegramBotService.sendFormattedMessage(telegramBotService.getChatId(), TextData.COUNT_APPLICATION_DONE + doneApplications.size());
                if (doneApplications.isEmpty()) {
                    telegramBotService.sendMessage(telegramBotService.getChatId(), TextData.NO_DONE_APPLICATION);
                } else {
                    for (Application a : doneApplications) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;
            case "allApplication":
                List<Application> allApplications = applicationService.getAllApplication();
                telegramBotService.sendFormattedMessage(telegramBotService.getChatId(), TextData.ALL_COUNT_APPLICATION + allApplications.size());
                if (allApplications.isEmpty()) {
                    telegramBotService.sendMessage(telegramBotService.getChatId(), TextData.NO_ALL_APPLICATION);
                } else {
                    for (Application a : allApplications) {
                        telegramBotService.sendApplicationInProgress(a);
                    }
                }
                break;
        }

    }
}
