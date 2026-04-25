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
    private final CheckService checkService;
    private final UserService userService;
    private final KeyBoardService keyBoardService;
    private final CallBackService callBackService;
    private final ApplicationService applicationService;
    @Value("${telegram.bot.admin}")
    private Long adminChatId;

    public TelegramBotService(@Value("${telegram.bot.username}") String botUsername,
                              @Value("${telegram.bot.token}") String botToken,
                              CheckService checkService,
                              UserService userService,
                              KeyBoardService keyBoardService,
                              CallBackService callBackService,
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
        // Извлекаем данные из update локально, не сохраняя в поля класса
        String messageText = getMessageText(update);
        Long chatId = getChatIdFromUpdate(update);

        if (checkService.checkMessageIsText(update)) {
            if (messageText.equals("/start")) {
                if (!checkService.checkUserAlreadyExist(update)) {
                    userService.saveUser(update, BotState.NEW);
                }
                if (chatId != null && chatId.equals(adminChatId)) {
                    sendMessage(chatId, TextData.START_TEXT_ADMIN);
                } else if (chatId != null) {
                    sendMessage(chatId, TextData.START_TEXT);
                }
                if (chatId != null) {
                    sendInlineKeyboard(chatId);
                }
            }
            if (checkService.checkMessageIsNotStartAndBotStateNew(messageText, update) ||
                    checkService.checkMessageIsNotStartAndBotStateFinish(messageText, update)) {
                if (chatId != null) {
                    sendMessage(chatId, TextData.FAILED_TO_PROCESS_COMMAND + "[" + messageText + "]");
                }
            }
            if (!checkService.checkMessageIsNotStartAndBotStateNew(messageText, update)) {
                sendQuestion(update, chatId, messageText);
            }
        }
        if (checkService.checkMessageIsCallbackQuery(update)) {
            try {
                callBackService.handleCallback(getCallbackQueryData(update), update);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            sendQuestion(update, chatId, messageText);
        }
        if (checkService.checkMessageIsPhoto(update)) {
            handlePhoto(update, chatId);
        }
        if (checkService.checkMessageIsVideo(update)) {
            handleVideo(update, chatId);
        }
        if (checkService.checkMessageIsTextAndBotStateAdminUpdate(update)) {
            updateApplication(update, chatId, messageText);
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
        if (chatId == null) return;
        try {
            execute(new SendMessage(chatId.toString(), text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendFormattedMessage(Long chatId, String text) {
        if (chatId == null) return;
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
        if (update.hasMessage() && update.getMessage() != null && update.getMessage().hasText()) {
            return update.getMessage().getText();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getText();
        }
        return "";
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage() && update.getMessage() != null) {
            return update.getMessage().getChatId();
        } else if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }

    private void sendInlineKeyboard(Long chatId) {
        if (chatId == null) return;
        SendMessage inlineKeyboardMessage = new SendMessage();
        inlineKeyboardMessage.setChatId(String.valueOf(chatId));
        inlineKeyboardMessage.setText("Выберите раздел:");
        if (chatId.equals(adminChatId)) {
            inlineKeyboardMessage.setReplyMarkup(keyBoardService.createMenuForAdmin());
        } else {
            inlineKeyboardMessage.setReplyMarkup(keyBoardService.createMenu());
        }
        sendMessage(inlineKeyboardMessage);
    }

    private void sendMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getCallbackQueryData(Update update) {
        return update.getCallbackQuery().getData();
    }

    private void sendQuestion(Update update, Long chatId, String messageText) {
        if (chatId == null) return;

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
            default:
                break;
        }
    }

    private void handlePhoto(Update update, Long chatId) {
        if (chatId == null) return;
        try {
            Message message = update.getMessage();
            BotState currentState = userService.getBotStateByChatId(update);

            if (currentState != BotState.IMAGE) {
                sendMessage(chatId, "Пожалуйста, сначала ответьте на предыдущие вопросы");
                return;
            }

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
                    response.setChatId(chatId.toString());
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

    private void handleVideo(Update update, Long chatId) {
        if (chatId == null) return;
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
                log.info("Файл сохранен: {}", filePath.toAbsolutePath());
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
                log.error("Заявка не найдена для chatId: {}", userChatId);
                return;
            }

            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 <b>Новая заявка: </b> №").append(application.getId()).append("\n\n");
            messageText.append("👤 Чат ID: ").append(userChatId).append("\n");

            if (update.hasMessage() && update.getMessage().getFrom() != null) {
                messageText.append("  Имя пользователя: ").append(update.getMessage().getFrom().getFirstName()).append("\n");
                messageText.append("  Фамилия пользователя: ").append(update.getMessage().getFrom().getLastName()).append("\n");
                messageText.append("  Тэг пользователя: ").append(update.getMessage().getFrom().getUserName()).append("\n");
            }

            messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
            messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
            messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
            messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
            messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
            messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

            String mediaInfo = application.getImage();
            if (mediaInfo != null && !mediaInfo.isEmpty()) {
                if (mediaInfo.startsWith("text:")) {
                    messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                    sendTextToAdmin(messageText.toString());
                } else if (mediaInfo.startsWith("video:")) {
                    messageText.append("🎥 Видео: см. выше\n");
                    sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "video");
                } else if (mediaInfo.startsWith("photo:")) {
                    messageText.append("📷 Фото: см. выше\n");
                    sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "photo");
                } else {
                    messageText.append("📎 Медиа: приложено\n");
                    sendTextToAdmin(messageText.toString());
                }
            } else {
                messageText.append("📷 Фото/видео: не приложено\n");
                sendTextToAdmin(messageText.toString());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextToAdmin(String text) throws TelegramApiException {
        SendMessage adminMessage = new SendMessage();
        adminMessage.setChatId(adminChatId.toString());
        adminMessage.setText(text);
        adminMessage.setParseMode("HTML");
        execute(adminMessage);
    }

    private void sendMediaWithCaptionToAdmin(String mediaPath, String caption, String mediaType) {
        try {
            Path path = Paths.get(mediaPath);
            if (!Files.exists(path)) {
                log.error("Файл не найден: {}", mediaPath);
                sendTextToAdmin(caption + "\n\n⚠️ Файл не найден на сервере");
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
                sendTextToAdmin(caption + "\n\n⚠️ Не удалось отправить медиафайл");
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void sendApplicationToUser(Long userChatId, Application application) {
        if (userChatId == null) return;

        try {
            StringBuilder messageText = new StringBuilder();
            messageText.append("📋 <b>Номер заявки: </b>").append(application.getId()).append("\n\n");
            messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
            messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
            messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
            messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
            messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
            messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

            String mediaInfo = application.getImage();
            if (mediaInfo != null && !mediaInfo.isEmpty()) {
                if (mediaInfo.startsWith("text:")) {
                    messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                    sendTextToUser(userChatId, messageText.toString());
                } else if (mediaInfo.startsWith("video:")) {
                    messageText.append("🎥 Видео: см. выше\n");
                    sendMediaWithCaptionToUser(userChatId, mediaInfo.substring(6), messageText.toString(), "video");
                } else if (mediaInfo.startsWith("photo:")) {
                    messageText.append("📷 Фото: см. выше\n");
                    sendMediaWithCaptionToUser(userChatId, mediaInfo.substring(6), messageText.toString(), "photo");
                } else {
                    messageText.append("📎 Медиа: приложено\n");
                    sendTextToUser(userChatId, messageText.toString());
                }
            } else {
                messageText.append("📷 Фото/видео: не приложено\n");
                sendTextToUser(userChatId, messageText.toString());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextToUser(Long userChatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(userChatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        execute(message);
    }

    private void sendMediaWithCaptionToUser(Long userChatId, String mediaPath, String caption, String mediaType) {
        if (userChatId == null) return;

        try {
            Path path = Paths.get(mediaPath);
            if (!Files.exists(path)) {
                log.error("Файл не найден: {}", mediaPath);
                sendTextToUser(userChatId, caption + "\n\n⚠️ Файл не найден на сервере");
                return;
            }

            File mediaFile = path.toFile();

            if (mediaType.equals("photo")) {
                execute(SendPhoto.builder()
                        .chatId(userChatId.toString())
                        .photo(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            } else if (mediaType.equals("video")) {
                execute(SendVideo.builder()
                        .chatId(userChatId.toString())
                        .video(new InputFile(mediaFile))
                        .caption(caption)
                        .parseMode("HTML")
                        .build());
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
            try {
                sendTextToUser(userChatId, caption + "\n\n⚠️ Не удалось отправить медиафайл");
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void sendApplicationInProgress(Application application) throws TelegramApiException {
        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 <b>Заявка: </b> №").append(application.getId()).append("\n\n");
        if (application.getStatus().equals(StatusApplication.PROCESSED)) {
            messageText.append("  <b>Статус: </b> ОБРАБОТАНА✅ \n\n");
        } else {
            messageText.append("  <b>Статус: </b> В РАБОТЕ🔄 \n\n");
        }
        messageText.append("👤 Чат ID: ").append(application.getChatId()).append("\n");
        messageText.append("\uD83D\uDD27 Тип работ: ").append(application.getDescriptionWork()).append("\n");
        messageText.append("\uD83D\uDCDD Описание объекта: ").append(application.getDescriptionObject()).append("\n");
        messageText.append("📍 Местоположение объекта: ").append(application.getTerritory()).append("\n");
        messageText.append("📏 Площадь: ").append(application.getSquare()).append("\n");
        messageText.append("📅 Начало работ: ").append(application.getStartWork()).append("\n");
        messageText.append("\uD83D\uDCDE Контактная информация: ").append(application.getContact()).append("\n");

        String mediaInfo = application.getImage();
        if (mediaInfo != null && !mediaInfo.isEmpty()) {
            if (mediaInfo.startsWith("text:")) {
                messageText.append("📝 Описание: ").append(mediaInfo.substring(5)).append("\n");
                sendTextToAdmin(messageText.toString());
            } else if (mediaInfo.startsWith("video:")) {
                messageText.append("🎥 Видео: см. выше\n");
                sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "video");
            } else if (mediaInfo.startsWith("photo:")) {
                messageText.append("📷 Фото: см. выше\n");
                sendMediaWithCaptionToAdmin(mediaInfo.substring(6), messageText.toString(), "photo");
            } else {
                messageText.append("📎 Медиа: приложено\n");
                sendTextToAdmin(messageText.toString());
            }
        } else {
            messageText.append("📷 Фото/видео: не приложено\n");
            sendTextToAdmin(messageText.toString());
        }
    }

    public void updateApplication(Update update, Long chatId, String messageText) {
        if (chatId == null) return;

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