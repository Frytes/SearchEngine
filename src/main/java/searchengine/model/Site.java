    package searchengine.model;

    import lombok.Getter;
    import lombok.Setter;

    import javax.persistence.*;
    import java.time.LocalDateTime;

    @Entity
    @Table(name = "site")
    @Getter
    @Setter
    public class Site {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        @Enumerated(EnumType.STRING)
        @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
        private SiteStatus status;

        @Column(name = "status_time", nullable = false)
        private LocalDateTime statusTime;

        @Column(name = "last_error", columnDefinition = "TEXT")
        private String lastError;

        @Column(nullable = false)
        private String url;

        @Column(nullable = false)
        private String name;

    }