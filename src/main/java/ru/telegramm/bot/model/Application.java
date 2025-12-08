package ru.telegramm.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import ru.telegramm.bot.data.StatusApplication;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long chatId;
    private String territory;
    private String square;
    private String image;
    private String startWork;
    @CreationTimestamp
    private LocalDateTime created;
    private String descriptionWork;
    private String descriptionObject;
    @Enumerated(EnumType.STRING)
    private StatusApplication status;
    private String contact;
}
