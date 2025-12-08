package ru.telegramm.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.telegramm.bot.model.UserData;

public interface UserRepository extends JpaRepository<UserData, Long> {

    UserData findByChatId(Long chatId);
}
