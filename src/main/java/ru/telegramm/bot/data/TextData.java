package ru.telegramm.bot.data;

public class TextData {
    public static final String START_TEXT = "Вас приветствует бот консультант, я помогу вам структурировать задачу и " +
            "направлю ее специалисту";
    public static final String FAILED_TO_PROCESS_COMMAND = "Не удалось обработать команду ";
    public static final String WHAT_TYPES_OF_WORK_ARE_YOU_INTERESTED_IN = "1. Какие виды работ вас интересуют?";
    public static final String DESCRIBE_THE_OBJECT = "2. Опишите объект для работ";
    public static final String WHERE_IS_THR_OBJECT_LOCATED = "3. Где территориально находится объект?";
    public static final String IS_THE_AREA_KNOWN = "4. Известна ли вам ориентировочная площадь или нужен более точный замер?";
    public static final String WHEN_TO_START_WORK = "6. В какой период времени хотели бы приступить в работе?";
    public static final String TEXT_IMAGE = "5. Есть ли возможность прислать фото или видеоматериалы объекты? Если нет введите \"-\"";
    public static final String CONTACT_TEXT = "7. Предоставьте контакты для связи номер телефона/почта";
    public static final String FINISH = "Опрос завершен, заявка направлена специалисту! Вы можете ознакомиться с работами в тг канале https://t.me/cmp_concrete";
    public static final String START_QUESTION_TEXT = "Ответьте на вопросы ниже это поможет специалистам быстрее приступить к работе";
    public static final String NOTIFICATION_MEDIA = "⚠\uFE0F Важно! Если будете прикладывать медиа файлы, " +
            "то прикладывайте только 1 фото или 1 видео! В случае, если будет приложено несколько файлов специалист " +
            "увидит только первый приложенный файл⚠\uFE0F";
    public static final String START_TEXT_ADMIN = "Вас приветствует бот помощник! Вы можете просмотреть активные заявки и изменить статус заявки";
    public static final String NO_APPLICATIONS = "У вас отсутствуют раннее поданые заявки";
    public static final String NO_APPLICATION_ADMIN = "На текущий момент нет заявок в работе";
    public static final String NO_DONE_APPLICATION = "На текущий момент нет обработанных заявок";
    public static final String NO_ALL_APPLICATION = "На текущий момент нет заявок";
    public static final String INPUT_NUMBER_APPLICATION = "Введите номер заявки: ";
    public static final String NO_APPLICATION_BY_ID = "Не найдена заявка с номером ";
    public static final String ALL_COUNT_APPLICATION = "Общее количество заявок: ";
    public static final String COUNT_APPLICATION_IN_PROCESS = "Количество заявок в работе: ";
    public static final String COUNT_APPLICATION_DONE = "Количество обработанных заявок: ";
    public static final String COUNT_APPLICATION = "Количество заявок: ";
}
