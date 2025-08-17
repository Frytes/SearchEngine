    package searchengine.model;

    import javax.persistence.*;
    import lombok.Getter;
    import lombok.Setter;

    @Getter
    @Setter
    @Entity
    @Table(name = "search_index",
            indexes = @Index(name = "idx_index_page_lemma",
                    columnList = "page_id, lemma_id"))
    public class SearchIndex {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        @Column(name = "`rank`", nullable = false)
        private float rank;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "lemma_id", foreignKey = @ForeignKey(name = "fk_index_lemma",
                foreignKeyDefinition = "FOREIGN KEY (lemma_id) REFERENCES lemma(id) ON DELETE CASCADE"))
        private Lemma lemma;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "page_id", foreignKey = @ForeignKey(name = "fk_index_page",
                foreignKeyDefinition = "FOREIGN KEY (page_id) REFERENCES page(id) ON DELETE CASCADE"))
        private Page page;
    }
