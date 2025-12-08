package ru.telegramm.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.telegramm.bot.data.StatusApplication;
import ru.telegramm.bot.model.Application;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Application findByChatIdAndSquareIsNull(Long chatId);

    Application findByChatIdAndImageIsNull(Long chatId);

    Application findByChatIdAndStartWorkIsNull(Long chatId);

    @Query("SELECT a FROM Application a WHERE a.chatId = :chatId ORDER BY a.created DESC LIMIT 1")
    Application findLatestByChatId(Long chatId);

    List<Application> findByChatId(Long chatId);

    Application findByChatIdAndTerritoryIsNull(Long chatId);

    Application findByChatIdAndContactIsNull(Long chatId);

    Application findByChatIdAndStatusIsNull(Long chatId);

    List<Application> findByStatus(StatusApplication status);
}
