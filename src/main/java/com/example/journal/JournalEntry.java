package com.example.journal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journals")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_before", length = 16)
    private Emotion emotionBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_after", length = 16)
    private Emotion emotionAfter;

    @Column(length = 4000)
    private String lessons;

    @Column(length = 4000)
    private String mistakes;

    @Column(length = 4000)
    private String notes;

    @Column(name = "entry_screenshot_url", length = 512)
    private String entryScreenshotUrl;

    @Column(name = "exit_screenshot_url", length = 512)
    private String exitScreenshotUrl;
}

