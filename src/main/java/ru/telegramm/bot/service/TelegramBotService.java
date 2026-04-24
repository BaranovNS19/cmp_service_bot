package ru.telegramm.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.telegramm.bot.data.BotState;
import ru.telegramm.bot.data.StatusApplication;
import ru.telegramm.bot.data.TextData;
import ru.telegramm.bot.model.Application;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {
    private final String botUsername;
    private final String botToken;
    private String messageText;
    private long chatId;
    private final CheckService checkService;
    private final UserService userService;
    private final KeyBoardService keyBoardService;
    private final CallBackService callBackService;
    private final ApplicationService applicationService;
    @Value("${telegram.bot.admin}")
    private Long adminChatId;

    public TelegramBotService(@Value("${telegram.bot.username}") String botUsername,
                              @Value("${telegram.bot.token}") String botToken, CheckService checkService,
                              UserService userService, KeyBoardService keyBoardService, CallBackService callBackService,
                              ApplicationService applicationService) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.checkService = checkService;
        this.userService = userService;
        this.keyBoardService = keyBoardService;
        this.callBackService = callBackService;
        this.applicationService = applicationService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (checkService.checkMessageIsText(update)) {
            messageText = getMessageText(update);
            chatId = getChatId(update);
            if (messageText.equals("/start")) {
                if (!checkService.checkUserAlreadyExist(update)) {
                    userService.saveUser(update, BotState.NEW);
                }
                if (chatId == adminChatId) {
                    sendMessage(chatId, TextData.START_TEXT_ADMIN);
                } else {
                    sendMessage(chatId, TextData.START_TEXT);
                }
                sendInlineKeyboard(chatId);
            }
            if (checkService.checkMessageIsNotStartAndBotStateNew(messageText, update) ||
                    checkService.checkMessageIsNotStartAndBotStateFinish(messageText, update)) {
                sendMessage(chatId, TextData.FAILED_TO_PROCESS_COMMAND + "[" + messageText + "]");
            }
            if (!checkService.checkMessageIsNotStartAndBotStateNew(messageText, update)) {
                sendQuestion(update);
            }
        }
        if (checkService.checkMessageIsCallbackQuery(update)) {
            try {
                callBackService.handleCallback(getCallbackQueryData(update), update);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            sendQuestion(update);

        }
        if (checkService.checkMessageIsPhoto(update)) {
            handlePhoto(update);
        }
        if (checkService.checkMessageIsVideo(update)) {
            handleVideo(update);
        }
        if (checkService.checkMessageIsTextAndBotStateAdminUpdate(update)) {
            updateApplication(update);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    public void sendMessage(Long chatId, String text) {
        try {
            execute(new SendMessage(chatId.toString(), text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getProgressBar(int current, int total) {
        int percent = (current * 100) / total;
        int bars = (percent / 10);
        String progressBar = "▰".repeat(bars) + "▱".repeat(10 - bars);
        return String.format("`[%s] %d/%d (%d%%)`", progressBar, current, total, percent);
    }

    public void sendFormattedMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        String htmlText = "<b>" + escapeHtml(text) + "</b>";
        message.setText(htmlText);
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, text);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String getMessageText(Update update) {
        String text = "";
        if (update.hasMessage() && update.getMessage() != null) {
            text = update.getMessage().getText();
        } else if (update.hasCallbackQuery()) {
            text = update.getCallbackQuery().getMessage().getText();
        }
        return text;
    }

    private List<PhotoSize> getMessagePhoto(Update update) {
        return update.getMessage().getPhoto();
    }

    private Long getChatId(Update update) {
        return update.getMessage().getChatId();
    }

    private void sendMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendInlineKeyboard(Long chatId) {
        SendMessage inlineKeyboardMessage = new SendMessage();
        inlineKeyboardMessage.setChatId(String.valueOf(chatId));
        inlineKeyboardMessage.setText("Выберите раздел:");
        if (chatId.equals(adminChatId)) {
            inlineKeyboardMessage.setReplyMarkup(keyBoardService.createMenuForAdmin());
            sendMessage(inlineKeyboardMessage);
        } else {
            inlineKeyboardMessage.setReplyMarkup(keyBoardService.createMenu());
            sendMessage(inlineKeyboardMessage);
        }
    }

    private String getCallbackQueryData(Update update) {
        return update.getCallbackQuery().getData();
    }

    private void sendQuestion(Update update) {
        switch (userService.getBotStateByChatId(update)) {
            case START_OF_THE_SURVEY:
                sendFormattedMessage(chatId, TextData.WHAT_TYPES_OF_WORK_ARE_YOU_INTERESTED_IN);
                userService.updateUserState(update, BotState.DESCRIPTION_WORK);
                break;
            case DESCRIPTION_WORK:
                applicationService.saveDescriptionWork(messageText, chatId);
                sendFormattedMessage(chatId, TextData.DESCRIBE_THE_OBJECT);
                userService.updateUserState(update, BotState.DESCRIPTION_OBJECT);
                break;
            case DESCRIPTION_OBJECT:
                applicationService.saveDescriptionObject(messageText, chatId);
                sendFormattedMessage(chatId, TextData.WHERE_IS_THR_OBJECT_LOCATED);
                userService.updateUserState(update, BotState.TERRITORY);
                break;
            case TERRITORY:
                applicationService.saveTerritory(messageText, chatId);
                if (applicationService.getApplicationByChatId(chatId).getTerritory() != null) {
                    userService.updateUserState(update, BotState.SQUARE);
                }
                sendFormattedMessage(chatId, TextData.IS_THE_AREA_KNOWN);
                break;
            case SQUARE:
                applicationService.saveSquare(messageText, chatId);
                if (applicationService.getApplicationByChatId(chatId).getSquare() != null) {
                    userService.updateUserState(update, BotState.IMAGE);
                }
                sendFormattedMessage(chatId, TextData.TEXT_IMAGE);
                sendMessage(chatId, TextData.NOTIFICATION_MEDIA);
                break;
            case IMAGE:
                if (checkService.checkMessageIsText(update)) {
                    applicationService.saveImage(messageText, chatId);
                    userService.updateUserState(update, BotState.TIME_WORK);
                    sendFormattedMessage(chatId, TextData.WHEN_TO_START_WORK);
                }
                if (checkService.checkMessageIsPhoto(update)) {
                    handlePhoto(update);
                }
                if (checkService.checkMessageIsVideo(update)) {
                    handleVideo(update);
                }
                break;
            case TIME_WORK:
                applicationService.saveStartWork(messageText, chatId);
                if (applicationService.getApplicationByChatId(chatId).getStartWork() != null) {
                    userService.updateUserState(update, BotState.CONTACT);
                }
                sendFormattedMessage(chatId, TextData.CONTACT_TEXT);
                break;
            case CONTACT:
                applicationService.saveContact(messageText, chatId);
                userService.updateUserState(update, BotState.FINISH);
                applicationService.setStatusApplication(StatusApplication.IN_PROCESS, chatId);
                sendMessage(chatId, TextData.FINISH);
                sendApplicationToAdmin(chatId, update);
                break;

        }
    }

    private PhotoSize getPhoto(Message msg) {
        List<PhotoSize> photos = msg.getPhoto();
        return photos.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .orElse(null);
    }

    private void handlePhoto(Update update) {
        try {
            Message message = update.getMessage();
            BotState currentState = userService.getBotStateByChatId(update);
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize largestPhoto = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            if (largestPhoto != null) {
                GetFile getFile = new GetFile();
                getFile.setFileId(largestPhoto.getFileId());
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
                String fileUrl = file.getFileUrl(getBotToken());
                String fileName = "photo_" + System.currentTimeMillis() + "_" + largestPhoto.getFileId() + ".jpg";
                String filePath = downloadFile(fileUrl, fileName);

                if (filePath != null) {
                    applicationService.saveImage("photo:" + filePath, chatId);
                    SendMessage response = new SendMessage();
                    response.setChatId(chatId);
                    response.setText("✅ Фото успешно загружено!");
                    execute(response);
                    userService.updateUserState(update, BotState.TIME_WORK);
                    sendFormattedMessage(chatId, TextData.WHEN_TO_START_WORK);
                } else {
                    sendMessage(chatId, "Ошибка при сохранении фото");
                }
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при обработке фото");
        }
    }


    private void handleVideo(Update update) {
        try {
            Message message = update.getMessage();
            BotState currentState = userService.getBotStateByChatId(update);

            if (currentState != BotState.IMAGE) {
                sendMessage(chatId, "Пожалуйста, сначала ответьте на предыдущие вопросы");
                return;
            }

            Video video = message.getVideo();
            GetFile getFile = new GetFile();
            getFile.setFileId(video.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

            String fileUrl = file.getFileUrl(getBotToken());
            String fileName = "video_" + System.currentTimeMillis() + "_" + video.getFileId() + ".mp4";
            String filePath = downloadFile(fileUrl, fileName);

            if (filePath != null) {
                applicationService.saveImage("video:" + filePath, chatId);
                SendMessage response = new SendMessage();
                response.setChatId(message.getChatId().toString());
                response.setText(String.format(
                        "✅ Видео успешно загружено!%n" +
                                "Длительность: %d сек.%n" +
                                "Разрешение: %dx%d",
                        video.getDuration(),
                        video.getWidth(),
                        video.getHeight()
                ));
                execute(response);
                userService.updateUserState(update, BotState.TIME_WORK);
                sendFormattedMessage(chatId, TextData.WHEN_TO_START_WORK);
            } else {
                sendMessage(chatId, "Ошибка при сохранении видео");
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при обработке видео");
        }
    }

    private String downloadFile(String fileUrl, String fileName) {
        try {
            String directoryPath = "uploads/";
            Path directory = Paths.get(directoryPath);

            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            Path filePath = directory.resolve(fileName);
            try (InputStream in = new URL(fileUrl).openStream()) {
                Files.copy(in, filePath);
                System.out.println("Файл сохранен: " + filePath.toAbsolutePath());
                return filePath.toAbsolutePath().toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendApplicationToAdmin(Long userChatId, Update update) {
        try {
            ru.telegramm.bot.model.Application application = applicationService.getApplicationByChatId(userChatId);

            if (application == null) {
                System.err.println("Заявка не найдена для chatId: " + userChatId);
                return;
            }

            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 <b>Новая заявка: </b> №" + application.getId() + "\n\n");
            messageText.append("👤 Чат ID: ").append(userChatId).append("\n");
            messageText.append("  Имя пользователя: ").append(update.getMessage().getFrom().getFirstName()).append("\n");
            messageText.append("  Фамилия пользователя: ").append(update.getMessage().getFrom().getLastName()).append("\n");
            messageText.append("  Тэг пользователя: ").append(update.getMessage().getFrom().getUserName()).append("\n");
            messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
            messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
            messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
            messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
            messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
            messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

            String mediaInfo = application.getImage();
            if (mediaInfo != null) {
                if (mediaInfo.startsWith("text:")) {
                    messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                    SendMessage adminMessage = new SendMessage();
                    adminMessage.setChatId(adminChatId.toString());
                    adminMessage.setText(messageText.toString());
                    adminMessage.setParseMode("HTML");
                    execute(adminMessage);
                    return;

                } else if (mediaInfo.startsWith("video:")) {
                    messageText.append("🎥 Видео: см. выше\n");
                    sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "video");
                    return;

                } else if (mediaInfo.startsWith("photo:")) {
                    messageText.append("📷 Фото: см. выше\n");
                    sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "photo");
                    return;
                } else {
                    messageText.append("📎 Медиа: не приложено\n");
                }
            } else {
                messageText.append("📷 Фото/видео: не приложено\n");
            }

            SendMessage adminMessage = new SendMessage();
            adminMessage.setChatId(adminChatId.toString());
            adminMessage.setText(messageText.toString());
            adminMessage.setParseMode("HTML");
            execute(adminMessage);

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMediaWithCaptionToAdmin(String mediaPath, String caption, String mediaType) {
        try {
            Path path = Paths.get(mediaPath);
            if (!Files.exists(path)) {
                System.err.println("Файл не найден: " + mediaPath);
                SendMessage adminMessage = new SendMessage();
                adminMessage.setChatId(adminChatId.toString());
                adminMessage.setText(caption + "\n\n⚠️ Файл не найден на сервере");
                adminMessage.setParseMode("HTML");
                execute(adminMessage);
                return;
            }

            File mediaFile = path.toFile();

            if (mediaType.equals("photo")) {
                execute(SendPhoto.builder()
                        .chatId(adminChatId.toString())
                        .photo(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            } else if (mediaType.equals("video")) {
                execute(SendVideo.builder()
                        .chatId(adminChatId.toString())
                        .video(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
            try {
                SendMessage adminMessage = new SendMessage();
                adminMessage.setChatId(adminChatId.toString());
                adminMessage.setText(caption + "\n\n⚠️ Не удалось отправить медиафайл");
                adminMessage.setParseMode("HTML");
                execute(adminMessage);
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    public long getChatId() {
        return chatId;
    }

    public void sendApplicationToUser(Long userChatId, Application application) {
        try {
            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 <b>Номер заявки: </b>" + application.getId() + "\n\n");
            messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
            messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
            messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
            messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
            messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
            messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

            String mediaInfo = application.getImage();
            if (mediaInfo != null) {
                if (mediaInfo.startsWith("text:")) {
                    messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                    SendMessage adminMessage = new SendMessage();
                    adminMessage.setChatId(chatId);
                    adminMessage.setText(messageText.toString());
                    adminMessage.setParseMode("HTML");
                    execute(adminMessage);
                    return;

                } else if (mediaInfo.startsWith("video:")) {
                    messageText.append("🎥 Видео: см. выше\n");
                    sendMediaWithCaptionToUser(mediaInfo.substring(6), messageText.toString(), "video");
                    return;

                } else if (mediaInfo.startsWith("photo:")) {
                    messageText.append("📷 Фото: см. выше\n");
                    sendMediaWithCaptionToUser(mediaInfo.substring(6), messageText.toString(), "photo");
                    return;
                } else {
                    messageText.append("📎 Медиа: приложено\n");
                }
            } else {
                messageText.append("📷 Фото/видео: не приложено\n");
            }

            SendMessage adminMessage = new SendMessage();
            adminMessage.setChatId(chatId);
            adminMessage.setText(messageText.toString());
            adminMessage.setParseMode("HTML");
            execute(adminMessage);

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMediaWithCaptionToUser(String mediaPath, String caption, String mediaType) {
        try {
            Path path = Paths.get(mediaPath);
            if (!Files.exists(path)) {
                System.err.println("Файл не найден: " + mediaPath);
                SendMessage adminMessage = new SendMessage();
                adminMessage.setChatId(chatId);
                adminMessage.setText(caption + "\n\n⚠️ Файл не найден на сервере");
                adminMessage.setParseMode("HTML");
                execute(adminMessage);
                return;
            }

            File mediaFile = path.toFile();

            if (mediaType.equals("photo")) {
                execute(SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            } else if (mediaType.equals("video")) {
                execute(SendVideo.builder()
                        .chatId(chatId)
                        .video(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
            try {
                SendMessage adminMessage = new SendMessage();
                adminMessage.setChatId(chatId);
                adminMessage.setText(caption + "\n\n⚠️ Не удалось отправить медиафайл");
                adminMessage.setParseMode("HTML");
                execute(adminMessage);
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void sendApplicationInProgress(Application application) throws TelegramApiException {
        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 <b>Заявка: </b> №" + application.getId() + "\n\n");
        if (application.getStatus().equals(StatusApplication.PROCESSED)) {
            messageText.append("  <b>Статус: </b> ОБРАБОТАНА✅ \n\n");
        } else {
            messageText.append("  <b>Статус: </b> В РАБОТЕ\uD83D\uDD04 \n\n");
        }
        messageText.append("👤 Чат ID: ").append(application.getChatId()).append("\n");
//        messageText.append("  Имя пользователя: ").append(update.getMessage().getFrom().getFirstName()).append("\n");
//        messageText.append("  Фамилия пользователя: ").append(update.getMessage().getFrom().getLastName()).append("\n");
//        messageText.append("  Тэг пользователя: ").append(update.getMessage().getFrom().getUserName()).append("\n");
        messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
        messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
        messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
        messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
        messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
        messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

        String mediaInfo = application.getImage();
        if (mediaInfo != null) {
            if (mediaInfo.startsWith("text:")) {
                messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                SendMessage adminMessage = new SendMessage();
                adminMessage.setChatId(adminChatId.toString());
                adminMessage.setText(messageText.toString());
                adminMessage.setParseMode("HTML");
                execute(adminMessage);
                return;

            } else if (mediaInfo.startsWith("video:")) {
                messageText.append("🎥 Видео: см. выше\n");
                sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "video");
                return;

            } else if (mediaInfo.startsWith("photo:")) {
                messageText.append("📷 Фото: см. выше\n");
                sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "photo");
                return;
            } else {
                messageText.append("📎 Медиа: не приложено\n");
            }
        } else {
            messageText.append("📷 Фото/видео: не приложено\n");
        }
        SendMessage adminMessage = new SendMessage();
        adminMessage.setChatId(chatId);
        adminMessage.setText(messageText.toString());
        adminMessage.setParseMode("HTML");
        execute(adminMessage);
    }

    public void updateApplication(Update update) {
        Optional<Application> application = applicationService.getApplicationById(Long.parseLong(messageText));
        if (application.isEmpty()) {
            sendMessage(chatId, TextData.NO_APPLICATION_BY_ID + messageText);
        } else {
            applicationService.updateStatusApplication(Long.valueOf(messageText), StatusApplication.PROCESSED);
            sendMessage(chatId, "Заявка под номером " + messageText + " переведена в статус \"ОБРАБОТАНА\"");
            userService.updateUserState(update, BotState.NEW);
            sendInlineKeyboard(chatId);
        }

    }

    public void sendAnswerMessage(String text, Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        answer.setText(text);
        answer.setShowAlert(true);

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Ошибка: {}", e.getMessage());
        }
    }
}
