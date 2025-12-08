package ru.telegramm.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.telegramm.bot.data.StatusApplication;
import ru.telegramm.bot.model.Application;
import ru.telegramm.bot.repository.ApplicationRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    @Autowired
    public ApplicationService(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public void saveDescriptionWork(String text, Long chatId) {
        Application application = new Application();
        application.setChatId(chatId);
        application.setDescriptionWork(text);
        applicationRepository.save(application);
    }

    public void saveDescriptionObject(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndTerritoryIsNull(chatId);
        application.setDescriptionObject(text);
        applicationRepository.save(application);
    }

    public void saveTerritory(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndTerritoryIsNull(chatId);
        application.setTerritory(text);
        applicationRepository.save(application);
    }

    public void saveSquare(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndSquareIsNull(chatId);
        application.setSquare(text);
        applicationRepository.save(application);
    }

    public void saveImage(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndImageIsNull(chatId);
        application.setImage(text);
        applicationRepository.save(application);
    }

    public void saveStartWork(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndStartWorkIsNull(chatId);
        application.setStartWork(text);
        applicationRepository.save(application);
    }

    public void saveContact(String text, Long chatId) {
        Application application = applicationRepository.findByChatIdAndContactIsNull(chatId);
        application.setContact(text);
        applicationRepository.save(application);
    }

    public Application getApplicationByChatId(Long chatId) {
        return applicationRepository.findLatestByChatId(chatId);
    }

    public List<Application> getApplicationsByChatId(Long chatId) {
        return applicationRepository.findByChatId(chatId);
    }

    public void setStatusApplication(StatusApplication statusApplication, Long chatId) {
        Application application = applicationRepository.findByChatIdAndStatusIsNull(chatId);
        application.setStatus(statusApplication);
        applicationRepository.save(application);
    }

    public List<Application> getApplicationsByStatus(StatusApplication statusApplication) {
        return applicationRepository.findByStatus(statusApplication);
    }

    public Optional<Application> getApplicationById(Long id) {
        return applicationRepository.findById(id);
    }

    public void updateStatusApplication(Long id, StatusApplication statusApplication) {
        Application application = getApplicationById(id)
                .orElseThrow(() -> new NullPointerException("Заявка с id " + id + " не найдена"));
        application.setStatus(statusApplication);
        applicationRepository.save(application);
    }

    public List<Application> getAllApplication(){
        return applicationRepository.findAll();
    }
}
